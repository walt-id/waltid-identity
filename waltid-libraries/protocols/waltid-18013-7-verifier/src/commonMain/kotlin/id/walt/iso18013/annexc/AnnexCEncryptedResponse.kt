@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.iso18013.annexc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborArray

/**
 * Annex C EncryptedResponse = ["dcapi", {enc, cipherText}]
 *
 * @see ISO/IEC TS 18013-7:2024(en), Annex C
 */
@Serializable
@CborArray
data class AnnexCEncryptedResponse(
    val type: String,
    val response: AnnexCEncryptedResponseData,
) {
    init {
        require(type == "dcapi") { "EncryptedResponse.type must be \"dcapi\"" }
    }
}

@Serializable
data class AnnexCEncryptedResponseData(
    @ByteString
    @SerialName("enc")
    val enc: ByteArray,

    @ByteString
    @SerialName("cipherText")
    val cipherText: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnnexCEncryptedResponseData) return false
        if (!enc.contentEquals(other.enc)) return false
        if (!cipherText.contentEquals(other.cipherText)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = enc.contentHashCode()
        result = 31 * result + cipherText.contentHashCode()
        return result
    }
}

