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
    FORM_POST;

    companion object {
        val ENCRYPTED_RESPONSES = setOf(DIRECT_POST_JWT, DC_API_JWT)
        val DC_API_RESPONSES = setOf(DC_API, DC_API_JWT)
        val DIRECT_POST_RESPONSES = setOf(DIRECT_POST, DIRECT_POST_JWT)

        val CLEARTEXT_NORMAL_RESPONSES = OpenID4VPResponseMode.entries - ENCRYPTED_RESPONSES - DC_API_RESPONSES

        init {
            check(DC_API !in CLEARTEXT_NORMAL_RESPONSES && DIRECT_POST_JWT !in CLEARTEXT_NORMAL_RESPONSES)
        }
    }
}
