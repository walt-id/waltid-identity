package id.walt.mdoc.objects.elements

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

object GenericAnySerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("GenericAny")

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Any) {
        when (value) {
            is String -> encoder.encodeString(value)
            is Int -> encoder.encodeInt(value)
            is UInt -> encoder.encodeSerializableValue(UInt.serializer(), value)
            is Long -> encoder.encodeLong(value)
            is Boolean -> encoder.encodeBoolean(value)
            is ByteArray -> encoder.encodeSerializableValue(ByteArraySerializer(), value)
            is LocalDate -> encoder.encodeSerializableValue(LocalDate.serializer(), value)
            is Instant -> encoder.encodeSerializableValue(InstantStringSerializer, value)
            is List<*> -> encoder.encodeSerializableValue(
                ListSerializer(GenericAnySerializer),
                value as List<Any>
            )
            is Map<*, *> -> encoder.encodeSerializableValue(
                MapSerializer(String.serializer(), GenericAnySerializer),
                value as Map<String, Any>
            )
            else -> throw IllegalArgumentException("Dynamic serialization unsupported for type: ${value::class.simpleName}")
        }
    }

    override fun deserialize(decoder: Decoder): Any {
        throw NotImplementedError("Dynamic decoding is not supported here. Used for encoding only.")
    }
}
