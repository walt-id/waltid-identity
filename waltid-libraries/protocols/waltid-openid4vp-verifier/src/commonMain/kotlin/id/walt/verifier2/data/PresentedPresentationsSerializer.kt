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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * `presented_presentations` is a list of presentations per query id.
 *
 * Sessions stored before that change keep one presentation object per query id. Those still load,
 * as a one-element list. New sessions are written as arrays.
 */
internal object PresentedPresentationsSerializer : KSerializer<Map<String, List<VerifiablePresentation>>> {
    private val mapSerializer = MapSerializer(String.serializer(), PresentationsOfQuerySerializer)

    override val descriptor: SerialDescriptor = mapSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Map<String, List<VerifiablePresentation>>) =
        mapSerializer.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): Map<String, List<VerifiablePresentation>> =
        mapSerializer.deserialize(decoder)
}

/**
 * The presentations of one query id: a list, or a single presentation object as stored by older versions.
 *
 * Telling the two apart needs a look at the stored value before decoding it, which only the format can give:
 *
 * - **JSON** reads the value as a [JsonElement] first and decodes that.
 * - **BSON** (MongoDB's `bson-kotlinx` codec) must not take that route. Its decoder is a [JsonDecoder] too, but
 *   its BSON encoder ignores `@JsonClassDiscriminator`: a [VerifiablePresentation] is stored with `_t` where
 *   JSON has `type`, so decoding the element as JSON fails for every stored session. It also costs a second
 *   pass over what can be the largest field of a session. So BSON decodes the list natively, and only when the
 *   stored value is not an array - which its decoder checks before consuming anything - falls back to the
 *   single object.
 * - **Any other format** never stored the single-object form, and decodes the list.
 */
internal object PresentationsOfQuerySerializer : KSerializer<List<VerifiablePresentation>> {
    private val listSerializer = ListSerializer(VerifiablePresentation.serializer())

    override val descriptor: SerialDescriptor = listSerializer.descriptor

    override fun serialize(encoder: Encoder, value: List<VerifiablePresentation>) =
        listSerializer.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<VerifiablePresentation> = when {
        decoder.isBsonDecoder() -> decodeBson(decoder)
        decoder is JsonDecoder -> decodeJson(decoder)
        else -> listSerializer.deserialize(decoder)
    }

    private fun decodeJson(decoder: JsonDecoder): List<VerifiablePresentation> =
        when (val value = decoder.decodeJsonElement()) {
            is JsonArray -> decoder.json.decodeFromJsonElement(listSerializer, value)
            is JsonObject -> listOf(decoder.json.decodeFromJsonElement(VerifiablePresentation.serializer(), value))
            else -> throw SerializationException(
                "presented_presentations values must be a presentation or a list of presentations",
            )
        }

    private fun decodeBson(decoder: Decoder): List<VerifiablePresentation> = try {
        listSerializer.deserialize(decoder)
    } catch (notAList: SerializationException) {
        try {
            listOf(VerifiablePresentation.serializer().deserialize(decoder))
        } catch (notAPresentation: SerializationException) {
            notAPresentation.addSuppressed(notAList)
            throw notAPresentation
        }
    }
}

/** Whether [this] decodes BSON through MongoDB's `bson-kotlinx` codec. See [PresentationsOfQuerySerializer]. */
internal expect fun Decoder.isBsonDecoder(): Boolean
