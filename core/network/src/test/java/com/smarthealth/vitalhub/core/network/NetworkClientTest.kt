package com.smarthealth.vitalhub.core.network

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import retrofit2.Call
import retrofit2.http.GET

class NetworkClientTest {
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
    fun `create service converts json response`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok"}"""),
        )
        val service = NetworkClient.create(NetworkConfig(server.url("/").toString()))
            .createService<TestService>()

        val response = service.status().execute()

        assertEquals("ok", response.body()?.status)
        assertEquals("/status", server.takeRequest().path)
    }

    @Test
    fun `headers providers are evaluated for every request and later values win`() {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        var token = "first"
        val service = NetworkClient.create(
            config = NetworkConfig(server.url("/").toString()),
            headersProviders = listOf(
                NetworkHeadersProvider { mapOf("Authorization" to "Bearer stale") },
                NetworkHeadersProvider { mapOf("Authorization" to "Bearer $token") },
            ),
        ).createService<TestService>()
        token = "fresh"

        service.status().execute()

        assertEquals("Bearer fresh", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `base url requires trailing slash`() {
        assertThrows(IllegalArgumentException::class.java) {
            NetworkConfig("https://example.com/api")
        }
    }

    private interface TestService {
        @GET("status")
        fun status(): Call<StatusDto>
    }

    private data class StatusDto(val status: String)
}
