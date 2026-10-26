package id.walt.verifier2.data

import id.walt.credentials.presentations.formats.VerifiablePresentation
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

/**
 * `presented_presentations` is a list of presentations per query id.
 *
 * Sessions stored before that change keep one presentation object per query id. Those still load,
 * as a one-element list. New sessions are written as arrays.
 */
internal object PresentedPresentationsSerializer : KSerializer<Map<String, List<VerifiablePresentation>>> {
    private val listSerializer = ListSerializer(VerifiablePresentation.serializer())
    private val mapSerializer = MapSerializer(String.serializer(), listSerializer)

    override val descriptor: SerialDescriptor = mapSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Map<String, List<VerifiablePresentation>>) {
        mapSerializer.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): Map<String, List<VerifiablePresentation>> {
        val input = decoder as? JsonDecoder
            ?: return mapSerializer.deserialize(decoder)
        val element = input.decodeJsonElement()
        if (element !is JsonObject) {
            throw SerializationException("presented_presentations must be an object keyed by query id")
        }
        return element.mapValues { (_, value) ->
            when (value) {
                is JsonArray -> value.map {
                    input.json.decodeFromJsonElement(VerifiablePresentation.serializer(), it)
                }
                is JsonObject -> listOf(
                    input.json.decodeFromJsonElement(VerifiablePresentation.serializer(), value),
                )
                else -> throw SerializationException(
                    "presented_presentations values must be a presentation or a list of presentations",
                )
            }
        }
    }
}
