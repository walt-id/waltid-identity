@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.handover

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborArray

/**
 * Represents the `OpenID4VPHandoverInfo` structure. This is an intermediate data structure
 * that contains the key parameters of an OID4VP transaction.
 *
 * The CBOR-encoded representation of this object is hashed with SHA-256 to produce the
 * `infoHash` used in the [OpenID4VPHandover] structure.
 *
 * @see OpenID for Verifiable Presentations 1.0, Appendix B.2.6.1
 *
 * @property clientId The `client_id` from the authorization request, identifying the verifier.
 * @property nonce The `nonce` from the authorization request, used for replay protection.
 * @property jwkThumbprint The SHA-256 thumbprint of the verifier's public key used for response encryption.
 * This MUST be null if the response is not encrypted.
 * @property responseUri The `response_uri` or `redirect_uri` from the authorization request,
 * identifying the endpoint for the response.
 */
@Serializable
@CborArray
data class OpenID4VPHandoverInfo(
    val clientId: String,

    val nonce: String,
    @ByteString
    val jwkThumbprint: ByteArray?, // Null if response is not encrypted
    val responseUri: String?
): BaseHandoverInfo {
    /**
     * Note: `equals` and `hashCode` are manually overridden because the default implementation for a
     * `data class` uses reference equality for `ByteArray` properties. This override ensures
     * content-based equality.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OpenID4VPHandoverInfo) return false

        if (clientId != other.clientId) return false
        if (nonce != other.nonce) return false
        if (jwkThumbprint != null) {
            if (other.jwkThumbprint == null) return false
            if (!jwkThumbprint.contentEquals(other.jwkThumbprint)) return false
        } else if (other.jwkThumbprint != null) return false
        if (responseUri != other.responseUri) return false

        return true
    }

    override fun hashCode(): Int {
        var result = clientId.hashCode()
        result = 31 * result + nonce.hashCode()
        result = 31 * result + (jwkThumbprint?.contentHashCode() ?: 0)
        result = 31 * result + (responseUri?.hashCode() ?: 0)
        return result
    }
}
