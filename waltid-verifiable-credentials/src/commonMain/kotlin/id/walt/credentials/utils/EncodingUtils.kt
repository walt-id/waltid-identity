package id.walt.credentials.utils

expect object EncodingUtils {
    fun urlEncode(path: String): String
    fun urlDecode(path: String): String
}
