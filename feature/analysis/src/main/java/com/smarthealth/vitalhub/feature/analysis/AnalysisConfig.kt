package com.smarthealth.vitalhub.feature.analysis

/** Analysis feature configuration. Polling intervals use seconds as the public unit. */
internal object AnalysisConfig {
    const val POLL_FALLBACK_INTERVAL_SECONDS = 10L
    const val POLL_FALLBACK_INTERVAL_MILLIS = POLL_FALLBACK_INTERVAL_SECONDS * 1_000L

    init {
        require(POLL_FALLBACK_INTERVAL_SECONDS > 0L) {
            "Poll fallback interval must be at least 1 second"
        }
    }
}
