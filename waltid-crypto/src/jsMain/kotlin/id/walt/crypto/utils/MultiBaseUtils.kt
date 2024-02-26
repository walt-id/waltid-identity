package id.walt.crypto.utils

@OptIn(ExperimentalJsExport::class)
@JsExport
actual object MultiBaseUtils {
    actual fun convertRawKeyToMultiBase58Btc(key: ByteArray, code: UInt): String = TODO("Not yet implemented")

    actual fun convertMultiBase58BtcToRawKey(mb: String): ByteArray = TODO("Not yet implemented")

    actual fun decodeMultiBase58Btc(mb: String): ByteArray = TODO("Not yet implemented")
}
