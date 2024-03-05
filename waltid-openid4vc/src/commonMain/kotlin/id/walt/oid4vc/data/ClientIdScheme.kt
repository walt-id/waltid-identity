package id.walt.oid4vc.data

enum class ClientIdScheme(val value: String) {
    pre_registered("pre-registered"),
    redirect_uri("redirect_uri");

    companion object {
        fun fromValue(value: String): ClientIdScheme? {
            return entries.find { it.value == value }
        }
    }
}
