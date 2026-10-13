package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.http.ConformanceStateRequestException
import id.walt.openid4vp.conformance.testplans.http.getConformanceState
import id.walt.openid4vp.conformance.testplans.http.safeConformanceFailure
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConformanceStateRequestTest {
    @Test
    fun transientHttpFailuresRecoverBeforeReadingWaitingState() = runTest {
        for (status in listOf(429, 502, 503, 504)) {
            var requests = 0
            HttpClient(MockEngine { request ->
                assertEquals(HttpMethod.Get, request.method)
                assertEquals("no-cache", request.headers[HttpHeaders.CacheControl])
                requests++
                if (requests < 3) respond("upstream unavailable", HttpStatusCode.fromValue(status))
                else respond("""{"status":"WAITING"}""")
            }).use { client ->
                assertEquals("""{"status":"WAITING"}""", client.getConformanceState("https://suite.example/api/info/test", "Read test status").bodyAsText())
                assertEquals(3, requests)
            }
        }
    }

    @Test
    fun transientTransportFailureRecovers() = runTest {
        var requests = 0
        HttpClient(MockEngine {
            if (++requests == 1) throw IOException("connection reset")
            respond("[]")
        }).use { client ->
            assertEquals("[]", client.getConformanceState("https://suite.example/api/log/test", "Read test log").bodyAsText())
            assertEquals(2, requests)
        }
    }

    @Test
    fun persistentHttpFailureStopsAfterThreeAttemptsAndExcludesResponseBody() = runTest {
        var requests = 0
        HttpClient(MockEngine {
            requests++
            respond("private test credentials", HttpStatusCode.BadGateway)
        }).use { client ->
            val error = assertFailsWith<ConformanceStateRequestException> {
                client.getConformanceState("https://suite.example/api/info/test", "Read test status")
            }
            assertEquals(3, requests)
            assertEquals("Read test status: HTTP 502", error.safeConformanceFailure())
        }
    }

    @Test
    fun persistentTransportFailureStopsAfterThreeAttemptsAndExcludesCauseMessage() = runTest {
        var requests = 0
        HttpClient(MockEngine {
            requests++
            throw IOException("private test credentials")
        }).use { client ->
            val error = assertFailsWith<ConformanceStateRequestException> {
                client.getConformanceState("https://suite.example/api/runner/test", "Read test runner state")
            }
            assertEquals(3, requests)
            assertEquals("Read test runner state: transport failure", error.safeConformanceFailure())
        }
    }

    @Test
    fun permanentHttpErrorsAreNotRetried() = runTest {
        for (status in listOf(400, 401, 403, 404, 500)) {
            var requests = 0
            HttpClient(MockEngine {
                requests++
                respond("private test credentials", HttpStatusCode.fromValue(status))
            }).use { client ->
                val error = assertFailsWith<ConformanceStateRequestException> {
                    client.getConformanceState("https://suite.example/api/info/test", "Read test status")
                }
                assertEquals(1, requests)
                assertEquals("Read test status: HTTP $status", error.safeConformanceFailure())
            }
        }
    }

    @Test
    fun cancellationIsNotRetriedOrWrapped() = runTest {
        var requests = 0
        HttpClient(MockEngine {
            requests++
            throw CancellationException("stop")
        }).use { client ->
            assertFailsWith<CancellationException> {
                client.getConformanceState("https://suite.example/api/info/test", "Read test status")
            }
            assertEquals(1, requests)
        }
    }

    @Test
    fun ordinaryGetRequestsSuchAsOfferDeliveryAreNotRetried() = runTest {
        var requests = 0
        HttpClient(MockEngine {
            requests++
            respond("upstream unavailable", HttpStatusCode.BadGateway)
        }).use { client ->
            assertEquals(HttpStatusCode.BadGateway, client.get("https://suite.example/test/test/credential_offer").status)
            assertEquals(1, requests)
        }
    }

    @Test
    fun failureSummaryOmitsMessagesAndHttpBodies() = runTest {
        assertEquals("IllegalStateException", IllegalStateException("private test credentials").safeConformanceFailure())
        HttpClient(MockEngine { respond("private test credentials", HttpStatusCode.BadRequest) }).use { client ->
            val response = client.get("https://issuer.example/credential-offers?secret=private")
            val error = ClientRequestException(response, "private test credentials")
            assertEquals("ClientRequestException: HTTP 400", error.safeConformanceFailure())
        }
    }
}
