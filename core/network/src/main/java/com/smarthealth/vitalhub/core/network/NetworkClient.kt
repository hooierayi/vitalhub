package com.smarthealth.vitalhub.core.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class NetworkClient private constructor(
    private val retrofit: Retrofit,
) {
    fun <Service : Any> createService(serviceClass: Class<Service>): Service =
        retrofit.create(serviceClass)

    inline fun <reified Service : Any> createService(): Service =
        createService(Service::class.java)

    companion object {
        fun create(
            config: NetworkConfig,
            headersProviders: List<NetworkHeadersProvider> = emptyList(),
        ): NetworkClient {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(config.connectTimeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(config.readTimeoutMillis, TimeUnit.MILLISECONDS)
                .writeTimeout(config.writeTimeoutMillis, TimeUnit.MILLISECONDS)
                .apply {
                    if (headersProviders.isNotEmpty()) {
                        addInterceptor(headersInterceptor(headersProviders.toList()))
                    }
                    if (config.logLevel != NetworkLogLevel.NONE) {
                        addInterceptor(
                            loggingInterceptor(
                                logLevel = config.logLevel,
                                redactedHeaderNames = config.redactedHeaderNames,
                            ),
                        )
                    }
                }
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(config.baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return NetworkClient(retrofit)
        }

        private fun headersInterceptor(
            providers: List<NetworkHeadersProvider>,
        ) = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            providers.forEach { provider ->
                provider.headers().forEach { (name, value) ->
                    requestBuilder.header(name, value)
                }
            }
            chain.proceed(requestBuilder.build())
        }

        private fun loggingInterceptor(
            logLevel: NetworkLogLevel,
            redactedHeaderNames: Set<String>,
        ) =
            HttpLoggingInterceptor().apply {
                redactedHeaderNames.forEach(::redactHeader)
                level = when (logLevel) {
                    NetworkLogLevel.NONE -> HttpLoggingInterceptor.Level.NONE
                    NetworkLogLevel.BASIC -> HttpLoggingInterceptor.Level.BASIC
                    NetworkLogLevel.HEADERS -> HttpLoggingInterceptor.Level.HEADERS
                    NetworkLogLevel.BODY -> HttpLoggingInterceptor.Level.BODY
                }
            }
    }
}
