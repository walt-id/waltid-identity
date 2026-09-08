@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.handover.NFCHandover
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborObjectAsArray
import kotlinx.serialization.cbor.ValueTags
import kotlinx.serialization.decodeFromByteArray

/** Decodes the three positional elements used by QR, conventional NFC and provisional NFCv2. */
internal fun decodeProximitySessionTranscript(exactBytes: ByteArray): SessionTranscript {
    val encoded = coseCompliantCbor.decodeFromByteArray<ByteArray>(exactBytes)
    val value = coseCompliantCbor.decodeFromByteArray<ProximityTranscript>(encoded)
    return value.handover?.let {
        SessionTranscript.forNfc(value.deviceEngagement, value.readerKey, it)
    } ?: SessionTranscript.forQr(value.deviceEngagement, value.readerKey)
}

// SessionTranscript's general model uses omitted defaults to encode several handover shapes.
// Its generated positional decoder cannot decode NFC's third element as a handover array.
// This projection has exactly the proximity wire shape, including the explicit QR null.
@Serializable
@CborObjectAsArray
private data class ProximityTranscript(
    @ByteString @ValueTags(24u) val deviceEngagement: ByteArray,
    @ByteString @ValueTags(24u) val readerKey: ByteArray,
    val handover: NFCHandover?,
)
