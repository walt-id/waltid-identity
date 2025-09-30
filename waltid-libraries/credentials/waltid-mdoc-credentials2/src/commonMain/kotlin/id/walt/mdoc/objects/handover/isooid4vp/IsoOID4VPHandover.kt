package id.walt.mdoc.objects.handover.isooid4vp

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborArray

/**
 * Represents the `OID4VPHandover` structure, which is the `Handover` component of a `SessionTranscript`
 * for an mdoc presentation using a specific version of the OID4VP flow.
 *
 * ### ⚠️ Deprecation Notice & Context
 * This structure is defined in **ISO/IEC TS 18013-7:2024(en), Annex B.4.4**. However, this mechanism
 * has been **superseded** in the final **OpenID for Verifiable Presentations 1.0** specification.
 * The newer specification (in Appendix B.2.6.1) uses a simpler `OpenID4VPHandoverInfo` structure that does
 * not require separate hashing of the `clientId` and `responseUri`.
 *
 * This class should be used for compatibility with systems based on the ISO/IEC TS but is considered
 * deprecated for new implementations, which should favor the final OID4VP 1.0 standard.
 *
 * @see ISO/IEC TS 18013-7:2024(en), B.4.4 (Session Transcript)
 *
 * @property clientIdHash The SHA-256 hash of the CBOR-encoded [ClientIdToHash] structure. It binds the session to the verifier's client identifier.
 * @property responseUriHash The SHA-256 hash of the CBOR-encoded [ResponseUriToHash] structure. It binds the session to the specific response endpoint for the transaction.
 * @property nonce The `nonce` value from the Authorization Request. It provides replay protection for the transaction.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@CborArray
@Deprecated("Legacy 18013-7 handling")
data class IsoOID4VPHandover(
    @ByteString
    /** [ClientIdToHash] SHA-256 hash */
    val clientIdHash: ByteArray,

    @ByteString
    /** [ResponseUriToHash] SHA-256 hash */
    val responseUriHash: ByteArray,

    /** The `nonce` Authorization Request parameter from the Authorization Request Object */
    val nonce: String
) {
    /**
     * Note: `equals` and `hashCode` are manually overridden because the default implementation for a
     * `data class` uses reference equality for `ByteArray` properties. This override ensures
     * content-based equality for the hashes.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IsoOID4VPHandover) return false

        if (!clientIdHash.contentEquals(other.clientIdHash)) return false
        if (!responseUriHash.contentEquals(other.responseUriHash)) return false
        if (nonce != other.nonce) return false

        return true
    }

    override fun hashCode(): Int {
        var result = clientIdHash.contentHashCode()
        result = 31 * result + responseUriHash.contentHashCode()
        result = 31 * result + nonce.hashCode()
        return result
    }
}
