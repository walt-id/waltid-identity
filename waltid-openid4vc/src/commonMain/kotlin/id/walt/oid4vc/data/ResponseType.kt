package id.walt.oid4vc.data

enum class ResponseType(val value: String) {
    IdToken("id_token"),
    Token("token"),
    Code("code"),
    VpToken("vp_token");

    companion object {
        fun getResponseTypeString(vararg types: ResponseType) = types.joinToString(" ") { it.value }
        fun getResponseTypeString(types: Set<ResponseType>) = getResponseTypeString(*types.toTypedArray())
        fun fromResponseTypeString(responseTypeString: String) = responseTypeString.split(" ").map { fromValue(it) ?: throw Exception("Invalid response type: $it") }.toSet()
        fun fromValue(value: String): ResponseType? {
            return entries.find { it.value == value }
        }
    }
}
