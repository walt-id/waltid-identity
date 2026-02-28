package id.walt.openid4vci.core

import id.walt.openid4vci.handlers.endpoints.authorization.AuthorizationEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.token.TokenEndpointHandlers
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssuer
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import id.walt.openid4vci.tokens.AccessTokenService
import id.walt.openid4vci.validation.AccessTokenRequestValidator
import id.walt.openid4vci.validation.AuthorizationRequestValidator

/**
 * Configuration holds handler registries and injectable objects used by the provider.
 *
 * Roadmap (mirrors the bullets in Builder.kt):
 * - authorizationRequestValidator → parse/validate authorize endpoint input
 * - accessTokenRequestValidator → parse/validate token endpoint input
 * - authorizationEndpointHandlers / tokenEndpointHandlers → the handlers
 * - repository → placeholder storage until implementation
 * - builder adaptability → expose knobs so advanced callers can replace validators or strategies
 *
 * All dependencies are supplied explicitly when building the config. The bundled in-memory
 * repositories stay internal to the DI layer so applications pass in their own implementations.
 */
data class OAuth2ProviderConfig(
    val authorizationRequestValidator: AuthorizationRequestValidator,
    val accessTokenRequestValidator: AccessTokenRequestValidator,
    val authorizationEndpointHandlers: AuthorizationEndpointHandlers,
    val tokenEndpointHandlers: TokenEndpointHandlers,
    val authorizationCodeRepository: AuthorizationCodeRepository,
    val preAuthorizedCodeRepository: PreAuthorizedCodeRepository,
    val preAuthorizedCodeIssuer: PreAuthorizedCodeIssuer,
    val accessTokenService: AccessTokenService,
)
