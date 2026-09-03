package com.smarthealth.vitalhub.feature.analysis.data

internal enum class AnalysisRequestPhase { UPLOAD, QUERY }

internal enum class AnalysisFailureAction {
    NONE,
    RETRY_UPLOAD,
    RESUME_QUERY,
    RESTART_ANALYSIS,
    RECOLLECT_DATA,
}

internal enum class AnalysisWaitingStatus { QUEUED, PROCESSING, RETRYING }

/** Complete, valid UI states for the upload-and-analysis flow. */
internal sealed interface AnalysisTaskState {
    data class Uploading(val progress: Int) : AnalysisTaskState

    data class Waiting(
        val status: AnalysisWaitingStatus,
        val message: String? = null,
    ) : AnalysisTaskState

    data class Completed(val markdown: String) : AnalysisTaskState

    data class Failed(
        val message: String,
        val action: AnalysisFailureAction,
    ) : AnalysisTaskState
}
