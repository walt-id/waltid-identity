package id.walt.crypto

import id.walt.crypto.keys.*

abstract class IosKey: Key() {
    abstract suspend fun signJws(bodyJson: ByteArray, headersJson: ByteArray): String
}
class IosKeys {

    companion object {
        @Throws(Exception::class)
        fun load(kid: String, type: KeyType): IosKey = when (type) {
            KeyType.secp256r1 -> P256Key.load(kid)
            else -> TODO("Not implemented")
        }

        @Throws(Exception::class)
        fun delete(kid: String, type: KeyType) = when (type) {
            KeyType.secp256r1 -> P256Key.delete(kid)
            else -> TODO("Not implemented")
        }

        @Throws(Exception::class)
        fun load(kid: String): IosKey? = when {
            P256Key.exist(kid) == true -> P256Key.load(kid)
            else -> null
        }
    }
}

// utility functions for swift
fun String.ExportedToByteArray(
    startIndex: Int, endIndex: Int, throwOnInvalidSequence: Boolean
): ByteArray {
    return this.encodeToByteArray()
}

fun ByteArray.ExportedToString(
): String {
    return this.decodeToString()
}