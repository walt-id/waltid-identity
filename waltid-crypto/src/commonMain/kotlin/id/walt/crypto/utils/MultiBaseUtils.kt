package id.walt.crypto.utils

expect object MultiBaseUtils {

    fun convertRawKeyToMultiBase58Btc(key: ByteArray, code: UInt): String

    fun convertMultiBase58BtcToRawKey(mb: String): ByteArray

    fun decodeMultiBase58Btc(mb: String): ByteArray
}