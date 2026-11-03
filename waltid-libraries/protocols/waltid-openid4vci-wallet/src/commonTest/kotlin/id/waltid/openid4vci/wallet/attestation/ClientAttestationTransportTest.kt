package id.waltid.openid4vci.wallet.attestation

import id.waltid.openid4vci.wallet.authorization.RetryablePushedAuthorizationRequestException
import id.waltid.openid4vci.wallet.authorization.PushedAuthorizationRequestExecutor
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import id.waltid.openid4vci.wallet.token.TokenRequestBuilder
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ClientAttestationTransportTest {
    @Test fun parChallengeWithExpectSuccessEnabled() = runTest { strictClient(true) }
    @Test fun tokenChallengeWithExpectSuccessEnabled() = runTest { strictClient(false) }

    private suspend fun strictClient(par: Boolean) {
        val context = ClientAttestationChallengeContext()
        var requests = 0
        val client = HttpClient(MockEngine) {
            expectSuccess = true
            engine { addHandler {
                requests++
                if (requests == 1) respond("""{"error":"use_attestation_challenge"}""", HttpStatusCode.BadRequest,
                    headersOf("OAuth-Client-Attestation-Challenge", "fresh"))
                else respond(if (par) """{"request_uri":"urn:par:1","expires_in":60}""" else
                    """{"access_token":"token","token_type":"Bearer"}""", if (par) HttpStatusCode.Created else HttpStatusCode.OK)
            } }
        }
        try {
            val result = runCatching {
                if (par) PushedAuthorizationRequestExecutor.execute(client, "https://as.example/par", emptyMap(),
                    onResponseHeaders = context::observe,
                    attestationHeadersFactory = { ClientAttestationHeaders("att", "proof-${context.challenge}") })
                else TokenRequestBuilder(ClientConfiguration("wallet", listOf("openid://callback")), client)
                    .exchangePreAuthorizedCode("https://as.example/token", "code", dpopProofFactory = null,
                        onResponseHeaders = context::observe,
                        attestationHeadersFactory = { ClientAttestationHeaders("att", "proof-${context.challenge}") })
            }
            assertEquals(2, requests, "Recovery was bypassed: ${result.exceptionOrNull()}")
            assertTrue(result.isSuccess)
            assertEquals("fresh", context.challenge)
        } finally { client.close() }
    }

    @Test fun automaticHttpRetryMustNotReplayPoPAndLoseChallenge() = runTest {
        val context = ClientAttestationChallengeContext("initial")
        val proofs = mutableListOf<String?>()
        var factoryCalls = 0
        val client = HttpClient(MockEngine) {
            install(HttpRequestRetry) { retryOnServerErrors(maxRetries = 1); delayMillis { 0L } }
            engine { addHandler { request ->
                proofs += request.headers["OAuth-Client-Attestation-PoP"]
                if (proofs.size == 1) respond("unavailable", HttpStatusCode.ServiceUnavailable,
                    headersOf("OAuth-Client-Attestation-Challenge", "fresh-from-503"))
                else respond("""{"request_uri":"urn:par:1","expires_in":60}""", HttpStatusCode.Created)
            } }
        }
        try {
            assertFailsWith<RetryablePushedAuthorizationRequestException> {
                PushedAuthorizationRequestExecutor.execute(client, "https://as.example/par", emptyMap(),
                    onResponseHeaders = context::observe,
                    attestationHeadersFactory = { factoryCalls++; ClientAttestationHeaders("att", "proof-$factoryCalls-${context.challenge}") })
            }
            assertEquals("fresh-from-503", context.challenge)
            assertEquals(1, proofs.size, "Generic HTTP retries should not replay this authenticated POST: $proofs")
        } finally { client.close() }
    }

    @Test fun tokenTransportErrorsAreNotRetriedByHttpPlugin() = runTest {
        for (networkFailure in listOf(false, true)) {
            var requests = 0
            val client = HttpClient(MockEngine) {
                install(HttpRequestRetry) { retryOnExceptionOrServerErrors(maxRetries = 3); delayMillis { 0L } }
                engine { addHandler {
                    requests++
                    if (networkFailure) throw IllegalStateException("transport failure")
                    respond("unavailable", HttpStatusCode.ServiceUnavailable)
                } }
            }
            try {
                val error = assertFailsWith<id.waltid.openid4vci.wallet.token.TokenRequestException> {
                    TokenRequestBuilder(ClientConfiguration("wallet", listOf("openid://callback")), client)
                        .exchangePreAuthorizedCode("https://as.example/token", "code", dpopProofFactory = null,
                            onResponseHeaders = {}, attestationHeadersFactory = { ClientAttestationHeaders("att", "pop") })
                }
                assertEquals(if (networkFailure) 0 else 503, error.statusCode)
                assertEquals(1, requests)
            } finally { client.close() }
        }
    }

    @Test fun challengeEndpointRequiresAttestationEvenWithDpop() = runTest {
        val client = HttpClient(MockEngine) { engine { addHandler {
            respond("{}", HttpStatusCode.OK, headersOf("DPoP-Nonce", "dpop-only"))
        } } }
        try {
            val error = assertFailsWith<WalletAttestationChallengeRequestException> {
                ClientAttestationChallengeContext().prepare(client, "https://as.example/challenge")
            }
            assertEquals(WalletAttestationChallengeRequestError.INVALID_RESPONSE, error.error)
        } finally { client.close() }
    }

    @Test fun challengeEndpointRejectsMissingOrMalformedAttestation() = runTest {
        for ((body, nonce) in listOf("{}" to "", "{}" to " ",
            """{"attestation_challenge":null}""" to "nonce",
            """{"attestation_challenge":5}""" to "nonce",
            """{"attestation_challenge":" "}""" to "nonce")) {
            val client = HttpClient(MockEngine) { engine { addHandler {
                respond(body, HttpStatusCode.OK, headersOf("DPoP-Nonce", nonce))
            } } }
            try {
                assertFailsWith<WalletAttestationChallengeRequestException> {
                    ClientAttestationChallengeContext().prepare(client, "https://as.example/challenge")
                }
            } finally { client.close() }
        }
    }

}
