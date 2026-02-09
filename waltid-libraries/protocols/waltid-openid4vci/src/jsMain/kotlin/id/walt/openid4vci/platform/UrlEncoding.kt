package id.walt.openid4vci.platform

actual fun urlEncode(value: String): String =
    js("encodeURIComponent(value)") as String
