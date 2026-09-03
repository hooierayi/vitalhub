package com.smarthealth.vitalhub.feature.analysis.data

import com.smarthealth.vitalhub.provider.record.AnalysisStatus
import com.smarthealth.vitalhub.provider.record.CollectionAnalysis
import com.smarthealth.vitalhub.provider.record.CollectionRecord
import com.smarthealth.vitalhub.provider.record.RecordProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal data class AnalysisProgress(
    val recordId: String,
    val status: AnalysisStatus,
    val uploadProgress: Int = if (status == AnalysisStatus.UPLOADING) 0 else 100,
    val resultMarkdown: String? = null,
    val errorMessage: String? = null,
)

internal interface AnalysisRunner {
    suspend fun execute(
        sessionId: String,
        retryFailed: Boolean = false,
        onProgress: (AnalysisProgress) -> Unit,
    )
}

internal class DefaultAnalysisRepository(
    private val recordProvider: RecordProvider,
    private val remoteDataSource: AnalysisRemoteDataSource,
    private val appVersion: String,
    private val protocolVersion: String = "1.0",
    private val pollIntervalMillis: Long = 3_000L,
    private val maxPollAttempts: Int = 200,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val now: () -> Long = System::currentTimeMillis,
) : AnalysisRunner {
    override suspend fun execute(
        sessionId: String,
        retryFailed: Boolean,
        onProgress: (AnalysisProgress) -> Unit,
    ) {
        try {
            val record = requireRecord(sessionId)
            val saved = record.analysis
            if (!retryFailed && saved?.status == AnalysisStatus.COMPLETED) {
                onProgress(saved.toProgress(record.id))
                return
            }
            if (!retryFailed && saved?.status == AnalysisStatus.FAILED) {
                onProgress(saved.toProgress(record.id))
                return
            }

            val analysisId = saved?.analysisId
                ?.takeIf { saved.status in POLLING_STATUSES }
                ?: upload(record, onProgress)
            poll(record.id, analysisId, onProgress)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AnalysisFailure) {
            val record = recordProvider.getRecordBySessionId(sessionId)
            if (record != null) {
                val failed = CollectionAnalysis(
                    analysisId = failure.analysisId,
                    status = AnalysisStatus.FAILED,
                    errorCode = failure.code,
                    errorMessage = failure.message,
                    updatedAtEpochMillis = now(),
                )
                recordProvider.saveAnalysis(record.id, failed)
                onProgress(failed.toProgress(record.id))
            } else {
                onProgress(
                    AnalysisProgress(
                        recordId = "",
                        status = AnalysisStatus.FAILED,
                        errorMessage = failure.message,
                    ),
                )
            }
        } catch (failure: IOException) {
            saveUnexpectedFailure(sessionId, failure.message ?: "网络连接失败", onProgress)
        } catch (failure: Throwable) {
            saveUnexpectedFailure(sessionId, failure.message ?: "上传或分析失败", onProgress)
        }
    }

    private suspend fun upload(
        record: CollectionRecord,
        onProgress: (AnalysisProgress) -> Unit,
    ): String {
        val path = record.localFilePath
            ?: throw AnalysisFailure(null, "采集记录缺少本地文件", false)
        val file = File(path)
        if (!file.isFile) throw AnalysisFailure(null, "本地 DICOM 文件不存在", false)

        saveAndEmit(
            recordId = record.id,
            analysis = CollectionAnalysis(
                analysisId = null,
                status = AnalysisStatus.UPLOADING,
                updatedAtEpochMillis = now(),
            ),
            uploadProgress = 0,
            onProgress = onProgress,
        )
        var lastProgress = -1
        val response = remoteDataSource.upload(
            file = file,
            appVersion = appVersion,
            protocolVersion = protocolVersion,
        ) { progress ->
            if (progress != lastProgress) {
                lastProgress = progress
                onProgress(
                    AnalysisProgress(
                        recordId = record.id,
                        status = AnalysisStatus.UPLOADING,
                        uploadProgress = progress,
                    ),
                )
            }
        }
        val body = requireBusinessBody(response)
        if (body.code !in UPLOAD_SUCCESS_CODES) {
            throw body.toFailure()
        }
        val data = body.data
            ?: throw AnalysisFailure(body.code, "服务端未返回分析任务信息", false)
        val analysisId = data.analysisId?.takeIf(String::isNotBlank)
            ?: throw AnalysisFailure(body.code, "服务端未返回 analysis_id", false)
        val status = data.status.toAnalysisStatus(default = AnalysisStatus.PROCESSING)
        saveAndEmit(
            recordId = record.id,
            analysis = CollectionAnalysis(
                analysisId = analysisId,
                status = status,
                updatedAtEpochMillis = now(),
            ),
            onProgress = onProgress,
        )
        return analysisId
    }

    private suspend fun poll(
        recordId: String,
        analysisId: String,
        onProgress: (AnalysisProgress) -> Unit,
    ) {
        var temporaryFailureCount = 0
        repeat(maxPollAttempts) { attempt ->
            if (attempt > 0) sleep(pollIntervalMillis)
            val response = try {
                remoteDataSource.getResult(analysisId)
            } catch (error: IOException) {
                temporaryFailureCount += 1
                if (temporaryFailureCount >= MAX_TEMPORARY_FAILURES) throw error
                return@repeat
            }
            if (!response.isHttpSuccessful &&
                (response.httpCode == 503 || response.errorCode == CODE_SERVICE_UNAVAILABLE)
            ) {
                temporaryFailureCount += 1
                if (temporaryFailureCount >= MAX_TEMPORARY_FAILURES) {
                    throw AnalysisFailure(
                        code = response.errorCode ?: response.httpCode,
                        message = response.errorMessage ?: "分析服务暂不可用",
                        retryable = true,
                        analysisId = analysisId,
                    )
                }
                return@repeat
            }
            val body = requireBusinessBody(response, analysisId)
            when (body.code) {
                CODE_COMPLETED -> {
                    val data = body.data
                        ?: throw AnalysisFailure(body.code, "服务端未返回分析结果", false, analysisId)
                    val completed = CollectionAnalysis(
                        analysisId = analysisId,
                        status = AnalysisStatus.COMPLETED,
                        resultMarkdown = data.result.orEmpty(),
                        updatedAtEpochMillis = now(),
                    )
                    saveAndEmit(recordId, completed, onProgress = onProgress)
                    return
                }

                CODE_PROCESSING, CODE_QUEUED, CODE_RETRYING -> {
                    temporaryFailureCount = 0
                    val status = when (body.code) {
                        CODE_QUEUED -> AnalysisStatus.QUEUED
                        CODE_RETRYING -> AnalysisStatus.RETRYING
                        else -> AnalysisStatus.PROCESSING
                    }
                    saveAndEmit(
                        recordId,
                        CollectionAnalysis(
                            analysisId = analysisId,
                            status = status,
                            updatedAtEpochMillis = now(),
                        ),
                        onProgress = onProgress,
                    )
                }

                CODE_SERVICE_UNAVAILABLE -> {
                    temporaryFailureCount += 1
                    if (temporaryFailureCount >= MAX_TEMPORARY_FAILURES) {
                        throw body.toFailure(analysisId)
                    }
                }

                else -> throw body.toFailure(analysisId)
            }
        }
        throw AnalysisFailure(null, "分析等待超时，请稍后重试", true, analysisId)
    }

    private suspend fun saveAndEmit(
        recordId: String,
        analysis: CollectionAnalysis,
        uploadProgress: Int = if (analysis.status == AnalysisStatus.UPLOADING) 0 else 100,
        onProgress: (AnalysisProgress) -> Unit,
    ) {
        if (!recordProvider.saveAnalysis(recordId, analysis)) {
            throw AnalysisFailure(null, "分析状态保存失败", false, analysis.analysisId)
        }
        onProgress(analysis.toProgress(recordId, uploadProgress))
    }

    private suspend fun requireRecord(sessionId: String): CollectionRecord =
        recordProvider.getRecordBySessionId(sessionId)
            ?: throw AnalysisFailure(null, "未找到本次采集记录", false)

    private fun <T> requireBusinessBody(
        response: RemoteResponse<T>,
        analysisId: String? = null,
    ): ApiEnvelope<T> {
        if (!response.isHttpSuccessful) {
            throw AnalysisFailure(
                code = response.errorCode ?: response.httpCode,
                message = response.errorMessage ?: "服务器请求失败（HTTP ${response.httpCode}）",
                retryable = response.httpCode == 503,
                analysisId = analysisId,
            )
        }
        return response.body
            ?: throw AnalysisFailure(null, "服务器返回为空", false, analysisId)
    }

    private suspend fun saveUnexpectedFailure(
        sessionId: String,
        message: String,
        onProgress: (AnalysisProgress) -> Unit,
    ) {
        val record = recordProvider.getRecordBySessionId(sessionId)
        val analysis = CollectionAnalysis(
            analysisId = record?.analysis?.analysisId,
            status = AnalysisStatus.FAILED,
            errorMessage = message,
            updatedAtEpochMillis = now(),
        )
        if (record != null) recordProvider.saveAnalysis(record.id, analysis)
        onProgress(analysis.toProgress(record?.id.orEmpty()))
    }

    private fun ApiEnvelope<*>.toFailure(analysisId: String? = null) = AnalysisFailure(
        code = code,
        message = message.ifBlank { "服务端返回错误（$code）" },
        retryable = code == CODE_CHECKSUM_FAILED || code == CODE_SERVICE_UNAVAILABLE,
        analysisId = analysisId,
    )

    private class AnalysisFailure(
        val code: Int?,
        override val message: String,
        val retryable: Boolean,
        val analysisId: String? = null,
    ) : Exception(message)

    private companion object {
        val UPLOAD_SUCCESS_CODES = setOf(100, 101)
        val POLLING_STATUSES = setOf(
            AnalysisStatus.QUEUED,
            AnalysisStatus.PROCESSING,
            AnalysisStatus.RETRYING,
        )
        const val CODE_COMPLETED = 0
        const val CODE_PROCESSING = 100
        const val CODE_QUEUED = 102
        const val CODE_RETRYING = 103
        const val CODE_CHECKSUM_FAILED = 1004
        const val CODE_SERVICE_UNAVAILABLE = 5101
        const val MAX_TEMPORARY_FAILURES = 3
    }
}

private fun String?.toAnalysisStatus(default: AnalysisStatus): AnalysisStatus = when (this) {
    "queued" -> AnalysisStatus.QUEUED
    "processing" -> AnalysisStatus.PROCESSING
    "retrying" -> AnalysisStatus.RETRYING
    "completed" -> AnalysisStatus.COMPLETED
    "failed" -> AnalysisStatus.FAILED
    else -> default
}

private fun CollectionAnalysis.toProgress(
    recordId: String,
    uploadProgress: Int = if (status == AnalysisStatus.UPLOADING) 0 else 100,
) = AnalysisProgress(
    recordId = recordId,
    status = status,
    uploadProgress = uploadProgress,
    resultMarkdown = resultMarkdown,
    errorMessage = errorMessage,
)
