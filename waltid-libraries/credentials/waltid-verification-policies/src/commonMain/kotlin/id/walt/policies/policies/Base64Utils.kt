package id.walt.policies.policies

object Base64Utils {
    fun decode(base64: String): ByteArray = Base64.getDecoder().decode(base64)
}