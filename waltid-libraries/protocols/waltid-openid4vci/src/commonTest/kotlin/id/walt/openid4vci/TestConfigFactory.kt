package id.walt.openid4vci

import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.openid4vci.core.OAuth2ProviderConfig
import id.walt.openid4vci.preauthorized.DefaultPreAuthorizedCodeIssuer
import id.walt.openid4vci.repository.authorization.InMemoryAuthorizationCodeRepository
import id.walt.openid4vci.repository.preauthorized.InMemoryPreAuthorizedCodeRepository
import id.walt.openid4vci.repository.refresh.RefreshTokenRepository
import id.walt.openid4vci.repository.refresh.InMemoryRefreshTokenRepository
import id.walt.openid4vci.tokens.access.AccessTokenIssuer
import id.walt.openid4vci.tokens.access.AccessTokenVerifier
import id.walt.openid4vci.tokens.refresh.RefreshTokenClaims
import id.walt.openid4vci.tokens.refresh.RefreshTokenGenerationRequest
import id.walt.openid4vci.tokens.refresh.RefreshTokenIssuer
import id.walt.openid4vci.tokens.refresh.RefreshTokenVerifier
import id.walt.openid4vci.tokens.jwt.refresh.KEYCLOAK_REFRESH_TOKEN_TYPE
import id.walt.openid4vci.handlers.endpoints.authorization.AuthorizationEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.token.TokenEndpointHandlers
import id.walt.openid4vci.validation.AccessTokenRequestValidator
import id.walt.openid4vci.validation.AuthorizationRequestValidator
import id.walt.openid4vci.validation.DefaultAccessTokenRequestValidator
import id.walt.openid4vci.validation.DefaultAuthorizationRequestValidator
import id.walt.openid4vci.validation.CredentialRequestValidator
import id.walt.openid4vci.validation.DefaultCredentialRequestValidator
import id.walt.openid4vci.validation.IssuerStateValidator
import kotlin.random.Random
import kotlin.time.Clock

internal fun createTestConfig(
    authorizationRequestValidator: AuthorizationRequestValidator = DefaultAuthorizationRequestValidator(),
    accessRequestValidator: AccessTokenRequestValidator = DefaultAccessTokenRequestValidator(),
    credentialRequestValidator: CredentialRequestValidator = DefaultCredentialRequestValidator(),
    accessTokenIssuer: AccessTokenIssuer = StubTokenIssuer(),
    accessTokenVerifier: AccessTokenVerifier? = null,
    refreshTokenIssuer: RefreshTokenIssuer = TestRefreshTokenIssuer(),
    refreshTokenVerifier: RefreshTokenVerifier = refreshTokenIssuer as? RefreshTokenVerifier ?: TestRefreshTokenIssuer(),
    refreshTokenRepository: RefreshTokenRepository = InMemoryRefreshTokenRepository(),
    issuerStateValidator: IssuerStateValidator? = null,
): OAuth2ProviderConfig {
    val authorizationCodeRepository = InMemoryAuthorizationCodeRepository()
    val preAuthorizedCodeRepository = InMemoryPreAuthorizedCodeRepository()
    return OAuth2ProviderConfig(
        authorizationRequestValidator = authorizationRequestValidator,
        issuerStateValidator = issuerStateValidator,
        accessTokenRequestValidator = accessRequestValidator,
        authorizationEndpointHandlers = AuthorizationEndpointHandlers(),
        tokenEndpointHandlers = TokenEndpointHandlers(),
        authorizationCodeRepository = authorizationCodeRepository,
        preAuthorizedCodeRepository = preAuthorizedCodeRepository,
        preAuthorizedCodeIssuer = DefaultPreAuthorizedCodeIssuer(preAuthorizedCodeRepository),
        accessTokenIssuer = accessTokenIssuer,
        accessTokenVerifier = accessTokenVerifier,
        refreshTokenIssuer = refreshTokenIssuer,
        refreshTokenVerifier = refreshTokenVerifier,
        refreshTokenRepository = refreshTokenRepository,
        credentialRequestValidator = credentialRequestValidator,
        credentialEndpointHandlers = CredentialEndpointHandlers(),
    )
}

internal class StubTokenIssuer : AccessTokenIssuer {
    override suspend fun issue(claims: Map<String, Any?>): String {
        val clientId = claims["client_id"]?.toString().orEmpty()
        val code = claims["code"]?.toString() ?: claims["pre_authorized_code"]?.toString() ?: "code"
        return "access-$clientId-$code"
    }
}

internal class TestRefreshTokenIssuer : RefreshTokenIssuer, RefreshTokenVerifier {

    override suspend fun issue(request: RefreshTokenGenerationRequest): String {
        val payload = listOf(
            request.issuer,
            request.subject,
            request.clientId.orEmpty(),
            request.issuedAt.epochSeconds.toString(),
            request.expiresAt.epochSeconds.toString(),
            request.sessionId.orEmpty(),
            request.scopes.joinToString(" "),
        ).joinToString("|")
        val encodedPayload = payload.encodeToByteArray().encodeToBase64Url()
        val signature = Random.nextBytes(32).encodeToBase64Url()
        return "test-refresh.$encodedPayload.$signature"
    }

    override suspend fun verify(
        token: String,
        expectedIssuer: String?,
        expectedClientId: String?,
    ): RefreshTokenClaims {
        val claims = decodeClaims(token)
        if (!expectedIssuer.isNullOrBlank() && claims.issuer != expectedIssuer) {
            throw IllegalArgumentException("Issuer mismatch")
        }
        if (!expectedClientId.isNullOrBlank() && claims.issuedFor != expectedClientId) {
            throw IllegalArgumentException("Client mismatch")
        }
        if (Clock.System.now() >= claims.expiresAt) {
            throw IllegalArgumentException("Expired refresh token")
        }
        return claims
    }

    override fun signature(token: String): String {
        val parts = token.split('.')
        require(parts.size == 3 && parts[0] == "test-refresh" && parts[2].isNotBlank()) {
            "Invalid refresh token"
        }
        return parts[2]
    }

    private fun decodeClaims(token: String): RefreshTokenClaims {
        val parts = token.split('.')
        require(parts.size == 3) { "Invalid refresh token" }
        val values = parts[1].decodeFromBase64Url().decodeToString().split('|')
        require(values.size == 7) { "Invalid refresh token payload" }
        val scopes = values[6].splitToSequence(' ')
            .filter { it.isNotBlank() }
            .toSet()
        return RefreshTokenClaims(
            id = parts[2],
            issuer = values[0],
            subject = values[1],
            type = KEYCLOAK_REFRESH_TOKEN_TYPE,
            issuedFor = values[2].takeIf { it.isNotBlank() },
            audience = setOf(values[0]),
            scopes = scopes,
            sessionId = values[5].takeIf { it.isNotBlank() },
            issuedAt = kotlin.time.Instant.fromEpochSeconds(values[3].toLong()),
            expiresAt = kotlin.time.Instant.fromEpochSeconds(values[4].toLong()),
        )
    }
}
