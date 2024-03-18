package id.walt.oid4vc.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ResponseMode(val value: String) {
    @SerialName("query") Query("query"),
    @SerialName("fragment") Fragment("fragment"),
    @SerialName("form_post") FormPost("form_post"),
    @SerialName("direct_post") DirectPost("direct_post"),
    @SerialName("post") Post("post");

    companion object {
        fun fromValue(value: String): ResponseMode? {
            return entries.find { it.value == value }
        }
    }
}
