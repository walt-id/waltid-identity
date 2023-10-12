package id.walt.oid4vc.data

enum class ResponseType {
    id_token,
    token,
    code,
    vp_token;

    companion object {
        fun getResponseTypeString(vararg types: ResponseType) = types.joinToString(" ") { it.name }
    }
}
