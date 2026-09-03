package com.smarthealth.vitalhub.feature.analysis.data

import com.smarthealth.vitalhub.core.network.NetworkClient
import com.smarthealth.vitalhub.core.network.NetworkConfig
import com.smarthealth.vitalhub.core.network.NetworkHeadersProvider
import com.smarthealth.vitalhub.core.network.NetworkLogLevel
import com.smarthealth.vitalhub.feature.analysis.BuildConfig

internal object AnalysisNetwork {
    val remoteDataSource: AnalysisRemoteDataSource by lazy {
        check(BuildConfig.ANALYSIS_API_KEY.isNotBlank()) {
            "未配置分析服务 API Key，请设置 Gradle 属性 analysisApiKey"
        }
        val client = NetworkClient.create(
            config = NetworkConfig(
                baseUrl = BuildConfig.ANALYSIS_BASE_URL,
                readTimeoutMillis = 60_000L,
                writeTimeoutMillis = 120_000L,
                logLevel = if (BuildConfig.DEBUG) {
                    NetworkLogLevel.BODY
                } else {
                    NetworkLogLevel.NONE
                },
                redactedHeaderNames = NetworkConfig.DEFAULT_REDACTED_HEADER_NAMES + "X-API-Key",
            ),
            headersProviders = listOf(
                NetworkHeadersProvider {
                    mapOf("X-API-Key" to BuildConfig.ANALYSIS_API_KEY)
                },
            ),
        )
        RetrofitAnalysisRemoteDataSource(client.createService<AnalysisApi>())
    }
}
