package id.walt.vical.serializers

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A private surrogate class to assist with CBOR tagging.
 * kotlinx.serialization-cbor applies the @CborTag annotation during serialization.
 * We use this to correctly tag the date-time string with tag 0.
 */
@Serializable
@JvmInline
@OptIn(ExperimentalSerializationApi::class)
private value class CborTaggedDateTimeString(@CborLabel(0) val rfc3339String: String)

/**
 * Custom serializer for kotlin.time.Instant to handle CBOR tag 0 (tdate).
 * ISO/IEC 18013-5 specifies that 'tdate' elements are encoded as a text string
 * (RFC 3339 format) with CBOR tag 0.
 *
 * This serializer works by delegating to a compiler-generated serializer for the
 * CborTaggedDateTimeString surrogate, which handles the tagging automatically.
 */
@OptIn(ExperimentalTime::class)
object VicalInstantSerializer : KSerializer<Instant> {
    private val surrogateSerializer = CborTaggedDateTimeString.serializer()

    override val descriptor: SerialDescriptor = surrogateSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeSerializableValue(surrogateSerializer, CborTaggedDateTimeString(value.toString()))
    }

    override fun deserialize(decoder: Decoder): Instant {
        val surrogate = decoder.decodeSerializableValue(surrogateSerializer)
        return Instant.parse(surrogate.rfc3339String)
    }
}
