import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

@JsModule("crypto")
@JsNonModule
external object crypto {
    fun sign(algorithm: String?, data: ByteArray, key: String): ByteArray
    fun verify(algorithm: String?, data: ByteArray, key: String, signature: ByteArray): Boolean
}

// An opaque type representing the JavaScript CryptoKey object
external class CryptoKey

// Represents the top-level `crypto` object in the browser
@JsName("crypto")
external object WebCrypto {
    val subtle: SubtleCrypto
}

// Represents the `crypto.subtle` object
external interface SubtleCrypto {
    // fun importKey(format, keyData, algorithm, extractable, keyUsages)
    fun importKey(
        format: String,
        keyData: ArrayBuffer,
        algorithm: dynamic,
        extractable: Boolean,
        keyUsages: Array<String>
    ): Promise<CryptoKey>

    // fun sign(algorithm, key, data)
    fun sign(
        algorithm: dynamic,
        key: CryptoKey,
        data: Uint8Array
    ): Promise<ArrayBuffer>

    // fun verify(algorithm, key, signature, data)
    fun verify(
        algorithm: dynamic,
        key: CryptoKey,
        signature: ArrayBuffer,
        data: Uint8Array
    ): Promise<Boolean>
}

