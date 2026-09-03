package com.smarthealth.vitalhub.feature.analysis.data

internal data class AnalysisProblem(
    val phase: AnalysisRequestPhase,
    val httpCode: Int?,
    val businessCode: Int?,
    val message: String,
    val action: AnalysisFailureAction,
)

internal sealed interface CreateAnalysisOutcome {
    data class Accepted(
        val analysisId: String,
        val status: AnalysisWaitingStatus,
        val pollIntervalMillis: Long? = null,
    ) : CreateAnalysisOutcome

    data class Failed(val problem: AnalysisProblem) : CreateAnalysisOutcome
}

internal sealed interface QueryAnalysisOutcome {
    data class Pending(
        val status: AnalysisWaitingStatus,
        val pollIntervalMillis: Long? = null,
    ) : QueryAnalysisOutcome
    data class Completed(val markdown: String) : QueryAnalysisOutcome
    data class TemporaryFailure(val problem: AnalysisProblem) : QueryAnalysisOutcome
    data class Failed(val problem: AnalysisProblem) : QueryAnalysisOutcome
}

internal object AnalyzeResponseMapper {
    fun map(result: RestResult<CreateAnalysisData>): CreateAnalysisOutcome = when (result) {
        is RestResult.Success -> mapSuccessfulHttp(result)
        is RestResult.HttpFailure -> CreateAnalysisOutcome.Failed(result.toUploadProblem())
        is RestResult.NetworkFailure -> CreateAnalysisOutcome.Failed(
            AnalysisProblem(
                phase = AnalysisRequestPhase.UPLOAD,
                httpCode = null,
                businessCode = null,
                message = result.cause.message ?: "网络连接失败",
                action = AnalysisFailureAction.RETRY_UPLOAD,
            ),
        )
        is RestResult.InvalidResponse -> CreateAnalysisOutcome.Failed(
            AnalysisProblem(
                phase = AnalysisRequestPhase.UPLOAD,
                httpCode = result.httpCode,
                businessCode = null,
                message = result.message,
                action = AnalysisFailureAction.RETRY_UPLOAD,
            ),
        )
    }

    private fun mapSuccessfulHttp(
        result: RestResult.Success<CreateAnalysisData>,
    ): CreateAnalysisOutcome {
        val envelope = result.body
        val data = envelope.data
        val analysisId = data?.analysisId?.takeIf(String::isNotBlank)
            ?: return failedProtocol(result.httpCode, envelope.code, "服务器已接收数据，但未返回 analysis_id")
        val businessCode = AnalysisBusinessCode.from(envelope.code)
        val status = when (businessCode) {
            AnalysisBusinessCode.PROCESSING,
            AnalysisBusinessCode.ALREADY_RECEIVED -> data.status.toPendingStatus()
                ?: AnalysisWaitingStatus.PROCESSING
            AnalysisBusinessCode.QUEUED -> AnalysisWaitingStatus.QUEUED
            AnalysisBusinessCode.RETRYING -> AnalysisWaitingStatus.RETRYING
            else -> if (businessCode == null) {
                data.status.toPendingStatus()
                    ?: return failedProtocol(
                        result.httpCode,
                        envelope.code,
                        "无法识别上传响应：HTTP ${result.httpCode}，业务码 ${envelope.code}",
                    )
            } else {
                return failedProtocol(
                    result.httpCode,
                    envelope.code,
                    "上传成功响应包含不适用的业务码 ${envelope.code}",
                )
            }
        }
        return CreateAnalysisOutcome.Accepted(
            analysisId = analysisId,
            status = status,
            pollIntervalMillis = data.pollIntervalSecs.toPollIntervalMillis(),
        )
    }

    private fun failedProtocol(
        httpCode: Int,
        businessCode: Int,
        message: String,
    ) = CreateAnalysisOutcome.Failed(
        AnalysisProblem(
            phase = AnalysisRequestPhase.UPLOAD,
            httpCode = httpCode,
            businessCode = businessCode,
            message = message,
            action = AnalysisFailureAction.RETRY_UPLOAD,
        ),
    )
}

internal object AnalysisResultResponseMapper {
    fun map(
        analysisId: String,
        result: RestResult<AnalysisResultData>,
    ): QueryAnalysisOutcome = when (result) {
        is RestResult.Success -> mapSuccessfulHttp(analysisId, result)
        is RestResult.HttpFailure -> {
            val problem = result.toPollProblem()
            if (result.httpCode == 503) {
                QueryAnalysisOutcome.TemporaryFailure(problem)
            } else {
                QueryAnalysisOutcome.Failed(problem)
            }
        }
        is RestResult.NetworkFailure -> QueryAnalysisOutcome.TemporaryFailure(
            AnalysisProblem(
                phase = AnalysisRequestPhase.QUERY,
                httpCode = null,
                businessCode = null,
                message = result.cause.message ?: "网络连接失败",
                action = AnalysisFailureAction.RESUME_QUERY,
            ),
        )
        is RestResult.InvalidResponse -> QueryAnalysisOutcome.Failed(
            AnalysisProblem(
                phase = AnalysisRequestPhase.QUERY,
                httpCode = result.httpCode,
                businessCode = null,
                message = result.message,
                action = AnalysisFailureAction.NONE,
            ),
        )
    }

    private fun mapSuccessfulHttp(
        analysisId: String,
        result: RestResult.Success<AnalysisResultData>,
    ): QueryAnalysisOutcome {
        val envelope = result.body
        val data = envelope.data
        val returnedId = data?.analysisId
        if (!returnedId.isNullOrBlank() && returnedId != analysisId) {
            return failedProtocol(result.httpCode, envelope.code, "服务器返回了不匹配的 analysis_id")
        }
        val expectedStatus = when (AnalysisBusinessCode.from(envelope.code)) {
            AnalysisBusinessCode.COMPLETED -> null
            AnalysisBusinessCode.PROCESSING -> AnalysisWaitingStatus.PROCESSING
            AnalysisBusinessCode.QUEUED -> AnalysisWaitingStatus.QUEUED
            AnalysisBusinessCode.RETRYING -> AnalysisWaitingStatus.RETRYING
            else -> return failedProtocol(
                result.httpCode,
                envelope.code,
                "无法识别查询响应：HTTP ${result.httpCode}，业务码 ${envelope.code}",
            )
        }
        val completed = envelope.code == AnalysisBusinessCode.COMPLETED.value
        val actualStatus = data?.status.toKnownStatus()
        if (actualStatus != null && actualStatus != expectedStatusName(completed, expectedStatus)) {
            return failedProtocol(result.httpCode, envelope.code, "分析状态与业务码不一致")
        }
        return if (completed) {
            QueryAnalysisOutcome.Completed(data?.result.orEmpty())
        } else {
            QueryAnalysisOutcome.Pending(
                status = checkNotNull(expectedStatus),
                pollIntervalMillis = data?.pollIntervalSecs.toPollIntervalMillis(),
            )
        }
    }

    private fun failedProtocol(
        httpCode: Int,
        businessCode: Int,
        message: String,
    ) = QueryAnalysisOutcome.Failed(
        AnalysisProblem(
            phase = AnalysisRequestPhase.QUERY,
            httpCode = httpCode,
            businessCode = businessCode,
            message = message,
            action = AnalysisFailureAction.NONE,
        ),
    )
}

private fun RestResult.HttpFailure.toUploadProblem(): AnalysisProblem {
    val code = businessCode?.let(AnalysisBusinessCode::from)
    val action = when (httpCode) {
        400 -> when (code) {
            AnalysisBusinessCode.CHECKSUM_FAILED -> AnalysisFailureAction.RETRY_UPLOAD
            AnalysisBusinessCode.UNSUPPORTED_PROTOCOL,
            AnalysisBusinessCode.INVALID_DATA_FORMAT -> AnalysisFailureAction.RECOLLECT_DATA
            else -> AnalysisFailureAction.NONE
        }
        503 -> AnalysisFailureAction.RETRY_UPLOAD
        else -> AnalysisFailureAction.NONE
    }
    return AnalysisProblem(
        phase = AnalysisRequestPhase.UPLOAD,
        httpCode = httpCode,
        businessCode = businessCode,
        message = message?.takeIf(String::isNotBlank) ?: code.defaultMessage(httpCode),
        action = action,
    )
}

private fun RestResult.HttpFailure.toPollProblem(): AnalysisProblem {
    val code = businessCode?.let(AnalysisBusinessCode::from)
    val action = when (httpCode) {
        503 -> AnalysisFailureAction.RESUME_QUERY
        404, 500, 504 -> AnalysisFailureAction.RESTART_ANALYSIS
        else -> AnalysisFailureAction.NONE
    }
    return AnalysisProblem(
        phase = AnalysisRequestPhase.QUERY,
        httpCode = httpCode,
        businessCode = businessCode,
        message = message?.takeIf(String::isNotBlank) ?: code.defaultMessage(httpCode),
        action = action,
    )
}

private fun AnalysisBusinessCode?.defaultMessage(httpCode: Int): String = when (this) {
    AnalysisBusinessCode.INVALID_PARAMETER -> "上传参数不正确"
    AnalysisBusinessCode.UNSUPPORTED_PROTOCOL -> "当前数据协议版本不受支持"
    AnalysisBusinessCode.INVALID_DATA_FORMAT -> "采集数据格式无法解析，请重新采集"
    AnalysisBusinessCode.CHECKSUM_FAILED -> "数据完整性校验失败，请重新上传"
    AnalysisBusinessCode.UNAUTHORIZED -> "分析服务认证失败"
    AnalysisBusinessCode.FORBIDDEN -> "无权查询该分析任务"
    AnalysisBusinessCode.NOT_FOUND -> "分析任务不存在"
    AnalysisBusinessCode.SESSION_CONFLICT -> "采集记录与服务端已有记录冲突"
    AnalysisBusinessCode.ANALYSIS_FAILED -> "分析算法执行失败"
    AnalysisBusinessCode.SERVICE_UNAVAILABLE -> "分析服务暂不可用"
    AnalysisBusinessCode.ANALYSIS_TIMEOUT -> "分析任务执行超时"
    else -> "服务器请求失败（HTTP $httpCode）"
}

private fun String?.toPendingStatus(): AnalysisWaitingStatus? = when (this) {
    "queued" -> AnalysisWaitingStatus.QUEUED
    "processing" -> AnalysisWaitingStatus.PROCESSING
    "retrying" -> AnalysisWaitingStatus.RETRYING
    else -> null
}

private fun String?.toKnownStatus(): String? = this?.takeIf {
    it in setOf("queued", "processing", "retrying", "completed", "failed")
}

private fun expectedStatusName(
    completed: Boolean,
    waitingStatus: AnalysisWaitingStatus?,
): String = if (completed) {
    "completed"
} else {
    checkNotNull(waitingStatus).name.lowercase()
}

private fun Long?.toPollIntervalMillis(): Long? = this
    ?.takeIf { it > 0L }
    ?.coerceAtMost(Long.MAX_VALUE / MILLIS_PER_SECOND)
    ?.times(MILLIS_PER_SECOND)

private const val MILLIS_PER_SECOND = 1_000L
