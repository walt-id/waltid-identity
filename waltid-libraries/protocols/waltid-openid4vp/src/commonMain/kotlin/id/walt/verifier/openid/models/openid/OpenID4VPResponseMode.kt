package id.walt.verifier.openid.models.openid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Actually OauthResponseMode?
@Serializable
enum class OpenID4VPResponseMode {

    /**
     * (Default for vp_token if using redirect)
     * Response parameters in URL fragment. For same-device.
     */
    @SerialName("fragment")
    FRAGMENT,

    /**
     * Response parameters in URL query string. Used with response_type=code.
     */
    @SerialName("query")
    QUERY,

    /**
     * Wallet POSTs the response to response_uri. For cross-device or large responses.
     */
    @SerialName("direct_post")
    DIRECT_POST,

    /**
     * Like direct_post, but the response is a JWE.
     */
    @SerialName("direct_post.jwt")
    DIRECT_POST_JWT,

    /** When using the Digital Credentials API (Appendix A). */
    @SerialName("dc_api")
    DC_API,

    /** When using the Digital Credentials API (Appendix A). */
    @SerialName("dc_api.jwt")
    DC_API_JWT,

    /** General OAuth, less emphasized in OpenID4VP examples */
    @SerialName("form_post")
    FORM_POST
}
