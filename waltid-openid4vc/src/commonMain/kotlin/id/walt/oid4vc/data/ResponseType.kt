package id.walt.oid4vc.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ResponseType(val value: String) {
    @SerialName("id_token") IdToken("id_token"),
    @SerialName("token") Token("token"),
    @SerialName("code") Code("code"),
    @SerialName("vp_token") VpToken("vp_token");

    companion object {
        fun getResponseTypeString(vararg types: ResponseType) = types.joinToString(" ") { it.value }
        fun getResponseTypeString(types: Set<ResponseType>) = getResponseTypeString(*types.toTypedArray())
        fun fromResponseTypeString(responseTypeString: String) = responseTypeString.split(" ").map { fromValue(it) ?: throw Exception("Invalid response type: $it") }.toSet()
        fun fromValue(value: String): ResponseType? {
            return entries.find { it.value == value }
        }
    }
}
