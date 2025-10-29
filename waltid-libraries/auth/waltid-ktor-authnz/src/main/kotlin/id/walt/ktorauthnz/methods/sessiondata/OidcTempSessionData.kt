package id.walt.ktorauthnz.methods.sessiondata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("oidc")
data class OidcTempSessionData(
    val state: String,
    val nonce: String,
    val codeVerifier: String? = null, // For PKCE
) : SessionData
