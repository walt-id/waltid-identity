package id.walt.crypto2.hash

import kotlinx.serialization.Serializable
import okio.ByteString.Companion.decodeHex

@Serializable
internal data class HashVector(
    val algorithm: String,
    val name: String,
    val messageHex: String,
    val digestHex: String,
) {

    val messageBytes
        get() = messageHex.decodeHex().toByteArray()

    val messageByteString
        get() = messageHex.decodeHex()

    val digestBytes
        get() = digestHex.decodeHex().toByteArray()

    val digestByteString
        get() = digestHex.decodeHex()

}
