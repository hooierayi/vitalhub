package com.smarthealth.vitalhub.core.network

/** Runtime-independent configuration for a single backend client. */
data class NetworkConfig(
    val baseUrl: String,
    val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    val readTimeoutMillis: Long = DEFAULT_READ_TIMEOUT_MILLIS,
    val writeTimeoutMillis: Long = DEFAULT_WRITE_TIMEOUT_MILLIS,
    val logLevel: NetworkLogLevel = NetworkLogLevel.NONE,
    val redactedHeaderNames: Set<String> = DEFAULT_REDACTED_HEADER_NAMES,
) {
    init {
        require(baseUrl.endsWith('/')) { "baseUrl must end with '/'" }
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be positive" }
        require(readTimeoutMillis > 0) { "readTimeoutMillis must be positive" }
        require(writeTimeoutMillis > 0) { "writeTimeoutMillis must be positive" }
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 15_000L
        const val DEFAULT_READ_TIMEOUT_MILLIS = 30_000L
        const val DEFAULT_WRITE_TIMEOUT_MILLIS = 30_000L
        val DEFAULT_REDACTED_HEADER_NAMES = setOf("Authorization", "Cookie", "Set-Cookie")
    }
}

enum class NetworkLogLevel {
    NONE,
    BASIC,
    HEADERS,
    BODY,
}

/** Called for every request so credentials can be refreshed without rebuilding the client. */
fun interface NetworkHeadersProvider {
    fun headers(): Map<String, String>
}
