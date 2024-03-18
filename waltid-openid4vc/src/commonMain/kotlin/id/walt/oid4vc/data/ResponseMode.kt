package id.walt.oid4vc.data

enum class ResponseMode(val value: String) {
    Query("query"),
    Fragment("fragment"),
    FormPost("form_post"),
    DirectPost("direct_post"),
    Post("post");

    companion object {
        fun fromValue(value: String): ResponseMode? {
            return entries.find { it.value == value }
        }
    }
}
