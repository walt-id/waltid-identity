package id.walt.openid4vci

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.ShaUtils
import id.walt.openid4vci.clientauth.AuthenticatedClient
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.deferred.DeferredCredentialRequestResult
import id.walt.openid4vci.dpop.DefaultDPoPProofVerifier
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssueRequest
import id.walt.openid4vci.repository.refresh.InMemoryRefreshTokenRepository
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.responses.token.TokenCredentialAuthorization
import id.walt.openid4vci.responses.token.TokenResponseOptions
import id.walt.openid4vci.tokens.access.*
import id.walt.openid4vci.tokens.jwt.access.*
import id.walt.openid4vci.tokens.jwt.refresh.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class DPoPRefreshFlowTest {
    @Test fun bothGrantsRetainBindingAcrossRotationAndRejectMissingWrongAndReplayedProofs() = runTest {
        for (grant in listOf(GrantType.AuthorizationCode, GrantType.PreAuthorizedCode)) verify(grant, bound = true)
    }

    @Test fun bothGrantsKeepBearerAndConfidentialClientRefreshSemantics() = runTest {
        for (grant in listOf(GrantType.AuthorizationCode, GrantType.PreAuthorizedCode)) {
            verify(grant, bound = false)
            verify(grant, bound = true, confidential = true)
        }
    }

    @Test fun bothGrantsBindPublicClientRefreshTokensWhenOptingIntoDPoPOnRefresh() = runTest {
        for (grant in listOf(GrantType.AuthorizationCode, GrantType.PreAuthorizedCode)) {
            verify(grant, bound = false, upgradeOnRefresh = true)
            verify(grant, bound = false, confidential = true, upgradeOnRefresh = true)
        }
    }

    private suspend fun verify(grant: GrantType, bound: Boolean, confidential: Boolean = false, upgradeOnRefresh: Boolean = false) {
        val issuer = "https://issuer.example"
        val endpoint = "$issuer/token"
        val tokenKey = JWKKey.generate(KeyType.secp256r1)
        val walletKey = JWKKey.generate(KeyType.secp256r1)
        val wrongKey = JWKKey.generate(KeyType.secp256r1)
        val repository = InMemoryRefreshTokenRepository()
        val refreshIssuer = JwtRefreshTokenIssuer { tokenKey }
        val verifier = JwtAccessTokenVerifier { tokenKey }
        val config = createTestConfig(
            accessTokenIssuer = JwtAccessTokenIssuer(resolver = { tokenKey }), accessTokenVerifier = verifier,
            refreshTokenIssuer = refreshIssuer, refreshTokenVerifier = JwtRefreshTokenVerifier { tokenKey },
            refreshTokenRepository = repository, dpopProofVerifier = DefaultDPoPProofVerifier(),
        )
        val provider = buildOAuth2Provider(config)
        var proofCounter = 0
        suspend fun proof(key: JWKKey = walletKey, target: String = endpoint, accessToken: String? = null) = key.signJws(
            buildJsonObject {
                put("jti", "proof-${++proofCounter}"); put("htm", "POST"); put("htu", target)
                put("iat", Clock.System.now().epochSeconds)
                accessToken?.let { put("ath", ShaUtils.calculateSha256Base64Url(it)) }
            }.toString().encodeToByteArray(), headers = mapOf("typ" to JsonPrimitive("dpop+jwt"), "jwk" to key.getPublicKey().exportJWKObject()),
        )
        var deadline = Clock.System.now() + 7.days
        val options = TokenResponseOptions(credentialAuthorizationResolver = { _, _ -> TokenCredentialAuthorization(emptyList(), false, deadline) })
        val initialForm = if (grant == GrantType.AuthorizationCode) {
            val request = assertIs<AuthorizationRequestResult.Success>(provider.createAuthorizationRequest(mapOf(
                "response_type" to listOf("code"), "client_id" to listOf("wallet"),
                "redirect_uri" to listOf("https://wallet.example/callback"), "scope" to listOf("credential"),
            ))).request.withIssuer(issuer)
            val code = assertIs<AuthorizationResponseResult.Success>(provider.createAuthorizationResponse(request, DefaultSession(subject = "offer"))).response.code
            mapOf("grant_type" to listOf(grant.value), "client_id" to listOf("wallet"), "code" to listOf(code), "redirect_uri" to listOf("https://wallet.example/callback"))
        } else {
            val code = config.preAuthorizedCodeIssuer.issue(PreAuthorizedCodeIssueRequest(
                scopes = setOf("credential"), session = DefaultSession(subject = "offer"),
            )).code
            mapOf("grant_type" to listOf(grant.value), "pre-authorized_code" to listOf(code))
        }
        fun AccessTokenRequest.authenticate() =
            if (confidential) withAuthenticatedClient(AuthenticatedClient(client.id, "private_key_jwt")) else this
        val initialRequest = assertIs<AccessTokenRequestResult.Success>(provider.createAccessTokenRequest(
            initialForm, headers = if (bound) mapOf("DPoP" to listOf(proof())) else emptyMap(), tokenEndpointUri = endpoint,
        )).request.withIssuer(issuer).authenticate()
        var response = assertIs<AccessTokenResponseResult.Success>(provider.createAccessTokenResponse(initialRequest, options)).response
        val walletThumbprint = walletKey.getPublicKey().getThumbprint()
        var expectedBinding = if (bound && !confidential) walletThumbprint else null
        val originalRecord = assertNotNull(repository.get(refreshIssuer.signature(assertNotNull(response.refreshToken))))
        assertEquals(expectedBinding, originalRecord.dpopJwkThumbprint)
        suspend fun request(refreshToken: String, dpop: String?) = provider.createAccessTokenRequest(
            mapOf("grant_type" to listOf("refresh_token"), "refresh_token" to listOf(refreshToken)) +
                if (grant == GrantType.AuthorizationCode) mapOf("client_id" to listOf("wallet")) else emptyMap(),
            headers = dpop?.let { mapOf("DPoP" to listOf(it)) }.orEmpty(), tokenEndpointUri = endpoint,
        )
        repeat(3) {
            val previousToken = assertNotNull(response.refreshToken)
            if (expectedBinding != null) {
                for ((dpop, error) in listOf(null to "invalid_dpop_proof", proof(wrongKey) to "invalid_grant")) {
                    val bad = assertIs<AccessTokenRequestResult.Success>(request(previousToken, dpop)).request.withIssuer(issuer).authenticate()
                    assertEquals(error, assertIs<AccessTokenResponseResult.Failure>(provider.createAccessTokenResponse(bad, options)).error.error)
                    assertTrue(assertNotNull(repository.get(refreshIssuer.signature(previousToken))).active)
                }
            }
            val dpop = if (expectedBinding != null || (upgradeOnRefresh && it == 0)) proof() else null
            val parsed = assertIs<AccessTokenRequestResult.Success>(request(previousToken, dpop)).request.withIssuer(issuer).authenticate()
            response = assertIs<AccessTokenResponseResult.Success>(provider.createAccessTokenResponse(parsed, options)).response
            val expectedAccessBinding = if (dpop != null) walletThumbprint else null
            if (dpop != null && !confidential) expectedBinding = walletThumbprint
            assertEquals(if (expectedAccessBinding == null) "bearer" else "DPoP", response.tokenType)
            val claims = verifier.verify(response.accessToken, issuer)
            assertEquals(expectedAccessBinding, (claims["cnf"] as? JsonObject)?.get("jkt")?.jsonPrimitive?.content)
            assertTrue(kotlin.math.abs(requireNotNull(response.expiresIn) - (claims.getValue("exp").jsonPrimitive.long - Clock.System.now().epochSeconds)) <= 1)
            val rotated = assertNotNull(repository.get(refreshIssuer.signature(assertNotNull(response.refreshToken))))
            assertEquals(originalRecord.expiresAt, rotated.expiresAt)
            assertEquals(expectedBinding, rotated.dpopJwkThumbprint)
            assertFalse(assertNotNull(repository.get(refreshIssuer.signature(previousToken))).active)
            if (dpop != null) {
                assertEquals("invalid_dpop_proof", assertIs<AccessTokenRequestResult.Failure>(request(assertNotNull(response.refreshToken), dpop)).error.error)
                val replayed = assertIs<AccessTokenRequestResult.Success>(request(previousToken, proof())).request.withIssuer(issuer).authenticate()
                assertEquals("invalid_grant", assertIs<AccessTokenResponseResult.Failure>(provider.createAccessTokenResponse(replayed, options)).error.error)
            }
            val pollEndpoint = "$issuer/deferred_credential"
            assertIs<DeferredCredentialRequestResult.Success>(provider.createDeferredCredentialRequest(
                mapOf("transaction_id" to listOf("transaction")), CredentialAccessTokenContext(
                    authorization = AccessTokenAuthorization(if (expectedAccessBinding == null) AccessTokenAuthorizationScheme.BEARER else AccessTokenAuthorizationScheme.DPOP, response.accessToken),
                    expectedIssuer = issuer, credentialEndpointUri = pollEndpoint,
                    dpopProofHeaderValues = if (expectedAccessBinding != null) listOf(proof(target = pollEndpoint, accessToken = response.accessToken)) else emptyList(),
                ),
            ))
        }
        deadline = Clock.System.now() - 1.seconds
        val finalToken = assertNotNull(response.refreshToken)
        val expired = assertIs<AccessTokenRequestResult.Success>(request(finalToken, if (expectedBinding != null) proof() else null)).request.withIssuer(issuer).authenticate()
        assertEquals("invalid_grant", assertIs<AccessTokenResponseResult.Failure>(provider.createAccessTokenResponse(expired, options)).error.error)
        assertTrue(assertNotNull(repository.get(refreshIssuer.signature(finalToken))).active)
    }
}
