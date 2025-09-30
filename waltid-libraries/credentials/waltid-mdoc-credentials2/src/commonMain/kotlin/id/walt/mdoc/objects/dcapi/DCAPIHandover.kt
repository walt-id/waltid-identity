package id.walt.mdoc.objects.dcapi

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborArray

/**
 * Represents the `Handover` structure for a `SessionTranscript` when using a
 * Digital Credentials API (DCAPI) for engagement and presentation.
 *
 * This structure is a key component for ensuring the integrity and context of the
 * mdoc presentation by binding it to the specific transaction. It is encoded as a
 * CBOR array.
 *
 * @param type The type of handover, indicating the protocol context.
 * @param dcapiInfoHash The SHA-256 hash of the CBOR-encoded `DCAPIInfo` or `OpenID4VPDCAPIHandoverInfo` structure. This binds the session to the specific request parameters like origin, nonce, and public keys.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@CborArray
data class DCAPIHandover(
    val type: HandoverType,
    /**
     * `dcapiInfoHash` shall contain the SHA-256 hash of the
     * CBOR encoded [DCAPIInfo] structure.
     * */
    @ByteString
    val dcapiInfoHash: ByteArray,
) {

    /**
     * Defines the type of handover mechanism being used for a session transcript in an
     * over-the-internet flow.
     *
     * This corresponds to the first element in the Handover CBOR array.
     */
    @Suppress("EnumEntryName")
    @Serializable
    enum class HandoverType {
        OpenID4VPDCAPIHandover,
        dcapi
    }

    /**
     * Note: `equals` and `hashCode` are manually overridden because the default implementation for a
     * `data class` uses reference equality for `ByteArray` properties. This override ensures
     * content-based equality for the hash.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DCAPIHandover

        if (type != other.type) return false
        if (!dcapiInfoHash.contentEquals(other.dcapiInfoHash)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + dcapiInfoHash.contentHashCode()
        return result
    }

}
