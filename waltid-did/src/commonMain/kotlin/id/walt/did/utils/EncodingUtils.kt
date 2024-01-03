package id.walt.did.utils

expect object EncodingUtils {
    fun urlEncode(path: String): String
    fun urlDecode(path: String): String
    fun base64Encode(data: ByteArray): String
    fun base64Decode(data: String): ByteArray
}
