package id.walt.openid4vci.core

import id.walt.openid4vci.clientauth.ClientAuthenticationServiceConfig
import id.walt.openid4vci.clientauth.attestation.ClientAttestationConfig
import id.walt.openid4vci.handlers.endpoints.authorization.AuthorizationEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.par.PushedAuthorizationEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.token.TokenEndpointHandlers
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssuer
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.repository.par.PARRepository
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import id.walt.openid4vci.repository.refresh.RefreshTokenRepository
import id.walt.openid4vci.tokens.access.AccessTokenIssuer
import id.walt.openid4vci.tokens.access.AccessTokenVerifier
import id.walt.openid4vci.tokens.refresh.RefreshTokenIssuer
import id.walt.openid4vci.tokens.refresh.RefreshTokenVerifier
import id.walt.openid4vci.responses.par.PushedAuthorizationResponse
import id.walt.openid4vci.validation.AccessTokenRequestValidator
import id.walt.openid4vci.validation.AuthorizationRequestValidator
import id.walt.openid4vci.validation.CredentialRequestValidator
import id.walt.openid4vci.validation.IssuerStateValidator

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
    val authorizationServerIssuer: String? = null,

    val authorizationRequestValidator: AuthorizationRequestValidator,
    val issuerStateValidator: IssuerStateValidator? = null,
    val authorizationEndpointHandlers: AuthorizationEndpointHandlers,
    val authorizationCodeRepository: AuthorizationCodeRepository,

    val pushedAuthorizationEndpointHandlers: PushedAuthorizationEndpointHandlers = PushedAuthorizationEndpointHandlers(),
    val pushedAuthorizationConfig: PushedAuthorizationConfig? = null,

    val clientAuthenticationServiceConfig: ClientAuthenticationServiceConfig = ClientAuthenticationServiceConfig(),
    val clientAttestationConfig: ClientAttestationConfig? = null,

    val accessTokenRequestValidator: AccessTokenRequestValidator,
    val tokenEndpointHandlers: TokenEndpointHandlers,
    val accessTokenIssuer: AccessTokenIssuer,
    val accessTokenVerifier: AccessTokenVerifier? = null,
    val refreshTokenIssuer: RefreshTokenIssuer,
    val refreshTokenVerifier: RefreshTokenVerifier,
    val refreshTokenRepository: RefreshTokenRepository,

    val preAuthorizedCodeRepository: PreAuthorizedCodeRepository,
    val preAuthorizedCodeIssuer: PreAuthorizedCodeIssuer,

    val credentialRequestValidator: CredentialRequestValidator,
    val credentialEndpointHandlers: CredentialEndpointHandlers,
)

data class PushedAuthorizationConfig(
    val repository: PARRepository,
    val requestUriPrefix: String = PushedAuthorizationResponse.DEFAULT_REQUEST_URI_PREFIX,
    val lifetimeSeconds: Int = 90,
    val enforcePushedAuthorizationRequests: Boolean = false,
) {
    init {
        require(requestUriPrefix.isNotBlank()) { "PAR requestUriPrefix must not be blank" }
        require(lifetimeSeconds > 0) { "PAR lifetimeSeconds must be positive" }
    }
}
