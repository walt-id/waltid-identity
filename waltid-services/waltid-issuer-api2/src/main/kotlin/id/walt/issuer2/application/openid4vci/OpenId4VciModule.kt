package id.walt.issuer2.application.openid4vci

import id.walt.crypto.keys.KeyManager
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.openid4vci.clientauth.attestation.ClientAttestationConfig
import id.walt.openid4vci.clientauth.attestation.verifier.toClientAttestationConfig
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.core.OAuth2ProviderConfig
import id.walt.openid4vci.core.PushedAuthorizationConfig
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.handlers.endpoints.authorization.AuthorizationEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.token.TokenEndpointHandlers
import id.walt.openid4vci.preauthorized.DefaultPreAuthorizedCodeIssuer
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssuer
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.repository.par.PARRepository
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import id.walt.openid4vci.repository.refresh.RefreshTokenRepository
import id.walt.openid4vci.tokens.jwt.access.JwtAccessTokenVerifier
import id.walt.openid4vci.tokens.jwt.access.JwtAccessTokenIssuer
import id.walt.openid4vci.tokens.jwt.refresh.JwtRefreshTokenIssuer
import id.walt.openid4vci.tokens.jwt.refresh.JwtRefreshTokenVerifier
import id.walt.openid4vci.tokens.jwt.JwtSigningKeyResolver
import id.walt.openid4vci.tokens.jwt.JwtVerificationKeyResolver
import id.walt.openid4vci.validation.DefaultAccessTokenRequestValidator
import id.walt.openid4vci.validation.DefaultAuthorizationRequestValidator
import id.walt.openid4vci.validation.DefaultCredentialRequestValidator
import kotlinx.coroutines.runBlocking

data class OpenId4VciModule(
    val oauth2Provider: OAuth2Provider,
    val preAuthorizedCodeIssuer: PreAuthorizedCodeIssuer,
) {

    companion object {
        fun create(
            config: Issuer2ServiceConfig,
            authorizationCodeRepository: AuthorizationCodeRepository,
            preAuthorizedCodeRepository: PreAuthorizedCodeRepository,
            parRepository: PARRepository,
            refreshTokenRepository: RefreshTokenRepository,
        ): OpenId4VciModule {
            val signingKeyResolver = JwtSigningKeyResolver {
                KeyManager.resolveSerializedKey(config.ciTokenKey)
            }
            val verificationKeyResolver = JwtVerificationKeyResolver {
                KeyManager.resolveSerializedKey(config.ciTokenKey)
            }
            val preAuthorizedCodeIssuer = DefaultPreAuthorizedCodeIssuer(
                repository = preAuthorizedCodeRepository,
                anonymousAccessSupported = config.supportsPreAuthAnonymous(),
            )

            val accessTokenVerifier = JwtAccessTokenVerifier(verificationKeyResolver)
            val provider = buildOAuth2Provider(
                OAuth2ProviderConfig(

                    authorizationServerIssuer = config.openId4VciBaseUrl(),
                    authorizationRequestValidator = DefaultAuthorizationRequestValidator(),
                    authorizationEndpointHandlers = AuthorizationEndpointHandlers(),
                    tokenEndpointHandlers = TokenEndpointHandlers(),
                    credentialEndpointHandlers = CredentialEndpointHandlers(),

                    authorizationRequestValidator = DefaultAuthorizationRequestValidator(),
                    accessTokenRequestValidator = DefaultAccessTokenRequestValidator(),
                    credentialRequestValidator = DefaultCredentialRequestValidator(),

                    authorizationCodeRepository = authorizationCodeRepository,
                    preAuthorizedCodeRepository = preAuthorizedCodeRepository,
                    refreshTokenRepository = refreshTokenRepository,

                    pushedAuthorizationConfig = PushedAuthorizationConfig(
                        repository = parRepository,
                        enforcePushedAuthorizationRequests = config.enforcePushedAuthorizationRequests,
                    ),
                    clientAttestationConfig = createClientAttestationConfig(config),


                    accessTokenIssuer = JwtAccessTokenIssuer(signingKeyResolver),
                    accessTokenVerifier = accessTokenVerifier,

                    refreshTokenIssuer = JwtRefreshTokenIssuer(signingKeyResolver),
                    refreshTokenVerifier = JwtRefreshTokenVerifier(verificationKeyResolver),

                    preAuthorizedCodeIssuer = preAuthorizedCodeIssuer,
                )
            )

            return OpenId4VciModule(
                oauth2Provider = provider,
                preAuthorizedCodeIssuer = preAuthorizedCodeIssuer,
            )
        }

        private fun createClientAttestationConfig(config: Issuer2ServiceConfig): ClientAttestationConfig? {
            val attestationConfig = config.clientAttestationConfig() ?: return null
            return runBlocking {
                attestationConfig.toClientAttestationConfig()
            }
        }
    }
}