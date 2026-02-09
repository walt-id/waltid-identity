package id.walt.openid4vci.core

import id.walt.openid4vci.AuthorizeEndpointHandlers
import id.walt.openid4vci.TokenEndpointHandlers
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssuer
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import id.walt.openid4vci.tokens.AccessTokenService
import id.walt.openid4vci.validation.AccessRequestValidator
import id.walt.openid4vci.validation.AuthorizeRequestValidator

/**
 * Configuration holds handler registries and injectable objects used by the provider.
 *
 * Roadmap (mirrors the bullets in Builder.kt):
 * - authorizeRequestValidator → parse/validate authorize endpoint input
 * - accessRequestValidator → parse/validate token endpoint input
 * - authorizeEndpointHandlers / tokenEndpointHandlers → the handlers
 * - repository → placeholder storage until implementation
 * - builder adaptability → expose knobs so advanced callers can replace validators or strategies
 *
 * All dependencies are supplied explicitly when building the config. The bundled in-memory
 * repositories stay internal to the DI layer so applications pass in their own implementations.
 */
data class OAuth2ProviderConfig(
    val authorizeRequestValidator: AuthorizeRequestValidator,
    val accessRequestValidator: AccessRequestValidator,
    val authorizeEndpointHandlers: AuthorizeEndpointHandlers,
    val tokenEndpointHandlers: TokenEndpointHandlers,
    val authorizationCodeRepository: AuthorizationCodeRepository,
    val preAuthorizedCodeRepository: PreAuthorizedCodeRepository,
    val preAuthorizedCodeIssuer: PreAuthorizedCodeIssuer,
    val tokenService: AccessTokenService,
)
