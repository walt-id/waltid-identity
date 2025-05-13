package id.walt.verifier.openid.models.openid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class OpenID4VPResponseType(val responseType: String) {
    /**
     * The Wallet will return a `vp_token` containing the presentation(s).
     */
    @SerialName("vp_token")
    VP_TOKEN("vp_token"),

    /**
     * For SIOPv2 integration, returns both `vp_token` and a self-issued `id_token`.
     */
    @SerialName("vp_token id_token")
    VP_TOKEN_ID_TOKEN("vp_token id_token"),

    /**
     * For OAuth 2.0 Authorization Code flow. The Wallet returns a code, which
     * the Verifier exchanges for tokens (including `vp_token`) at the Wallet's token endpoint.
     */
    @SerialName("code")
    CODE("code")
}
