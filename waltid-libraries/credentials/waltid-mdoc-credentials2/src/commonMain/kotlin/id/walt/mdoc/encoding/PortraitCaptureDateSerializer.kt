package id.walt.mdoc.encoding

import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.CborEncoder
import kotlinx.serialization.cbor.CborDecoder
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Keeps the legacy date-only model/JSON contract and emits midnight UTC for CBOR. */
@OptIn(ExperimentalSerializationApi::class)
internal object PortraitCaptureDateSerializer : KSerializer<LocalDate> {
    override val descriptor = LocalDate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: LocalDate) {
        if (encoder is CborEncoder) {
            encoder.encodeSerializableValue(CborElement.serializer(), PortraitCaptureValue.DateOnly(value).toCborString())
        } else encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDate {
        val value = if (decoder is CborDecoder) {
            val element = decoder.decodeSerializableValue(CborElement.serializer())
            require(element is CborString) { "portrait_capture_date must be a string" }
            element.value
        } else decoder.decodeString()
        return PortraitCaptureValue.parse(value).toLocalDate()
    }
}

/** Readers accept old date-only values without rewriting the authoritative signed bytes. */
internal object PortraitCaptureTimestampSerializer : TransformingSerializerTemplate<Any, String>(
    parent = String.serializer(),
    encodeAs = { PortraitCaptureValue.fromLegacyValue(it).toTimestampString() },
    decodeAs = { PortraitCaptureValue.parse(it).toLegacyValue() },
    serialName = "PortraitCaptureTimestamp",
)
