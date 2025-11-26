package id.walt.ktorauthnz.methods.sessiondata

import id.walt.ktorauthnz.accounts.identifiers.methods.OIDCIdentifier
import id.walt.ktorauthnz.methods.OIDC
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("oidc-auth-step")
data class OidcSessionAuthenticationStepData(
    val state: String,
    val nonce: String,
    val codeVerifier: String? = null, // For PKCE
) : SessionData

@Serializable
@SerialName("oidc-authenticated")
data class OidcSessionAuthenticatedData(
    val tokenValidationData: TokenValidationData,
    val oidcIdentifier: OIDCIdentifier
) : SessionData {

    @Serializable
    data class TokenValidationData(
        val idpJwksUrl: String,
        val idpIss: String,
    ) {
        constructor(openIdConfiguration: OIDC.OpenIdConfiguration) : this(
            idpJwksUrl = openIdConfiguration.jwksUri,
            idpIss = openIdConfiguration.issuer
        )
    }

}
