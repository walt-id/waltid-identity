package id.walt.openid4vci.metadata.issuer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Display metadata for the Credential Issuer (OpenID4VCI 1.0).
 */
@Serializable
data class IssuerDisplay(
    val name: String? = null,
    val locale: String? = null,
    val logo: IssuerLogo? = null,
) {
    init {
        name?.let { require(it.isNotBlank()) { "display name must not be blank" } }
        locale?.let { require(it.isNotBlank()) { "display locale must not be blank" } }
    }
}

@Serializable
data class IssuerLogo(
    val uri: String,
    @SerialName("alt_text")
    val altText: String? = null,
) {
    init {
        require(uri.isNotBlank()) { "logo uri must not be blank" }
    }
}
