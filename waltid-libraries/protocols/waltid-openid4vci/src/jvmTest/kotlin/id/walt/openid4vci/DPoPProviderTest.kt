package id.walt.openid4vci

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.ShaUtils
import id.walt.openid4vci.core.TOKEN_TYPE_DPOP
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.dpop.DPoPConstants
import id.walt.openid4vci.dpop.DefaultDPoPProofVerifier
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssueRequest
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.requests.notification.NotificationRequestResult
import id.walt.openid4vci.validation.NotificationRequestValidator
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.tokens.access.AccessTokenAuthorization
import id.walt.openid4vci.tokens.access.AccessTokenAuthorizationScheme
import id.walt.openid4vci.tokens.access.AccessTokenIssuer
import id.walt.openid4vci.tokens.access.AccessTokenVerifier
import id.walt.openid4vci.tokens.access.ProtectedResourceAccessTokenContext
import id.walt.openid4vci.tokens.jwt.JwtConfirmationClaims
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class DPoPProviderTest {

    @Test
    fun `authorization code token request binds the access token to the DPoP key`() = runTest {
        val tokenIssuer = CapturingDPoPTokenIssuer()
        val config = createTestConfig(
            accessTokenIssuer = tokenIssuer,
            dpopProofVerifier = dpopVerifier,
        )
        val provider = buildOAuth2Provider(config)
        val authorizationRequest = assertIs<AuthorizationRequestResult.Success>(
            provider.createAuthorizationRequest(
                mapOf(
                    "response_type" to listOf(ResponseType.CODE.value),
                    "client_id" to listOf("wallet-client"),
                    "redirect_uri" to listOf("https://wallet.example/callback"),
                    "scope" to listOf("openid credential"),
                ),
            ),
        ).request.withIssuer(ISSUER)
        val code = assertIs<AuthorizationResponseResult.Success>(
            provider.createAuthorizationResponse(
                authorizationRequest,
                DefaultSession(subject = "wallet-subject"),
            ),
        ).response.code
        val walletKey = JWKKey.generate(KeyType.secp256r1)

        val tokenRequest = assertIs<AccessTokenRequestResult.Success>(
            provider.createAccessTokenRequest(
                parameters = mapOf(
                    "grant_type" to listOf(GrantType.AuthorizationCode.value),
                    "client_id" to listOf("wallet-client"),
                    "code" to listOf(code),
                    "redirect_uri" to listOf("https://wallet.example/callback"),
                ),
                headers = mapOf(
                    DPoPConstants.HEADER_NAME to listOf(createProof(walletKey, TOKEN_ENDPOINT_URI)),
                ),
                tokenEndpointUri = TOKEN_ENDPOINT_URI,
            ),
        ).request.withIssuer(ISSUER)
        val tokenResponse = assertIs<AccessTokenResponseResult.Success>(
            provider.createAccessTokenResponse(tokenRequest),
        ).response

        assertDPoPToken(tokenResponse.tokenType, tokenIssuer.lastClaims, walletKey)
    }

    @Test
    fun `pre-authorized code token request binds the access token to the DPoP key`() = runTest {
        val tokenIssuer = CapturingDPoPTokenIssuer()
        val config = createTestConfig(
            accessTokenIssuer = tokenIssuer,
            dpopProofVerifier = dpopVerifier,
        )
        val provider = buildOAuth2Provider(config)
        val code = config.preAuthorizedCodeIssuer.issue(
            PreAuthorizedCodeIssueRequest(
                scopes = setOf("credential"),
                audience = setOf(ISSUER),
                session = DefaultSession(subject = "wallet-subject"),
            ),
        ).code
        val walletKey = JWKKey.generate(KeyType.secp256r1)

        val tokenRequest = assertIs<AccessTokenRequestResult.Success>(
            provider.createAccessTokenRequest(
                parameters = mapOf(
                    "grant_type" to listOf(GrantType.PreAuthorizedCode.value),
                    "pre-authorized_code" to listOf(code),
                ),
                headers = mapOf(
                    DPoPConstants.HEADER_NAME to listOf(createProof(walletKey, TOKEN_ENDPOINT_URI)),
                ),
                tokenEndpointUri = TOKEN_ENDPOINT_URI,
            ),
        ).request.withIssuer(ISSUER)
        val tokenResponse = assertIs<AccessTokenResponseResult.Success>(
            provider.createAccessTokenResponse(tokenRequest),
        ).response

        assertDPoPToken(tokenResponse.tokenType, tokenIssuer.lastClaims, walletKey)
    }

    @Test
    fun `credential request rejects an access token presented with another wallet key`() = runTest {
        val walletOneKey = JWKKey.generate(KeyType.secp256r1)
        val walletTwoKey = JWKKey.generate(KeyType.secp256r1)
        val accessTokenVerifier = object : AccessTokenVerifier {
            override suspend fun verify(
                token: String,
                expectedIssuer: String?,
                expectedAudience: String?,
            ): JsonObject = buildJsonObject {
                put(JwtPayloadClaims.CONFIRMATION, buildJsonObject {
                    put(JwtConfirmationClaims.JWK_THUMBPRINT, walletTwoKey.getPublicKey().getThumbprint())
                })
            }
        }
        val provider = buildOAuth2Provider(
            createTestConfig(
                accessTokenVerifier = accessTokenVerifier,
                dpopProofVerifier = dpopVerifier,
            ),
        )
        val proof = createProof(
            key = walletOneKey,
            targetUri = CREDENTIAL_ENDPOINT_URI,
            accessToken = ACCESS_TOKEN,
        )

        val failure = assertIs<CredentialRequestResult.OAuthFailure>(
            provider.createCredentialRequest(
                parameters = emptyMap(),
                accessTokenContext = ProtectedResourceAccessTokenContext(
                    authorization = AccessTokenAuthorization(
                        scheme = AccessTokenAuthorizationScheme.DPOP,
                        token = ACCESS_TOKEN,
                    ),
                    expectedIssuer = ISSUER,
                    dpopProofHeaderValues = listOf(proof),
                    targetUri = CREDENTIAL_ENDPOINT_URI,
                ),
            ),
        )
        val response = provider.writeCredentialError(failure.error)

        assertEquals(OAuthErrorCodes.INVALID_TOKEN, failure.error.error)
        assertEquals(401, response.status)
        assertEquals("no-store", response.headers["Cache-Control"])
        assertTrue(response.headers["WWW-Authenticate"]?.startsWith(TOKEN_TYPE_DPOP) == true)
    }

    @Test
    fun `credential request accepts an access token presented with its bound wallet key`() = runTest {
        val walletKey = JWKKey.generate(KeyType.secp256r1)
        val accessTokenVerifier = object : AccessTokenVerifier {
            override suspend fun verify(
                token: String,
                expectedIssuer: String?,
                expectedAudience: String?,
            ): JsonObject = buildJsonObject {
                put(JwtPayloadClaims.CONFIRMATION, buildJsonObject {
                    put(JwtConfirmationClaims.JWK_THUMBPRINT, walletKey.getPublicKey().getThumbprint())
                })
            }
        }
        val provider = buildOAuth2Provider(
            createTestConfig(
                accessTokenVerifier = accessTokenVerifier,
                dpopProofVerifier = dpopVerifier,
            ),
        )
        val proof = createProof(
            key = walletKey,
            targetUri = CREDENTIAL_ENDPOINT_URI,
            accessToken = ACCESS_TOKEN,
        )

        val result = provider.createCredentialRequest(
            parameters = mapOf("credential_configuration_id" to listOf("identity_credential")),
            accessTokenContext = ProtectedResourceAccessTokenContext(
                authorization = AccessTokenAuthorization(
                    scheme = AccessTokenAuthorizationScheme.DPOP,
                    token = ACCESS_TOKEN,
                ),
                expectedIssuer = ISSUER,
                dpopProofHeaderValues = listOf(proof),
                targetUri = CREDENTIAL_ENDPOINT_URI,
            ),
        )

        assertIs<CredentialRequestResult.Success>(result)
    }

    @Test
    fun `notification request rejects an access token presented with another wallet key`() = runTest {
        val walletOneKey = JWKKey.generate(KeyType.secp256r1)
        val walletTwoKey = JWKKey.generate(KeyType.secp256r1)
        val accessTokenVerifier = object : AccessTokenVerifier {
            override suspend fun verify(
                token: String,
                expectedIssuer: String?,
                expectedAudience: String?,
            ): JsonObject = buildJsonObject {
                put(JwtPayloadClaims.CONFIRMATION, buildJsonObject {
                    put(JwtConfirmationClaims.JWK_THUMBPRINT, walletTwoKey.getPublicKey().getThumbprint())
                })
            }
        }
        val provider = buildOAuth2Provider(
            createTestConfig(
                accessTokenVerifier = accessTokenVerifier,
                dpopProofVerifier = dpopVerifier,
            ).copy(notificationRequestValidator = NotificationRequestValidator {
                error("Notification parameters must not be validated when DPoP binding fails")
            }),
        )
        val proof = createProof(
            key = walletOneKey,
            targetUri = "https://issuer.example/notification",
            accessToken = ACCESS_TOKEN,
        )

        val failure = assertIs<NotificationRequestResult.OAuthFailure>(
            provider.createNotificationRequest(
                parameters = emptyMap(),
                accessTokenContext = ProtectedResourceAccessTokenContext(
                    authorization = AccessTokenAuthorization(
                        scheme = AccessTokenAuthorizationScheme.DPOP,
                        token = ACCESS_TOKEN,
                    ),
                    expectedIssuer = ISSUER,
                    dpopProofHeaderValues = listOf(proof),
                    targetUri = "https://issuer.example/notification",
                ),
            ),
        )
        val response = provider.writeNotificationError(failure.error, AccessTokenAuthorizationScheme.DPOP)

        assertEquals(OAuthErrorCodes.INVALID_TOKEN, failure.error.error)
        assertEquals(401, response.status)
        assertEquals("no-store", response.headers["Cache-Control"])
        assertTrue(response.headers["WWW-Authenticate"]?.startsWith(TOKEN_TYPE_DPOP) == true)
    }

    @Test
    fun `notification request accepts an access token presented with its bound wallet key`() = runTest {
        val walletKey = JWKKey.generate(KeyType.secp256r1)
        val accessTokenVerifier = object : AccessTokenVerifier {
            override suspend fun verify(
                token: String,
                expectedIssuer: String?,
                expectedAudience: String?,
            ): JsonObject = buildJsonObject {
                put(JwtPayloadClaims.CONFIRMATION, buildJsonObject {
                    put(JwtConfirmationClaims.JWK_THUMBPRINT, walletKey.getPublicKey().getThumbprint())
                })
            }
        }
        val provider = buildOAuth2Provider(
            createTestConfig(
                accessTokenVerifier = accessTokenVerifier,
                dpopProofVerifier = dpopVerifier,
            ),
        )
        val proof = createProof(
            key = walletKey,
            targetUri = "https://issuer.example/notification",
            accessToken = ACCESS_TOKEN,
        )

        val result = provider.createNotificationRequest(
            parameters = mapOf("notification_id" to listOf("notification"), "event" to listOf("credential_accepted")),
            accessTokenContext = ProtectedResourceAccessTokenContext(
                authorization = AccessTokenAuthorization(
                    scheme = AccessTokenAuthorizationScheme.DPOP,
                    token = ACCESS_TOKEN,
                ),
                expectedIssuer = ISSUER,
                dpopProofHeaderValues = listOf(proof),
                targetUri = "https://issuer.example/notification",
            ),
        )

        assertIs<NotificationRequestResult.Success>(result)
    }

    private suspend fun assertDPoPToken(
        tokenType: String,
        claims: Map<String, Any?>,
        walletKey: JWKKey,
    ) {
        assertEquals(TOKEN_TYPE_DPOP, tokenType)
        val confirmation = assertIs<Map<*, *>>(claims[JwtPayloadClaims.CONFIRMATION])
        assertEquals(
            walletKey.getPublicKey().getThumbprint(),
            confirmation[JwtConfirmationClaims.JWK_THUMBPRINT],
        )
    }

    private suspend fun createProof(
        key: JWKKey,
        targetUri: String,
        accessToken: String? = null,
    ): String = key.signJws(
        buildJsonObject {
            put(JwtPayloadClaims.JWT_ID, "proof-${key.getPublicKey().getThumbprint()}")
            put(DPoPConstants.HTTP_METHOD_CLAIM, "POST")
            put(DPoPConstants.HTTP_URI_CLAIM, targetUri)
            put(JwtPayloadClaims.ISSUED_AT, NOW.epochSeconds)
            accessToken?.let {
                put(DPoPConstants.ACCESS_TOKEN_HASH_CLAIM, ShaUtils.calculateSha256Base64Url(it))
            }
        }.toString().encodeToByteArray(),
        headers = mapOf(
            JwtHeaderParams.TYPE to JsonPrimitive(DPoPConstants.JWT_TYPE),
            JwtHeaderParams.JSON_WEB_KEY to key.getPublicKey().exportJWKObject(),
        ),
    )

    private class CapturingDPoPTokenIssuer : AccessTokenIssuer {
        var lastClaims: Map<String, Any?> = emptyMap()
            private set

        override suspend fun issue(claims: Map<String, Any?>): String {
            lastClaims = claims
            return ACCESS_TOKEN
        }
    }

    private companion object {
        val NOW = Instant.fromEpochSeconds(1_800_000_000)
        val dpopVerifier = DefaultDPoPProofVerifier(now = { NOW })
        const val ISSUER = "https://issuer.example"
        const val TOKEN_ENDPOINT_URI = "$ISSUER/token"
        const val CREDENTIAL_ENDPOINT_URI = "$ISSUER/credential"
        const val ACCESS_TOKEN = "client-two-access-token"
    }
}
