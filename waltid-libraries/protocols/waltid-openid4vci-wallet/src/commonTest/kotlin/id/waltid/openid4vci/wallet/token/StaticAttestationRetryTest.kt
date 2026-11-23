package id.waltid.openid4vci.wallet.token

import id.waltid.openid4vci.wallet.attestation.ClientAttestationHeaders
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StaticAttestationRetryTest {
    private enum class Grant { PRE_AUTHORIZED, AUTHORIZATION_CODE, REFRESH }
    private val staticHeaders = ClientAttestationHeaders("manual-wia", "manual-pop")
    private val endpoint = "https://issuer.example/token"

    @Test
    fun rawStaticAttestationDoesNotRetryEitherChallengeForAnyGrant() = runTest {
        assertStaticAttestationDoesNotRetry(raw = true)
    }

    @Test
    fun typedStaticAttestationDoesNotRetryEitherChallengeForAnyGrant() = runTest {
        assertStaticAttestationDoesNotRetry(raw = false)
    }

    private suspend fun assertStaticAttestationDoesNotRetry(raw: Boolean) {
        for (grant in Grant.entries) for (error in listOf("use_attestation_challenge", "use_dpop_nonce")) {
            var requests = 0
            val client = client { headers ->
                requests++
                assertEquals(staticHeaders.attestationJwt, headers[ClientAttestationHeaders.HEADER_ATTESTATION])
                assertEquals(staticHeaders.popJwt, headers[ClientAttestationHeaders.HEADER_ATTESTATION_POP])
                error
            }
            client.use {
                val failure = assertFailsWith<TokenRequestException> {
                    exchange(
                        TokenRequestBuilder(ClientConfiguration("wallet", listOf("openid://")), it),
                        grant,
                        additionalHeaders = if (raw) mapOf(
                            ClientAttestationHeaders.HEADER_ATTESTATION.lowercase() to staticHeaders.attestationJwt,
                            ClientAttestationHeaders.HEADER_ATTESTATION_POP.uppercase() to staticHeaders.popJwt,
                        ) else emptyMap(),
                        attestationHeaders = staticHeaders.takeUnless { raw },
                        dpopFactory = { _, _ -> "dpop-proof" },
                    )
                }
                assertEquals(error, failure.oauthError)
                assertEquals(1, requests, "$grant / $error / raw=$raw")
            }
        }
    }

    @Test
    fun dpopWithoutStaticAttestationStillRecoversForEveryGrant() = runTest {
        for (grant in Grant.entries) {
            var requests = 0
            val nonces = mutableListOf<String?>()
            client { if (++requests == 1) "use_dpop_nonce" else null }.use {
                val result = exchange(
                    TokenRequestBuilder(ClientConfiguration("wallet", listOf("openid://")), it), grant,
                    dpopFactory = { _, nonce -> nonces += nonce; "dpop-${nonces.size}" },
                )
                assertEquals("access", result.access_token)
                assertEquals(listOf(null, "nonce"), nonces)
                assertEquals(2, requests)
            }
        }
    }

    @Test
    fun attestationFactoryStillCreatesFreshProofsForBothChallengesAndEveryGrant() = runTest {
        for (grant in Grant.entries) for (error in listOf("use_attestation_challenge", "use_dpop_nonce")) {
            var requests = 0
            var proofs = 0
            val observed = mutableListOf<String?>()
            client { headers ->
                observed += headers[ClientAttestationHeaders.HEADER_ATTESTATION_POP]
                if (++requests == 1) error else null
            }.use {
                val result = exchange(
                    TokenRequestBuilder(ClientConfiguration("wallet", listOf("openid://")), it), grant,
                    dpopFactory = { _, _ -> "dpop-$requests" },
                    attestationFactory = { ClientAttestationHeaders("automatic-wia", "fresh-pop-${++proofs}") },
                )
                assertEquals("access", result.access_token)
                assertEquals<List<String?>>(listOf("fresh-pop-1", "fresh-pop-2"), observed, "$grant / $error")
                assertEquals(2, requests)
            }
        }
    }

    private fun client(errorForRequest: (Headers) -> String?) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                val error = errorForRequest(request.headers)
                respond(
                    content = error?.let { """{"error":"$it"}""" }
                        ?: """{"access_token":"access","token_type":"DPoP"}""",
                    status = if (error == null) HttpStatusCode.OK else HttpStatusCode.BadRequest,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        "DPoP-Nonce" to listOf("nonce"),
                        "OAuth-Client-Attestation-Challenge" to listOf("challenge"),
                    ),
                )
            }
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private suspend fun exchange(
        builder: TokenRequestBuilder,
        grant: Grant,
        additionalHeaders: Map<String, String> = emptyMap(),
        attestationHeaders: ClientAttestationHeaders? = null,
        dpopFactory: DPoPProofFactory,
        attestationFactory: ClientAttestationHeadersFactory? = null,
    ): TokenRequestBuilder.TokenResponse = when (grant) {
        Grant.PRE_AUTHORIZED -> builder.exchangePreAuthorizedCode(
            tokenEndpoint = endpoint, preAuthorizedCode = "pre-code",
            additionalHeaders = additionalHeaders, attestationHeaders = attestationHeaders,
            dpopProofFactory = dpopFactory, onResponseHeaders = {}, attestationHeadersFactory = attestationFactory,
        )
        Grant.AUTHORIZATION_CODE -> builder.exchangeAuthorizationCode(
            tokenEndpoint = endpoint, code = "code",
            additionalHeaders = additionalHeaders, attestationHeaders = attestationHeaders,
            dpopProofFactory = dpopFactory, onResponseHeaders = {}, attestationHeadersFactory = attestationFactory,
        )
        Grant.REFRESH -> builder.refreshAccessToken(
            tokenEndpoint = endpoint, refreshToken = "refresh",
            additionalHeaders = additionalHeaders, attestationHeaders = attestationHeaders,
            dpopProofFactory = dpopFactory, onResponseHeaders = {}, attestationHeadersFactory = attestationFactory,
        )
    }
}
