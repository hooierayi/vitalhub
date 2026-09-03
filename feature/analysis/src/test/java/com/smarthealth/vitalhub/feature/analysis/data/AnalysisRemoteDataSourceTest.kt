package com.smarthealth.vitalhub.feature.analysis.data

import com.smarthealth.vitalhub.core.network.NetworkClient
import com.smarthealth.vitalhub.core.network.NetworkConfig
import com.smarthealth.vitalhub.core.network.NetworkHeadersProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnalysisRemoteDataSourceTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `upload sends documented multipart fields and api key`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"code":100,"message":"started","data":{"session_id":"S1","analysis_id":"A1","status":"processing"}}""",
                ),
        )
        val file = File.createTempFile("analysis", ".dcm").apply {
            writeBytes("dicom-payload".toByteArray())
            deleteOnExit()
        }
        val client = NetworkClient.create(
            config = NetworkConfig(server.url("/").toString()),
            headersProviders = listOf(
                NetworkHeadersProvider { mapOf("X-API-Key" to "test-key") },
            ),
        )
        val remote = RetrofitAnalysisRemoteDataSource(client.createService<AnalysisApi>())

        val response = remote.upload(file, "1.2.3", "1.0") {}

        assertTrue(response is RestResult.Success)
        response as RestResult.Success
        assertEquals(202, response.httpCode)
        assertEquals("A1", response.body.data?.analysisId)
        val request = server.takeRequest()
        assertEquals("/api/v1/analyze", request.path)
        assertEquals("test-key", request.getHeader("X-API-Key"))
        val requestBody = request.body.readUtf8()
        assertTrue(requestBody.contains("name=\"data\""))
        assertTrue(requestBody.contains("filename=\"${file.name}\""))
        assertTrue(requestBody.contains("application/dicom"))
        assertTrue(requestBody.contains("name=\"app_version\""))
        assertTrue(requestBody.contains("1.2.3"))
        assertTrue(requestBody.contains("name=\"protocol_version\""))
    }

    @Test
    fun `non successful http remains failure even when body contains processing code`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":100,"message":"invalid request"}"""),
        )
        val file = File.createTempFile("analysis", ".dcm").apply { deleteOnExit() }
        val client = NetworkClient.create(NetworkConfig(server.url("/").toString()))
        val remote = RetrofitAnalysisRemoteDataSource(client.createService<AnalysisApi>())

        val response = remote.upload(file, "1.2.3", "1.0") {}

        assertTrue(response is RestResult.HttpFailure)
        response as RestResult.HttpFailure
        assertEquals(400, response.httpCode)
        assertEquals(100, response.businessCode)
    }
}
