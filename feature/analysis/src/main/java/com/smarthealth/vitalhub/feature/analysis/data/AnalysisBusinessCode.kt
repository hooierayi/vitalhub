package com.smarthealth.vitalhub.feature.analysis.data

internal enum class AnalysisBusinessCode(val value: Int) {
    COMPLETED(0),
    PROCESSING(100),
    ALREADY_RECEIVED(101),
    QUEUED(102),
    RETRYING(103),
    INVALID_PARAMETER(1001),
    UNSUPPORTED_PROTOCOL(1002),
    INVALID_DATA_FORMAT(1003),
    CHECKSUM_FAILED(1004),
    UNAUTHORIZED(1101),
    FORBIDDEN(1102),
    NOT_FOUND(1201),
    SESSION_CONFLICT(1301),
    ANALYSIS_FAILED(5003),
    SERVICE_UNAVAILABLE(5101),
    ANALYSIS_TIMEOUT(5102),
    ;

    companion object {
        fun from(value: Int): AnalysisBusinessCode? = entries.firstOrNull { it.value == value }
    }
}
