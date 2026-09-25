package id.walt.openid4vci.mdoc

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Serializable(with = MsoDataSerializer::class)
data class MsoData(
    val validFrom: String? = null,
    val validUntil: String? = null,
    val expectedUpdate: String? = null,
    val expectedUpdateCleared: Boolean = false,
) {
    fun merge(override: MsoData?): MsoData {
        if (override == null) return this
        override.requireNonBlankFields()
        return MsoData(
            validFrom = override.validFrom ?: validFrom,
            validUntil = override.validUntil ?: validUntil,
            expectedUpdate = when {
                override.expectedUpdateCleared -> null
                override.expectedUpdate != null -> override.expectedUpdate
                else -> expectedUpdate
            },
        )
    }

    fun isEmpty(): Boolean =
        validFrom == null && validUntil == null && expectedUpdate == null && !expectedUpdateCleared

    fun requireNonBlankFields() {
        requireNonBlank("validFrom", validFrom)
        requireNonBlank("validUntil", validUntil)
        requireNonBlank("expectedUpdate", expectedUpdate)
    }

    companion object {
        fun requireNonBlank(fieldName: String, value: String?) {
            if (value != null && value.isBlank()) {
                throw IllegalArgumentException("msoData.$fieldName must not be blank")
            }
        }
    }
}

object MsoDataSerializer : KSerializer<MsoData> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MsoData") {
        element("validFrom", String.serializer().nullable.descriptor, isOptional = true)
        element("validUntil", String.serializer().nullable.descriptor, isOptional = true)
        element("expectedUpdate", String.serializer().nullable.descriptor, isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: MsoData) {
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(
                buildJsonObject {
                    value.validFrom?.let { put("validFrom", it) }
                    value.validUntil?.let { put("validUntil", it) }
                    when {
                        value.expectedUpdateCleared -> put("expectedUpdate", JsonNull)
                        value.expectedUpdate != null -> put("expectedUpdate", value.expectedUpdate)
                    }
                }
            )
            return
        }
        encoder.encodeStructure(descriptor) {
            encodeNullableSerializableElement(descriptor, 0, String.serializer(), value.validFrom)
            encodeNullableSerializableElement(descriptor, 1, String.serializer(), value.validUntil)
            encodeNullableSerializableElement(descriptor, 2, String.serializer(), value.expectedUpdate)
        }
    }

    override fun deserialize(decoder: Decoder): MsoData {
        if (decoder is JsonDecoder) {
            return deserializeJson(decoder.decodeJsonElement().jsonObject)
        }
        return decoder.decodeStructure(descriptor) {
            var validFrom: String? = null
            var validUntil: String? = null
            var expectedUpdate: String? = null
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> validFrom = decodeNullableSerializableElement(descriptor, 0, String.serializer().nullable)
                    1 -> validUntil = decodeNullableSerializableElement(descriptor, 1, String.serializer().nullable)
                    2 -> expectedUpdate = decodeNullableSerializableElement(descriptor, 2, String.serializer().nullable)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            MsoData(validFrom = validFrom, validUntil = validUntil, expectedUpdate = expectedUpdate)
                .also { it.requireNonBlankFields() }
        }
    }

    private fun deserializeJson(obj: JsonObject): MsoData {
        val expectedUpdateElement = obj["expectedUpdate"]
        return MsoData(
            validFrom = obj.requiredNonBlankString("validFrom"),
            validUntil = obj.requiredNonBlankString("validUntil"),
            expectedUpdate = obj.requiredNonBlankString("expectedUpdate"),
            expectedUpdateCleared = expectedUpdateElement is JsonNull,
        )
    }

    private fun JsonObject.requiredNonBlankString(fieldName: String): String? {
        val element = this[fieldName] ?: return null
        if (element is JsonNull) return null
        val content = (element as? JsonPrimitive)?.contentOrNull
            ?: throw SerializationException("msoData.$fieldName must be a string")
        if (content.isBlank()) {
            throw SerializationException("msoData.$fieldName must not be blank")
        }
        return content
    }
}
