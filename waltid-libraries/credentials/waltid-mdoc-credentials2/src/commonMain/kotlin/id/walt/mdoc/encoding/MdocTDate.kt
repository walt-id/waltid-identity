package id.walt.mdoc.encoding

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.CborDecoder
import kotlinx.serialization.cbor.CborEncoder
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

/**
 * RFC 3339 date-time formatting for CBOR tag 0 (tdate) per ISO/IEC 18013-5 and RFC 8949.
 *
 * Fractional seconds are not permitted in mdoc date-time strings; values are truncated to whole seconds.
 * Enforces the RFC 3339 four-digit year range for all callers, including MSO validity timestamps
 * and schema DATETIME fields. Date-only (full-date/tag-1004) encoding uses a separate path.
 *
 * Incoming MSO timestamps are validated in [MdocTDateMode.STRICT] by default: CBOR tag 0, whole seconds,
 * and a UTC `Z` offset. [parseMdocTDate] with [MdocTDateMode.LENIENT] is the explicit compatibility path
 * for credentials that used fractional seconds or a numeric offset; Instant conversion still loses that
 * original representation, so it must not be the default verification path.
 */
fun Instant.toMdocTDateString(): String =
    Instant.fromEpochSeconds(epochSeconds).toString().also {
        require(it.length == 20) { "mdoc timestamps require a four-digit year" }
    }

/**
 * Normalizes an RFC 3339 date-time string to second-level precision without fractional seconds.
 */
fun String.toMdocTDateString(): String =
    parseMdocTDate(this, MdocTDateMode.LENIENT).toMdocTDateString()

enum class MdocTDateMode {
    STRICT,
    LENIENT,
}

private val STRICT_TDATE = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$""")

fun parseMdocTDate(text: String, mode: MdocTDateMode = MdocTDateMode.STRICT): Instant {
    if (mode == MdocTDateMode.STRICT && !STRICT_TDATE.matches(text)) {
        throw IllegalArgumentException(
            "MSO tdate must be a whole-second UTC timestamp ending in Z, got: $text"
        )
    }
    return try {
        Instant.fromEpochSeconds(Instant.parse(text).epochSeconds)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("MSO tdate is not a valid ISO-8601 timestamp: $text", e)
    }
}

/**
 * Serializes [Instant] values as CBOR tag-0 date-time strings without fractional seconds.
 * Decoding uses strict tag and lexical checks; use [parseMdocTDate] with [MdocTDateMode.LENIENT]
 * only for an explicit compatibility import path.
 */
@OptIn(ExperimentalSerializationApi::class)
object MdocTDateInstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MdocTDateInstant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        val text = value.toMdocTDateString()
        if (encoder is CborEncoder) {
            encoder.encodeSerializableValue(CborElement.serializer(), CborString(text, 0uL))
        } else {
            encoder.encodeString(text)
        }
    }

    override fun deserialize(decoder: Decoder): Instant {
        if (decoder is CborDecoder) {
            val element = decoder.decodeSerializableValue(CborElement.serializer())
            val text = (element as? CborString)?.value
                ?: throw SerializationException("MSO tdate must be a CBOR text string")
            if (0uL !in element.tags) {
                throw SerializationException(
                    "MSO tdate requires CBOR tag 0, got tags=${element.tags}"
                )
            }
            return try {
                parseMdocTDate(text, MdocTDateMode.STRICT)
            } catch (e: IllegalArgumentException) {
                throw SerializationException(e.message, e)
            }
        }
        return parseMdocTDate(decoder.decodeString(), MdocTDateMode.STRICT)
    }
}
