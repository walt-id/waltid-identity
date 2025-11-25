@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.handover

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborArray

/**
 * Represents the `OpenID4VPHandover` structure, which is the `Handover` component of a `SessionTranscript`
 * for an mdoc presentation using the final OpenID4VP 1.0 flow.
 *
 * This structure is serialized as a CBOR array.
 *
 * @see OpenID for Verifiable Presentations 1.0, Appendix B.2.6.1
 *
 * @property identifier A fixed string with the value "OpenID4VPHandover" to identify the structure's type.
 * @property infoHash The SHA-256 hash of the CBOR-encoded [OpenID4VPHandoverInfo] structure.
 */
@Serializable
@CborArray
data class OpenID4VPHandover(
    val identifier: String, // "OpenID4VPHandover"
    @ByteString
    val infoHash: ByteArray
) {
    init {
        // Enforce the constant value for the type identifier as per the specification.
        require(identifier in listOf("OpenID4VPHandover", "OpenID4VPDCAPIHandover")) {
            "The identifier for OpenID4VPHandover must be 'OpenID4VPHandover' or other valid identifier. Instead, was: $identifier"
        }
    }

    /**
     * Note: `equals` and `hashCode` are manually overridden to ensure content-based equality
     * for the `infoHash` byte array.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OpenID4VPHandover) return false

        if (identifier != other.identifier) return false
        if (!infoHash.contentEquals(other.infoHash)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = identifier.hashCode()
        result = 31 * result + infoHash.contentHashCode()
        return result
    }
}

