package id.walt.webdatafetching

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentNegotiationHeadersTest {
    @Test
    fun explicitProtocolAcceptHeadersArePreserved() = runTest {
        val expected = listOf("application/oauth-authz-req+jwt", "application/json;q=0")
        val client = HttpClient(MockEngine { request ->
            assertEquals(expected, request.headers.getAll(HttpHeaders.Accept))
            respond("signed-request", headers = headersOf(HttpHeaders.ContentType, "application/oauth-authz-req+jwt"))
        }) {
            WebDataFetchingConfiguration().applyConfigurationToHttpClient(this)
        }
        try {
            assertEquals("signed-request", client.get("https://verifier.example/request") {
                expected.forEach { header(HttpHeaders.Accept, it) }
            }.bodyAsText())
        } finally {
            client.close()
        }
    }

    @Test
    fun requestsWithoutAcceptRetainTheJsonDefault() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(listOf("application/json"), request.headers.getAll(HttpHeaders.Accept))
            respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            WebDataFetchingConfiguration().applyConfigurationToHttpClient(this)
        }
        try {
            assertEquals("{}", client.get("https://issuer.example/metadata").bodyAsText())
        } finally {
            client.close()
        }
    }
}
