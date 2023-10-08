package id.walt.core.crypto.utils

import io.ipfs.multibase.Multibase

actual object MultiBaseUtils {

    actual fun convertRawKeyToMultiBase58Btc(key: ByteArray, code: UInt): String {
        val codeVarInt = MultiCodecUtils.UVarInt(code)
        val multicodecAndRawKey = ByteArray(key.size + codeVarInt.length)
        codeVarInt.bytes.copyInto(multicodecAndRawKey)
        key.copyInto(multicodecAndRawKey, codeVarInt.length)
        return encodeMultiBase58Btc(multicodecAndRawKey)
    }

    actual fun convertMultiBase58BtcToRawKey(mb: String): ByteArray {
        val bytes = decodeMultiBase58Btc(mb)
        val code = MultiCodecUtils.UVarInt.fromBytes(bytes)
        return bytes.drop(code.length).toByteArray()
    }

    actual fun decodeMultiBase58Btc(mb: String): ByteArray = Multibase.decode(mb)

    private fun encodeMultiBase58Btc(byteArray: ByteArray): String =
        Multibase.encode(Multibase.Base.Base58BTC, byteArray)
}