@file:Suppress("DEPRECATION")

package id.walt.openid4vci

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.clientauth.*
import id.walt.openid4vci.clientauth.attestation.*
import id.walt.openid4vci.clientauth.attestation.challenge.JwtAttestationChallengeService
import id.walt.openid4vci.clientauth.attestation.verifier.*
import id.walt.openid4vci.core.*
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssueRequest
import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.tokens.jwt.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class AttestationChallengeFlowTest {
    @Test
    fun `same challenge authenticates PAR code redemption and refresh with retry recovery`() = runTest {
        val fixture = Fixture().apply { initialize() }
        val provider = fixture.provider
        val parameters = mapOf(
            "response_type" to listOf("code"), "client_id" to listOf("wallet"),
            "redirect_uri" to listOf("https://wallet.example/callback"), "scope" to listOf("openid"),
        )
        val parFailure = assertIs<AuthorizationRequestResult.Failure>(provider.createPushedAuthorizationRequest(parameters, fixture.headers()))
        fixture.assertChallengeError(parFailure.error)
        val challenge = fixture.challenge()
        val par = assertIs<AuthorizationRequestResult.Success>(provider.createPushedAuthorizationRequest(parameters, fixture.headers(challenge))).request
        val pushed = assertIs<PushedAuthorizationResponseResult.Success>(provider.createPushedAuthorizationResponse(par)).response
        val authorization = assertIs<AuthorizationRequestResult.Success>(provider.createAuthorizationRequest(mapOf(
            "client_id" to listOf("wallet"), "request_uri" to listOf(pushed.requestUri),
        ))).request.withIssuer(fixture.issuer)
        val code = assertIs<AuthorizationResponseResult.Success>(provider.createAuthorizationResponse(authorization, DefaultSession(subject = "holder"))).response.code
        val tokenParameters = mapOf(
            "grant_type" to listOf(GrantType.AuthorizationCode.value), "code" to listOf(code),
            "client_id" to listOf("wallet"), "redirect_uri" to parameters.getValue("redirect_uri"),
        )
        fixture.assertChallengeError(assertIs<AccessTokenRequestResult.Failure>(provider.createAccessTokenRequest(tokenParameters, fixture.headers())).error)
        // The PAR challenge is still usable, even after an error supplied a different one.
        val token = fixture.redeem(tokenParameters, challenge)
        var refreshParameters = mapOf(
            "grant_type" to listOf(GrantType.RefreshToken.value),
            "refresh_token" to listOf(assertNotNull(token.refreshToken)), "client_id" to listOf("wallet"),
        )
        val refreshed = fixture.redeem(refreshParameters, challenge)
        refreshParameters = refreshParameters + ("refresh_token" to listOf(assertNotNull(refreshed.refreshToken)))
        fixture.time += 301.seconds
        val expired = assertIs<AccessTokenRequestResult.Failure>(provider.createAccessTokenRequest(refreshParameters, fixture.headers(challenge)))
        val replacement = fixture.assertChallengeError(expired.error)
        assertNotNull(fixture.redeem(refreshParameters, replacement).refreshToken)
    }

    @Test
    fun `preauthorized grant survives missing expired and rotated challenges`() = runTest {
        val fixture = Fixture().apply { initialize() }
        val code = fixture.config.preAuthorizedCodeIssuer.issue(PreAuthorizedCodeIssueRequest(
            clientId = "wallet", session = DefaultSession(subject = "holder"),
        )).code
        val parameters = mapOf("grant_type" to listOf(GrantType.PreAuthorizedCode.value), "pre-authorized_code" to listOf(code), "client_id" to listOf("wallet"))
        var challenge = fixture.assertChallengeError(assertIs<AccessTokenRequestResult.Failure>(fixture.provider.createAccessTokenRequest(parameters, fixture.headers())).error)
        fixture.time += 301.seconds
        challenge = fixture.assertChallengeError(assertIs<AccessTokenRequestResult.Failure>(fixture.provider.createAccessTokenRequest(parameters, fixture.headers(challenge))).error)
        fixture.tokenKey = JWKKey.generate(KeyType.secp256r1)
        challenge = fixture.assertChallengeError(assertIs<AccessTokenRequestResult.Failure>(fixture.provider.createAccessTokenRequest(parameters, fixture.headers(challenge))).error)
        assertTrue(fixture.redeem(parameters, challenge).accessToken.isNotBlank())
    }

    @Test
    fun `challenges are issuer bound even with a shared signing key`() = runTest {
        val fixture = Fixture().apply { initialize() }
        val first = fixture.challenge()
        fixture.issuer = "https://second.example"
        val code = fixture.config.preAuthorizedCodeIssuer.issue(PreAuthorizedCodeIssueRequest(
            clientId = "wallet", session = DefaultSession(subject = "holder"),
        )).code
        val parameters = mapOf("grant_type" to listOf(GrantType.PreAuthorizedCode.value), "pre-authorized_code" to listOf(code), "client_id" to listOf("wallet"))
        fixture.assertChallengeError(assertIs<AccessTokenRequestResult.Failure>(
            fixture.provider.createAccessTokenRequest(parameters, fixture.headers(first)),
        ).error)
        val second = fixture.challenge()
        assertTrue(fixture.redeem(parameters, second).accessToken.isNotBlank())
    }

    private class Fixture {
        var issuer = "https://issuer.example"
        var time = Clock.System.now()
        lateinit var tokenKey: JWKKey
        lateinit var instanceKey: JWKKey
        lateinit var attestation: String
        val config = createTestConfig()
        lateinit var provider: OAuth2Provider
        lateinit var challenges: JwtAttestationChallengeService

        suspend fun initialize() {
            tokenKey = JWKKey.generate(KeyType.secp256r1)
            instanceKey = JWKKey.generate(KeyType.secp256r1)
            val attester = JWKKey.generate(KeyType.secp256r1)
            attestation = attester.signJws(buildJsonObject {
                put("sub", "wallet"); put("exp", time.epochSeconds + 3600)
                put("cnf", buildJsonObject { put("jwk", instanceKey.getPublicKey().exportJWKObject()) })
            }.toString().encodeToByteArray(), mapOf("typ" to JsonPrimitive(ClientAttestationJwtTypes.CLIENT_ATTESTATION)))
            challenges = JwtAttestationChallengeService(JwtSigningKeyResolver { tokenKey }, JwtVerificationKeyResolver { tokenKey.getPublicKey() }, now = { time })
            val method = AttestationBasedClientAuthenticationMethod(
                attestationVerifier = KeyBasedClientAttestationVerifier { _, _ -> listOf(attester.getPublicKey()) },
                challengeService = challenges, clock = { time },
            )
            val serviceConfig = ClientAuthenticationServiceConfig(listOf(method), ClientAuthenticationEndpoint.entries.associateWith { setOf(method.name) })
            provider = buildOAuth2Provider(config.copy(
                clientAuthenticationServiceResolver = ClientAuthenticationServiceResolver { _, _, _ ->
                    ClientAuthenticationServiceResolution(serviceConfig, ClientAuthenticationContext(issuer))
                },
                pushedAuthorizationConfig = PushedAuthorizationConfig(InMemoryPARRepository()),
            ))
        }

        suspend fun challenge(): String = challenges.issue(issuer)

        suspend fun headers(challenge: String? = null): Map<String, List<String>> {
            val pop = instanceKey.signJws(buildJsonObject {
                put("aud", issuer); put("iat", time.epochSeconds); put("jti", Uuid.random().toString())
                challenge?.let { put("challenge", it) }
            }.toString().encodeToByteArray(), mapOf("typ" to JsonPrimitive(ClientAttestationJwtTypes.CLIENT_ATTESTATION_POP)))
            return mapOf(ClientAttestationHeaders.CLIENT_ATTESTATION to listOf(attestation), ClientAttestationHeaders.CLIENT_ATTESTATION_POP to listOf(pop))
        }

        fun assertChallengeError(error: id.walt.openid4vci.errors.OAuthError): String {
            assertEquals("use_attestation_challenge", error.error)
            return assertNotNull(error.responseHeaders[ClientAttestationHeaders.CLIENT_ATTESTATION_CHALLENGE])
        }

        suspend fun redeem(parameters: Map<String, List<String>>, challenge: String): id.walt.openid4vci.responses.token.AccessTokenResponse {
            val request = assertIs<AccessTokenRequestResult.Success>(provider.createAccessTokenRequest(parameters, headers(challenge))).request.withIssuer(issuer)
            val response = assertIs<AccessTokenResponseResult.Success>(provider.createAccessTokenResponse(request)).response
            assertNull(provider.writeAccessTokenResponse(request, response).headers[ClientAttestationHeaders.CLIENT_ATTESTATION_CHALLENGE])
            return response
        }
    }
}
