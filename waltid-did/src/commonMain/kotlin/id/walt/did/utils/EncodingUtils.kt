package id.walt.did.utils

expect object EncodingUtils {
    fun urlEncode(path: String): String
    fun urlDecode(path: String): String
    fun base64Encode(data: ByteArray): String
    fun base64Decode(data: String): ByteArray
    fun base58Encode(byteArray: ByteArray): String
    fun base58Decode(base58String: String): ByteArray
    fun fromHexString(hexString: String): ByteArray
    fun toHexString(byteArray: ByteArray): String
}
