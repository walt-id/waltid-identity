@JsModule("crypto")
@JsNonModule
external object crypto {
    fun sign(algorithm: String?, data: ByteArray, key: String): ByteArray
    fun verify(algorithm: String?, data: ByteArray, key: String, signature: ByteArray): Boolean
}
