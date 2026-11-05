package id.walt.openid4vci

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.dpop.DefaultDPoPProofVerifier
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssueRequest
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.responses.token.TokenResponseOptions
import id.walt.openid4vci.responses.token.TokenCredentialAuthorization
import id.walt.openid4vci.tokens.access.AccessTokenIssuer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import id.walt.openid4vci.repository.refresh.*

class DeferredRefreshRegressionTest {
    private suspend fun flow(includeRefreshProof: Boolean, nearDeadline: Boolean = false): Pair<AccessTokenResponseResult, Map<String, Any?>> {
        val issuer = "https://issuer.example"
        val endpoint = "$issuer/token"
        val wallet = JWKKey.generate(KeyType.secp256r1)
        var claims: Map<String, Any?> = emptyMap()
        var sequence = 0
        val signer = object : AccessTokenIssuer {
            override suspend fun issue(value: Map<String, Any?>): String { claims = value; return "access-${++sequence}" }
        }
        val backing = InMemoryRefreshTokenRepository()
        val repository = object : RefreshTokenRepository by backing {
            override suspend fun get(tokenSignature: String): RefreshTokenRecord? {
                val record = backing.get(tokenSignature) ?: return null
                if (!nearDeadline) return record
                return object : RefreshTokenRecord by record {
                    override val session = record.session.withExpiresAt(TokenType.ACCESS_TOKEN, Clock.System.now() - 1.seconds)
                }
            }
        }
        val config = createTestConfig(accessTokenIssuer = signer, dpopProofVerifier = DefaultDPoPProofVerifier(), refreshTokenRepository = repository)
        val provider = buildOAuth2Provider(config)
        val code = config.preAuthorizedCodeIssuer.issue(PreAuthorizedCodeIssueRequest(
            scopes = setOf("credential"), audience = setOf(issuer), session = DefaultSession(subject = "offer"),
        )).code
        suspend fun proof(id: String) = wallet.signJws(buildJsonObject {
            put("jti", id); put("htm", "POST"); put("htu", endpoint); put("iat", Clock.System.now().epochSeconds)
        }.toString().encodeToByteArray(), headers = mapOf(
            "typ" to JsonPrimitive("dpop+jwt"), "jwk" to wallet.getPublicKey().exportJWKObject(),
        ))
        val deadline = Clock.System.now() + if (nearDeadline) 30.seconds else 7.days
        val options = TokenResponseOptions(credentialAuthorizationResolver = { _, _ ->
            TokenCredentialAuthorization(emptyList(), false, deadline)
        })
        val initialRequest = assertIs<AccessTokenRequestResult.Success>(provider.createAccessTokenRequest(
            mapOf("grant_type" to listOf(GrantType.PreAuthorizedCode.value), "pre-authorized_code" to listOf(code)),
            headers = mapOf("DPoP" to listOf(proof("initial"))), tokenEndpointUri = endpoint,
        )).request.withIssuer(issuer)
        val initial = assertIs<AccessTokenResponseResult.Success>(provider.createAccessTokenResponse(initialRequest, options)).response
        assertEquals("DPoP", initial.tokenType)
        assertNotNull(claims["cnf"])
        val refreshRequest = assertIs<AccessTokenRequestResult.Success>(provider.createAccessTokenRequest(
            mapOf("grant_type" to listOf(GrantType.RefreshToken.value), "refresh_token" to listOf(assertNotNull(initial.refreshToken))),
            headers = if (includeRefreshProof) mapOf("DPoP" to listOf(proof("refresh"))) else emptyMap(),
            tokenEndpointUri = endpoint,
        )).request.withIssuer(issuer)
        return provider.createAccessTokenResponse(refreshRequest, options) to claims
    }

    @Test fun deferredRefreshRetainsDpopTokenAndBinding() = runTest {
        val (result, claims) = flow(true)
        val response = assertIs<AccessTokenResponseResult.Success>(result).response
        assertEquals("DPoP", response.tokenType, "DPoP must survive the real refresh handler")
        assertNotNull(claims["cnf"], "Refreshed access token must retain its bound key")
        assertEquals(1800L, (claims.getValue("exp") as Number).toLong() - (claims.getValue("iat") as Number).toLong())
        assertTrue(requireNotNull(response.expiresIn) in 1798L..1800L)
    }

    @Test fun refreshExpiryReflectsRemainingDeferredDeadline() = runTest {
        val (result, claims) = flow(true, nearDeadline = true)
        val response = assertIs<AccessTokenResponseResult.Success>(result).response
        val signedRemaining = (claims.getValue("exp") as Number).toLong() - Clock.System.now().epochSeconds
        assertTrue(kotlin.math.abs(requireNotNull(response.expiresIn) - signedRemaining) <= 1)
        assertTrue(requireNotNull(response.expiresIn) <= 30, "expires_in must respect remaining deadline, got ${response.expiresIn}; signed exp=${claims["exp"]}, iat=${claims["iat"]}")
    }

    @Test fun dpopBoundRefreshRejectsMissingProof() = runTest {
        val (result, _) = flow(false)
        assertIs<AccessTokenResponseResult.Failure>(result, "A DPoP-bound refresh must reject omission of the key proof")
    }
}
