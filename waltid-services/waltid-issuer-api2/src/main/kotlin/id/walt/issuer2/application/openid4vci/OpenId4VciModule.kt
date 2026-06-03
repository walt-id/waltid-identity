package id.walt.issuer2.application.openid4vci

import id.walt.crypto.keys.KeyManager
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.core.OAuth2ProviderConfig
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.handlers.endpoints.authorization.AuthorizationEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.token.TokenEndpointHandlers
import id.walt.openid4vci.preauthorized.DefaultPreAuthorizedCodeIssuer
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssuer
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import id.walt.openid4vci.tokens.jwt.JwtAccessTokenIssuer
import id.walt.openid4vci.tokens.jwt.JwtAccessTokenVerifier
import id.walt.openid4vci.tokens.jwt.JwtSigningKeyResolver
import id.walt.openid4vci.tokens.jwt.JwtVerificationKeyResolver
import id.walt.openid4vci.validation.DefaultAccessTokenRequestValidator
import id.walt.openid4vci.validation.DefaultAuthorizationRequestValidator
import id.walt.openid4vci.validation.DefaultCredentialRequestValidator

data class OpenId4VciModule(
    val oauth2Provider: OAuth2Provider,
    val preAuthorizedCodeIssuer: PreAuthorizedCodeIssuer,
) {

    companion object {
        fun create(
            config: Issuer2ServiceConfig,
            authorizationCodeRepository: AuthorizationCodeRepository,
            preAuthorizedCodeRepository: PreAuthorizedCodeRepository,
        ): OpenId4VciModule {
            val signingKeyResolver = JwtSigningKeyResolver {
                KeyManager.resolveSerializedKey(config.ciTokenKey)
            }
            val verificationKeyResolver = JwtVerificationKeyResolver {
                KeyManager.resolveSerializedKey(config.ciTokenKey)
            }
            val preAuthorizedCodeIssuer = DefaultPreAuthorizedCodeIssuer(preAuthorizedCodeRepository)

            val accessTokenVerifier = JwtAccessTokenVerifier(verificationKeyResolver)
            val provider = buildOAuth2Provider(
                OAuth2ProviderConfig(
                    authorizationRequestValidator = DefaultAuthorizationRequestValidator(),
                    authorizationEndpointHandlers = AuthorizationEndpointHandlers(),
                    authorizationCodeRepository = authorizationCodeRepository,

                    accessTokenRequestValidator = DefaultAccessTokenRequestValidator(),
                    tokenEndpointHandlers = TokenEndpointHandlers(),
                    accessTokenService = JwtAccessTokenIssuer(signingKeyResolver),
                    accessTokenVerifier = accessTokenVerifier,

                    preAuthorizedCodeRepository = preAuthorizedCodeRepository,
                    preAuthorizedCodeIssuer = preAuthorizedCodeIssuer,

                    credentialRequestValidator = DefaultCredentialRequestValidator(),
                    credentialEndpointHandlers = CredentialEndpointHandlers(),
                )
            )

            return OpenId4VciModule(
                oauth2Provider = provider,
                preAuthorizedCodeIssuer = preAuthorizedCodeIssuer,
            )
        }
    }
}
