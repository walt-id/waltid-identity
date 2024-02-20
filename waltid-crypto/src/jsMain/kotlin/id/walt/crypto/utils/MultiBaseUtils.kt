package id.walt.crypto.utils

import io.ktor.utils.io.core.*
import multibase
import org.khronos.webgl.Uint8Array

@ExperimentalJsExport
@JsExport
actual object MultiBaseUtils {
    actual fun convertRawKeyToMultiBase58Btc(key: ByteArray, code: UInt): String {
        val codeVarInt = MultiCodecUtils.UVarInt(code)
        val multicodecAndRawKey = ByteArray(key.size + codeVarInt.length)
        codeVarInt.bytes.copyInto(multicodecAndRawKey)
        key.copyInto(multicodecAndRawKey, codeVarInt.length)
        return multibase.encode("base58btc", multicodecAndRawKey).decodeToString()
    }

    actual fun convertMultiBase58BtcToRawKey(mb: String): ByteArray {
        val bytes = decodeMultiBase58Btc(mb)
        val code = MultiCodecUtils.UVarInt.fromBytes(bytes)
        return bytes.drop(code.length).toByteArray()
    }

    actual fun decodeMultiBase58Btc(mb: String): ByteArray {
        return multibase.decode(Uint8Array(mb.toByteArray().toTypedArray()))
    }
}