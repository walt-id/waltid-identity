@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.handover

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.dcapi.DCAPIHandover
import id.walt.mdoc.objects.sha256
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborObjectAsArray

/** * Helper class to structure the data before hashing.
 * This corresponds to the `dcapiInfo` structure in the spec.
 */
@Serializable
@CborObjectAsArray
data class AnnexCDcapiHandoverInfo(
    /** The raw Base64url string sent in the JS request 'encryptionInfo' field */
    val base64EncryptionInfo: String,

    /** The origin string (e.g. "https://example.com") - NO trailing slash! */
    val origin: String
) {
    companion object {
        /**
         * `SessionTranscript = [ null, null, ["dcapi", sha256(cbor(dcapiInfo))] ]`
         *
         * Wallet and verifier both derive this transcript from the same two inputs and never
         * transmit it, so a single construction is what keeps their HPKE contexts and reader-auth
         * payloads byte-identical.
         *
         * @see ISO/IEC TS 18013-7:2024(en), Annex C.5
         */
        fun sessionTranscript(base64EncryptionInfo: String, origin: String): SessionTranscript =
            SessionTranscript.forDcApi(
                DCAPIHandover(
                    type = DCAPIHandover.HandoverType.dcapi,
                    dcapiInfoHash = coseCompliantCbor.encodeToByteArray(
                        serializer(),
                        AnnexCDcapiHandoverInfo(base64EncryptionInfo, origin),
                    ).sha256(),
                )
            )

        /** The HPKE `info` for an Annex C response: `CBOR(SessionTranscript)`. */
        fun hpkeInfo(transcript: SessionTranscript): ByteArray =
            coseCompliantCbor.encodeToByteArray(SessionTranscript.serializer(), transcript)

        /** The HPKE `info` for the transcript these two inputs derive. */
        fun hpkeInfo(base64EncryptionInfo: String, origin: String): ByteArray =
            hpkeInfo(sessionTranscript(base64EncryptionInfo, origin))
    }
}
