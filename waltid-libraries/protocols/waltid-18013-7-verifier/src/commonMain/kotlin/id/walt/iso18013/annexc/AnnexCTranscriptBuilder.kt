@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.iso18013.annexc

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.dcapi.DCAPIHandover
import id.walt.mdoc.objects.sha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.encodeToByteArray

/**
 * Annex C dcapiInfo = [ Base64EncryptionInfoString, SerializedOriginString ]
 */
@Serializable
@CborArray
data class AnnexCDcApiInfo(
    val encryptionInfoB64: String,
    val serializedOrigin: String,
)

object AnnexCTranscriptBuilder {
    fun encodeDcApiInfo(encryptionInfoB64: String, origin: String): ByteArray =
        coseCompliantCbor.encodeToByteArray(AnnexCDcApiInfo.serializer(), AnnexCDcApiInfo(encryptionInfoB64, origin))

    fun computeDcApiInfoHash(encryptionInfoB64: String, origin: String): ByteArray =
        encodeDcApiInfo(encryptionInfoB64, origin).sha256()

    /**
     * SessionTranscript = [ null, null, ["dcapi", sha256(cbor(dcapiInfo))] ]
     */
    fun buildSessionTranscript(encryptionInfoB64: String, origin: String): SessionTranscript {
        val dcapiInfoHash = computeDcApiInfoHash(encryptionInfoB64, origin)
        val handover = DCAPIHandover(
            type = DCAPIHandover.HandoverType.dcapi,
            dcapiInfoHash = dcapiInfoHash
        )
        return SessionTranscript.forDcApi(handover)
    }

    /**
     * HPKE info = CBOR(SessionTranscript)
     */
    fun computeHpkeInfo(encryptionInfoB64: String, origin: String): ByteArray =
        coseCompliantCbor.encodeToByteArray(SessionTranscript.serializer(), buildSessionTranscript(encryptionInfoB64, origin))
}

