package id.walt.oid4vc.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ResponseMode {
    query,
    fragment,
    form_post,
    direct_post,
    @SerialName("direct_post.jwt") direct_post_jwt,
    post;

    override fun toString(): String {
        return when(this) {
            direct_post_jwt -> "direct_post.jwt"
            else -> super.toString()
        }
    }

    companion object {
        fun fromString(value: String): ResponseMode {
            return when (value) {
                "direct_post.jwt" -> direct_post_jwt
                else -> ResponseMode.valueOf(value)
            }
        }
    }
}
