package id.walt.idp.oidc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthorizeRequest(
    /** CSRF protection */
    val state: String? = null,
    /** server-side relay protection */
    val nonce: String? = null,
    /** scope */
    //val scope: List<String>,
    val scope: String,
    /** OIDC Provider will redirect here */
    @SerialName("redirect_uri")
    val redirectUri: String,
    @SerialName("response_type")
    val responseType: String = "code",
    /** Relying Party identifier */
    @SerialName("client_id")
    val clientId: String,
)
