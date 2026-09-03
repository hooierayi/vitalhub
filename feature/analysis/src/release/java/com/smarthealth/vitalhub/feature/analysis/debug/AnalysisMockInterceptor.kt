package com.smarthealth.vitalhub.feature.analysis.debug

import okhttp3.Interceptor
import okhttp3.Response

/** Release variant safeguard: mock responses are unavailable and all requests pass through. */
internal object AnalysisMockConfig {
    const val enabled: Boolean = false
}

internal class AnalysisMockInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
