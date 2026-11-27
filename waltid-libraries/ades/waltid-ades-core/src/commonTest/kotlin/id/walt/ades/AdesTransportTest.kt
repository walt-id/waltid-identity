package id.walt.ades

import id.walt.ades.transport.KtorAdesTransport
import id.walt.ades.transport.AdesTransportException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdesTransportTest {
    @Test
    fun appliesUrlPolicyBeforeGetAndPost() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond("ok")
        }
        HttpClient(engine) { followRedirects = false }.use { client ->
            val transport = KtorAdesTransport(client) { require(it.startsWith("https://allowed.example/")) }
            assertContentEquals("ok".encodeToByteArray(), transport.get("https://allowed.example/doc"))
            assertContentEquals("ok".encodeToByteArray(), transport.post("https://allowed.example/ocsp", "application/ocsp-request", byteArrayOf()))
            assertFailsWith<IllegalArgumentException> { transport.get("http://127.0.0.1/private") }
            assertFailsWith<IllegalArgumentException> { transport.post("http://127.0.0.1/private", "text/plain", byteArrayOf()) }
            assertEquals(2, requests)
        }
    }

    @Test
    fun refusesAutomaticRedirectsAndDoesNotFollowRedirectResponses() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "http://127.0.0.1/private"))
        }
        HttpClient(engine).use { client ->
            assertFailsWith<IllegalArgumentException> { KtorAdesTransport(client) {} }
        }
        HttpClient(MockEngine {
            requests++
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "http://127.0.0.1/private"))
        }) { followRedirects = false }.use { client ->
            assertFailsWith<AdesTransportException> { KtorAdesTransport(client) {}.get("https://allowed.example/doc") }
        }
        assertEquals(1, requests)
    }
}
