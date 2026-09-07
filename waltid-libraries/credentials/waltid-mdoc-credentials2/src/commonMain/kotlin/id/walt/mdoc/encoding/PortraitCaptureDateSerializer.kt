package id.walt.mdoc.encoding

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.CborEncoder
import kotlinx.serialization.cbor.CborDecoder
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

/** Keeps the legacy date-only model/JSON contract and emits midnight UTC for CBOR. */
@OptIn(ExperimentalSerializationApi::class)
internal object PortraitCaptureDateSerializer : KSerializer<LocalDate> {
    override val descriptor = LocalDate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: LocalDate) {
        if (encoder is CborEncoder) {
            encoder.encodeSerializableValue(CborElement.serializer(), CborString(value.atStartOfDayIn(TimeZone.UTC).toMdocTDateString(), 0u))
        } else encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDate {
        val value = if (decoder is CborDecoder) {
            val element = decoder.decodeSerializableValue(CborElement.serializer())
            require(element is CborString) { "portrait_capture_date must be a string" }
            element.value
        } else decoder.decodeString()
        if (value.length == 10) return LocalDate.parse(value)
        val instant = Instant.parse(value)
        val date = instant.toLocalDateTime(TimeZone.UTC).date
        require(date.atStartOfDayIn(TimeZone.UTC) == instant) {
            "The legacy LocalDate model cannot retain a capture time; use namespace data for full timestamps"
        }
        return date
    }
}

/** Readers accept old date-only values without rewriting the authoritative signed bytes. */
internal object PortraitCaptureTimestampSerializer : TransformingSerializerTemplate<Any, String>(
    parent = String.serializer(),
    encodeAs = {
        when (it) {
            is LocalDate -> it.atStartOfDayIn(TimeZone.UTC).toMdocTDateString()
            is Instant -> it.toMdocTDateString()
            else -> error("Expected a portrait capture date or timestamp")
        }
    },
    decodeAs = {
        if (it.length == 10) LocalDate.parse(it) else Instant.parse(it)
    },
    serialName = "PortraitCaptureTimestamp",
)
