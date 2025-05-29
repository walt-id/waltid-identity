package id.walt.verifier.openid.models.authorization

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * HTTP Methods for request_uri_method.
 * See Section 5.1 (request_uri_method) of OpenID4VP spec.
 */
@Serializable
enum class RequestUriHttpMethod(val method: String) {
    @SerialName("get")
    GET("get"),
    @SerialName("post")
    POST("post"),
}
