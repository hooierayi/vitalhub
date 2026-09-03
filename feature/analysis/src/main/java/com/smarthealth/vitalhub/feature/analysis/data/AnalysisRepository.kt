package com.smarthealth.vitalhub.feature.analysis.data

import com.smarthealth.vitalhub.feature.analysis.AnalysisConfig
import com.smarthealth.vitalhub.provider.record.CollectionRecord
import com.smarthealth.vitalhub.provider.record.RecordProvider
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal data class AnalysisProgress(val recordId: String, val state: AnalysisTaskState)

internal interface AnalysisRunner {
    suspend fun execute(
        sessionId: String,
        action: AnalysisFailureAction? = null,
        onProgress: (AnalysisProgress) -> Unit,
    )
}

internal class DefaultAnalysisRepository(
    private val recordProvider: RecordProvider,
    private val remoteDataSource: AnalysisRemoteDataSource,
    private val appVersion: String,
    private val protocolVersion: String = "1.0",
    private val pollIntervalMillis: Long = AnalysisConfig.POLL_FALLBACK_INTERVAL_MILLIS,
    private val maxPollAttempts: Int = 200,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) : AnalysisRunner {
    override suspend fun execute(
        sessionId: String,
        action: AnalysisFailureAction?,
        onProgress: (AnalysisProgress) -> Unit,
    ) {
        try {
            val record = requireRecord(sessionId)
            val (task, resumedExistingTask) = when (action) {
                AnalysisFailureAction.RESTART_ANALYSIS,
                AnalysisFailureAction.RETRY_UPLOAD -> {
                    clearAnalysisId(record)
                    upload(record, onProgress) to false
                }
                AnalysisFailureAction.RESUME_QUERY -> record.analysisId
                    ?.takeIf(String::isNotBlank)
                    ?.let { AnalysisTask(it, initialPollDelayMillis = 0L) to true }
                    ?: (upload(record, onProgress) to false)
                AnalysisFailureAction.RECOLLECT_DATA,
                AnalysisFailureAction.NONE -> return
                null -> record.analysisId
                    ?.takeIf(String::isNotBlank)
                    ?.let { AnalysisTask(it, initialPollDelayMillis = 0L) to true }
                    ?: (upload(record, onProgress) to false)
            }
            if (resumedExistingTask) {
                onProgress(
                    AnalysisProgress(
                        record.id,
                        AnalysisTaskState.Waiting(AnalysisWaitingStatus.PROCESSING),
                    ),
                )
            }
            poll(
                recordId = record.id,
                analysisId = task.analysisId,
                initialDelayMillis = task.initialPollDelayMillis,
                onProgress = onProgress,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AnalysisFailure) {
            onProgress(
                AnalysisProgress(
                    failure.recordId,
                    AnalysisTaskState.Failed(failure.problem.message, failure.problem.action),
                ),
            )
        } catch (failure: Throwable) {
            onProgress(
                AnalysisProgress(
                    recordProvider.getRecordBySessionId(sessionId)?.id.orEmpty(),
                    AnalysisTaskState.Failed(
                        failure.message ?: "上传或分析失败",
                        AnalysisFailureAction.NONE,
                    ),
                ),
            )
        }
    }

    private suspend fun upload(
        record: CollectionRecord,
        onProgress: (AnalysisProgress) -> Unit,
    ): AnalysisTask {
        val path = record.localFilePath
            ?: throw AnalysisFailure(record.id, localFileProblem("采集记录缺少本地文件"))
        val file = File(path)
        if (!file.isFile) {
            throw AnalysisFailure(record.id, localFileProblem("本地 DICOM 文件不存在"))
        }

        onProgress(AnalysisProgress(record.id, AnalysisTaskState.Uploading(0)))
        var lastProgress = -1
        val result = remoteDataSource.upload(
            file = file,
            appVersion = appVersion,
            protocolVersion = protocolVersion,
        ) { progress ->
            if (progress != lastProgress) {
                lastProgress = progress
                onProgress(
                    AnalysisProgress(
                        record.id,
                        AnalysisTaskState.Uploading(progress.coerceIn(0, 100)),
                    ),
                )
            }
        }
        return when (val outcome = AnalyzeResponseMapper.map(result)) {
            is CreateAnalysisOutcome.Accepted -> {
                if (!recordProvider.saveAnalysisId(record.id, outcome.analysisId)) {
                    throw AnalysisFailure(
                        record.id,
                        AnalysisProblem(
                            phase = AnalysisRequestPhase.UPLOAD,
                            httpCode = null,
                            businessCode = null,
                            message = "分析任务编号保存失败",
                            action = AnalysisFailureAction.NONE,
                        ),
                    )
                }
                onProgress(
                    AnalysisProgress(record.id, AnalysisTaskState.Waiting(outcome.status)),
                )
                AnalysisTask(
                    analysisId = outcome.analysisId,
                    initialPollDelayMillis = outcome.pollIntervalMillis ?: pollIntervalMillis,
                )
            }
            is CreateAnalysisOutcome.Failed -> throw AnalysisFailure(record.id, outcome.problem)
        }
    }

    private suspend fun poll(
        recordId: String,
        analysisId: String,
        initialDelayMillis: Long,
        onProgress: (AnalysisProgress) -> Unit,
    ) {
        var temporaryFailureCount = 0
        var nextDelayMillis = initialDelayMillis
        repeat(maxPollAttempts) {
            if (nextDelayMillis > 0L) sleep(nextDelayMillis)
            when (
                val outcome = AnalysisResultResponseMapper.map(
                    analysisId,
                    remoteDataSource.getResult(analysisId),
                )
            ) {
                is QueryAnalysisOutcome.Completed -> {
                    onProgress(
                        AnalysisProgress(recordId, AnalysisTaskState.Completed(outcome.markdown)),
                    )
                    return
                }
                is QueryAnalysisOutcome.Pending -> {
                    temporaryFailureCount = 0
                    nextDelayMillis = outcome.pollIntervalMillis ?: pollIntervalMillis
                    onProgress(
                        AnalysisProgress(recordId, AnalysisTaskState.Waiting(outcome.status)),
                    )
                }
                is QueryAnalysisOutcome.TemporaryFailure -> {
                    temporaryFailureCount += 1
                    if (temporaryFailureCount >= MAX_TEMPORARY_FAILURES) {
                        throw AnalysisFailure(recordId, outcome.problem)
                    }
                    onProgress(
                        AnalysisProgress(
                            recordId,
                            AnalysisTaskState.Waiting(
                                status = AnalysisWaitingStatus.RETRYING,
                                message = "${outcome.problem.message}，将在 " +
                                    "${fallbackIntervalSeconds()} 秒后继续查询",
                            ),
                        ),
                    )
                    nextDelayMillis = pollIntervalMillis
                }
                is QueryAnalysisOutcome.Failed -> throw AnalysisFailure(recordId, outcome.problem)
            }
        }
        throw AnalysisFailure(
            recordId,
            AnalysisProblem(
                phase = AnalysisRequestPhase.QUERY,
                httpCode = null,
                businessCode = null,
                message = "分析等待超时，请稍后继续查询",
                action = AnalysisFailureAction.RESUME_QUERY,
            ),
        )
    }

    private suspend fun clearAnalysisId(record: CollectionRecord) {
        if (record.analysisId == null) return
        if (!recordProvider.saveAnalysisId(record.id, null)) {
            throw AnalysisFailure(
                record.id,
                AnalysisProblem(
                    phase = AnalysisRequestPhase.UPLOAD,
                    httpCode = null,
                    businessCode = null,
                    message = "无法重置旧分析任务",
                    action = AnalysisFailureAction.NONE,
                ),
            )
        }
    }

    private suspend fun requireRecord(sessionId: String): CollectionRecord =
        recordProvider.getRecordBySessionId(sessionId)
            ?: throw AnalysisFailure("", localFileProblem("未找到本次采集记录"))

    private fun localFileProblem(message: String) = AnalysisProblem(
        phase = AnalysisRequestPhase.UPLOAD,
        httpCode = null,
        businessCode = null,
        message = message,
        action = AnalysisFailureAction.RECOLLECT_DATA,
    )

    private fun fallbackIntervalSeconds(): Long =
        ((pollIntervalMillis + 999L) / 1_000L).coerceAtLeast(1L)

    private class AnalysisFailure(
        val recordId: String,
        val problem: AnalysisProblem,
    ) : Exception(problem.message)

    private data class AnalysisTask(
        val analysisId: String,
        val initialPollDelayMillis: Long,
    )

    private companion object {
        const val MAX_TEMPORARY_FAILURES = 3
    }
}
