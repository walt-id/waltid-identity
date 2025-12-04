package id.walt.openid4vci

import id.walt.openid4vci.core.Config
import id.walt.openid4vci.preauthorized.DefaultPreAuthorizedCodeIssuer
import id.walt.openid4vci.repository.authorization.defaultAuthorizationCodeRepository
import id.walt.openid4vci.repository.preauthorized.defaultPreAuthorizedCodeRepository
import id.walt.openid4vci.validation.AccessRequestValidator
import id.walt.openid4vci.validation.AuthorizeRequestValidator
import id.walt.openid4vci.validation.DefaultAccessRequestValidator
import id.walt.openid4vci.validation.DefaultAuthorizeRequestValidator

internal fun createTestConfig(
    authorizeRequestValidator: AuthorizeRequestValidator = DefaultAuthorizeRequestValidator(),
    accessRequestValidator: AccessRequestValidator = DefaultAccessRequestValidator(),
): Config {
    val authorizationCodeRepository = defaultAuthorizationCodeRepository()
    val preAuthorizedCodeRepository = defaultPreAuthorizedCodeRepository()
    return Config(
        authorizeRequestValidator = authorizeRequestValidator,
        accessRequestValidator = accessRequestValidator,
        authorizeEndpointHandlers = AuthorizeEndpointHandlers(),
        tokenEndpointHandlers = TokenEndpointHandlers(),
        authorizationCodeRepository = authorizationCodeRepository,
        preAuthorizedCodeRepository = preAuthorizedCodeRepository,
        preAuthorizedCodeIssuer = DefaultPreAuthorizedCodeIssuer(preAuthorizedCodeRepository),
    )
}
