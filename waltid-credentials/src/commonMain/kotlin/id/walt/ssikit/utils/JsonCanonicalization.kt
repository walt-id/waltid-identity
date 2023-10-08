package id.walt.ssikit.utils

expect object JsonCanonicalization {
    fun getCanonicalBytes(json: String): ByteArray
    fun getCanonicalString(json: String): String
}