package id.walt.openid4vci

import id.walt.openid4vci.core.OAuth2ProviderConfig
import id.walt.openid4vci.preauthorized.DefaultPreAuthorizedCodeIssuer
import id.walt.openid4vci.repository.authorization.defaultAuthorizationCodeRepository
import id.walt.openid4vci.repository.preauthorized.defaultPreAuthorizedCodeRepository
import id.walt.openid4vci.tokens.AccessTokenService
import id.walt.openid4vci.validation.AccessRequestValidator
import id.walt.openid4vci.validation.AuthorizeRequestValidator
import id.walt.openid4vci.validation.DefaultAccessRequestValidator
import id.walt.openid4vci.validation.DefaultAuthorizeRequestValidator

internal fun createTestConfig(
    authorizeRequestValidator: AuthorizeRequestValidator = DefaultAuthorizeRequestValidator(),
    accessRequestValidator: AccessRequestValidator = DefaultAccessRequestValidator(),
    tokenService: AccessTokenService = StubTokenService(),
): OAuth2ProviderConfig {
    val authorizationCodeRepository = defaultAuthorizationCodeRepository()
    val preAuthorizedCodeRepository = defaultPreAuthorizedCodeRepository()
    return OAuth2ProviderConfig(
        authorizeRequestValidator = authorizeRequestValidator,
        accessRequestValidator = accessRequestValidator,
        authorizeEndpointHandlers = AuthorizeEndpointHandlers(),
        tokenEndpointHandlers = TokenEndpointHandlers(),
        authorizationCodeRepository = authorizationCodeRepository,
        preAuthorizedCodeRepository = preAuthorizedCodeRepository,
        preAuthorizedCodeIssuer = DefaultPreAuthorizedCodeIssuer(preAuthorizedCodeRepository),
        tokenService = tokenService,
    )
}

internal class StubTokenService : AccessTokenService {
    override suspend fun createAccessToken(claims: Map<String, Any?>): String {
        val clientId = claims["client_id"]?.toString().orEmpty()
        val code = claims["code"]?.toString() ?: claims["pre_authorized_code"]?.toString() ?: "code"
        return "access-$clientId-$code"
    }
}
