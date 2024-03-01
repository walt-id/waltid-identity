import org.khronos.webgl.Uint8Array

@JsModule("multibase")
@JsNonModule
external object multibase {
    fun encode(base: String, data: ByteArray): ByteArray
    fun decode(data: Uint8Array): ByteArray
}