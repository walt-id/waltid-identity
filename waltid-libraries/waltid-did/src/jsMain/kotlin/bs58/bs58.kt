import org.khronos.webgl.Uint8Array

@JsModule("bs58")
@JsNonModule
external object bs58 {
    fun encode(data: Uint8Array): String
    fun decode(base58String: String): Uint8Array
}