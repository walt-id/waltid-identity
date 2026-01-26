package id.walt.openid4vci

import id.walt.openid4vci.core.OAuth2ProviderConfig
import id.walt.openid4vci.preauthorized.DefaultPreAuthorizedCodeIssuer
import id.walt.openid4vci.repository.authorization.defaultAuthorizationCodeRepository
import id.walt.openid4vci.repository.preauthorized.defaultPreAuthorizedCodeRepository
import id.walt.openid4vci.tokens.AccessTokenService
import id.walt.openid4vci.handlers.endpoints.authorization.AuthorizationEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.token.TokenEndpointHandlers
import id.walt.openid4vci.validation.AccessTokenRequestValidator
import id.walt.openid4vci.validation.AuthorizationRequestValidator
import id.walt.openid4vci.validation.DefaultAccessTokenRequestValidator
import id.walt.openid4vci.validation.DefaultAuthorizationRequestValidator
import id.walt.openid4vci.validation.CredentialRequestValidator
import id.walt.openid4vci.validation.DefaultCredentialRequestValidator

internal fun createTestConfig(
    authorizationRequestValidator: AuthorizationRequestValidator = DefaultAuthorizationRequestValidator(),
    accessRequestValidator: AccessTokenRequestValidator = DefaultAccessTokenRequestValidator(),
    credentialRequestValidator: CredentialRequestValidator = DefaultCredentialRequestValidator(),
    accessTokenService: AccessTokenService = StubTokenService(),
): OAuth2ProviderConfig {
    val authorizationCodeRepository = defaultAuthorizationCodeRepository()
    val preAuthorizedCodeRepository = defaultPreAuthorizedCodeRepository()
    return OAuth2ProviderConfig(
        authorizationRequestValidator = authorizationRequestValidator,
        accessTokenRequestValidator = accessRequestValidator,
        authorizationEndpointHandlers = AuthorizationEndpointHandlers(),
        tokenEndpointHandlers = TokenEndpointHandlers(),
        authorizationCodeRepository = authorizationCodeRepository,
        preAuthorizedCodeRepository = preAuthorizedCodeRepository,
        preAuthorizedCodeIssuer = DefaultPreAuthorizedCodeIssuer(preAuthorizedCodeRepository),
        accessTokenService = accessTokenService,
        credentialRequestValidator = credentialRequestValidator,
        credentialEndpointHandlers = CredentialEndpointHandlers(),
    )
}

internal class StubTokenService : AccessTokenService {
    override suspend fun createAccessToken(claims: Map<String, Any?>): String {
        val clientId = claims["client_id"]?.toString().orEmpty()
        val code = claims["code"]?.toString() ?: claims["pre_authorized_code"]?.toString() ?: "code"
        return "access-$clientId-$code"
    }
}
