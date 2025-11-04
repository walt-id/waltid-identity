package id.walt.ktorauthnz.methods.sessiondata

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
    val idpJwksUrl: String,
    val idpIss: String
) : SessionData {

    constructor(openIdConfiguration: OIDC.OpenIdConfiguration) : this(
        idpJwksUrl = openIdConfiguration.jwksUri,
        idpIss = openIdConfiguration.issuer
    )

}
