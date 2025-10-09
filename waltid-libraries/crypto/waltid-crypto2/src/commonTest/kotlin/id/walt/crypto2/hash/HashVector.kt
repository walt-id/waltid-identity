package id.walt.crypto2.hash

import kotlinx.serialization.Serializable
import okio.ByteString
import okio.ByteString.Companion.decodeHex

@Serializable
internal data class HashVector(
    val algorithm: String,
    val name: String,
    val messageHex: String,
    val digestHex: String,
) {

    val messageBytes: ByteArray get() = messageHex.decodeHex().toByteArray()
    val messageByteString: ByteString get() = messageHex.decodeHex()
    val digestBytes: ByteArray get() = digestHex.decodeHex().toByteArray()
    val digestByteString: ByteString get() = digestHex.decodeHex()

}
