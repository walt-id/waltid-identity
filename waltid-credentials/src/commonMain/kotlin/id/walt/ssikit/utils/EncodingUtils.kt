package id.walt.ssikit.utils

expect object EncodingUtils {
    fun urlEncode(path: String): String
    fun urlDecode(path: String): String
}