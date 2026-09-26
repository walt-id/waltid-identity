package id.walt.itb

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ItbReferenceAuthorizationTest {
    private val origin = Url("https://testbed.example")
    private val authorization = Url("https://testbed.example/authorize?request_uri=synthetic")
    private val callback = Url("openid://")

    @Test
    fun `returns reference callback without navigating it`() = runBlocking<Unit> {
        var calls = 0
        HttpClient(MockEngine {
            calls++
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "openid://?code=test-code&state=test-state"))
        }).use { client ->
            val result = ItbReferenceAuthorization.resolve(client, origin, authorization, callback)
            assertEquals("test-code", result.parameters["code"])
            assertEquals(1, calls)
        }
    }

    @Test
    fun `rejects an external endpoint before sending authorization data`() = runBlocking<Unit> {
        HttpClient(MockEngine { error("No request expected") }).use { client ->
            assertFailsWith<IllegalArgumentException> {
                ItbReferenceAuthorization.resolve(client, origin, Url("https://another.example/authorize"), callback)
            }
        }
    }

    @Test
    fun `does not follow another destination or silently accept an interactive login`() = runBlocking<Unit> {
        for ((status, location) in listOf(
            HttpStatusCode.Found to "https://another.example/authorize",
            HttpStatusCode.Found to "openid://other?code=secret",
            HttpStatusCode.OK to "openid://?code=secret",
        )) {
            var calls = 0
            HttpClient(MockEngine { calls++; respond("", status, headersOf(HttpHeaders.Location, location)) }).use { client ->
                assertFails { ItbReferenceAuthorization.resolve(client, origin, authorization, callback) }
                assertEquals(1, calls)
            }
        }
    }
}
