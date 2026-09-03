package com.smarthealth.vitalhub.feature.analysis.data

import com.smarthealth.vitalhub.core.network.NetworkClient
import com.smarthealth.vitalhub.core.network.NetworkConfig
import com.smarthealth.vitalhub.feature.analysis.debug.AnalysisMockConfig
import com.smarthealth.vitalhub.feature.analysis.debug.AnalysisMockInterceptor
import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisMockInterceptorTest {
    private val remoteDataSource = RetrofitAnalysisRemoteDataSource(
        NetworkClient.create(
            config = NetworkConfig("https://debug-markdown.invalid/"),
            headersProviders = emptyList(),
            interceptors = listOf(AnalysisMockInterceptor()),
        ).createService<AnalysisApi>(),
    )

    @Test
    fun `mock upload and query return a completed markdown showcase`() = runTest {
        AnalysisMockConfig.setEnabled(true)
        val file = File.createTempFile("analysis-markdown", ".dcm").apply {
            writeText("debug")
            deleteOnExit()
        }
        try {
            val upload = remoteDataSource.upload(file, "debug", "1.0") {}
            assertTrue(upload is RestResult.Success)
            upload as RestResult.Success
            assertEquals(202, upload.httpCode)
            assertEquals(
                AnalysisMockInterceptor.MOCK_ANALYSIS_ID,
                upload.body.data?.analysisId,
            )

            val query = remoteDataSource.getResult(AnalysisMockInterceptor.MOCK_ANALYSIS_ID)
            assertTrue(query is RestResult.Success)
            query as RestResult.Success
            assertEquals(200, query.httpCode)
            assertEquals("completed", query.body.data?.status)
            assertEquals(
                AnalysisMockInterceptor.MARKDOWN_SHOWCASE,
                query.body.data?.result,
            )
        } finally {
            AnalysisMockConfig.setEnabled(false)
        }
    }

    @Test
    fun `disabled mock delegates to the configured service`() = runTest {
        AnalysisMockConfig.setEnabled(false)
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"code":0,"message":"online","data":{"analysis_id":"A1","status":"completed","result":"online result"}}""",
                    ),
            )
            val onlineRemote = RetrofitAnalysisRemoteDataSource(
                NetworkClient.create(
                    config = NetworkConfig(server.url("/").toString()),
                    headersProviders = emptyList(),
                    interceptors = listOf(AnalysisMockInterceptor()),
                ).createService<AnalysisApi>(),
            )

            val query = onlineRemote.getResult("A1")

            assertTrue(query is RestResult.Success)
            query as RestResult.Success
            assertEquals("online result", query.body.data?.result)
            assertEquals("/api/v1/result/A1", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }
}
