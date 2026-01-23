@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.handover

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborArray

@Serializable
@CborArray
data class OpenID4VPDCAPIHandoverInfo(
    /** verifier's origin - without 'origin:' prefix (e.g., https://verifier.example.com) */
    val origin: String?,

    /** nonce of authorization request*/
    val nonce: String,

    /** SHA-256 thumbprint of the ephemeral public encryption key of client_metadata */
    @ByteString
    val jwkThumbprint: ByteArray?
): BaseHandoverInfo {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OpenID4VPDCAPIHandoverInfo) return false

        if (origin != other.origin) return false
        if (nonce != other.nonce) return false
        if (!jwkThumbprint.contentEquals(other.jwkThumbprint)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = origin.hashCode()
        result = 31 * result + nonce.hashCode()
        result = 31 * result + jwkThumbprint.contentHashCode()
        return result
    }
}
