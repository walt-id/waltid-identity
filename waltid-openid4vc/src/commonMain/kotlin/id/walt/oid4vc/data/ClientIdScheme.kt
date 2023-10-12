package id.walt.oid4vc.data

enum class ClientIdScheme(val value: String) {
    pre_registered("pre-registered"),
    redirect_uri("redirect_uri");

    companion object {
        fun fromValue(value: String): ClientIdScheme? {
            return ClientIdScheme.values().find { it.value == value }
        }
    }
}
