package id.waltid.openid4vci.wallet.attestation

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class WalletAttestationChallengeRequestBuilderTest {
    @Test
    fun requestsJsonChallengeWithPostAndAcceptHeader() = runTest {
        val builder = WalletAttestationChallengeRequestBuilder(client { request ->
            assertEquals("https://issuer.example/challenge", request.url.toString())
            assertEquals("POST", request.method.value)
            assertEquals(ContentType.Application.Json.toString(), request.headers[HttpHeaders.Accept])
            respond(
                """{"attestation_challenge":"challenge-1","extension":"ignored"}""",
                HttpStatusCode.OK,
            )
        })

        val response = builder.requestChallenge("https://issuer.example/challenge")

        assertEquals("challenge-1", response.attestationChallenge)
        assertFalse(response.toString().contains("challenge-1"))
    }

    @Test
    fun rejectsNonSuccessAndMalformedOrBlankResponses() = runTest {
        val rejected = assertFailsWith<WalletAttestationChallengeRequestException> {
            WalletAttestationChallengeRequestBuilder(client {
                respond("""{"error":"private-detail"}""", HttpStatusCode.BadRequest)
            }).requestChallenge(ENDPOINT)
        }
        assertEquals(WalletAttestationChallengeRequestError.AUTHORIZATION_SERVER_RESPONSE, rejected.error)
        assertEquals(400, rejected.statusCode)
        assertFalse(rejected.message.orEmpty().contains("private-detail"))

        for (body in listOf("{}", """{"attestation_challenge":""}""", "not-json")) {
            val error = assertFailsWith<WalletAttestationChallengeRequestException> {
                WalletAttestationChallengeRequestBuilder(client { respond(body, HttpStatusCode.OK) })
                    .requestChallenge(ENDPOINT)
            }
            assertEquals(WalletAttestationChallengeRequestError.INVALID_RESPONSE, error.error)
        }
    }

    @Test
    fun rejectsInvalidEndpointBeforeSending() = runTest {
        var calls = 0
        val error = assertFailsWith<WalletAttestationChallengeRequestException> {
            WalletAttestationChallengeRequestBuilder(client {
                calls++
                respond("{}", HttpStatusCode.OK)
            }).requestChallenge("")
        }
        assertEquals(WalletAttestationChallengeRequestError.INVALID_ENDPOINT, error.error)
        assertEquals(0, calls)
    }

    private fun client(
        handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ): HttpClient = HttpClient(MockEngine) {
        engine { addHandler(handler) }
    }

    private companion object {
        const val ENDPOINT = "https://issuer.example/challenge"
    }
}
