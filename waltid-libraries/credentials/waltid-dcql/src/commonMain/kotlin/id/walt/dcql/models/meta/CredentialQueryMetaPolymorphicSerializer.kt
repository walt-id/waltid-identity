package id.walt.dcql.models.meta

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

object CredentialQueryMetaPolymorphicSerializer : KSerializer<CredentialQueryMeta> {

    override val descriptor: SerialDescriptor =
        SerialDescriptor("CredentialQueryMeta", JsonElement.serializer().descriptor)

    override fun serialize(encoder: Encoder, value: CredentialQueryMeta) {
        // Delegate to the actual serializer of the concrete type
        when (value) {
            is NoMeta -> encoder.encodeSerializableValue(NoMeta.serializer(), value) // Will serialize as {}
            is W3cCredentialMeta -> encoder.encodeSerializableValue(W3cCredentialMeta.serializer(), value)
            is SdJwtVcMeta -> encoder.encodeSerializableValue(SdJwtVcMeta.serializer(), value)
            is MsoMdocMeta -> encoder.encodeSerializableValue(MsoMdocMeta.serializer(), value)
            is GenericMeta -> encoder.encodeSerializableValue(GenericMeta.serializer(), value)
        }
    }

    override fun deserialize(decoder: Decoder): CredentialQueryMeta {
        val jsonInput = decoder as? JsonDecoder
            ?: throw SerializationException("This serializer can only be used with Json")

        val jsonElement = jsonInput.decodeJsonElement()
        if (jsonElement !is JsonObject) {
            throw SerializationException("Expected JsonObject for CredentialQueryMeta, got ${jsonElement::class}")
        }

        // Spec: "If empty, no specific constraints are placed..."
        // This implies an empty JSON object {} for meta should be treated as NoMeta.
        if (jsonElement.isEmpty()) {
            return NoMeta
        }

        // Attempt to infer type based on known unique keys
        // This part needs to be robust or, ideally, guided by the parent 'format' field
        // if this serializer was part of a CredentialQuery custom serializer.
        return when {
            jsonElement.containsKey(W3cCredentialMeta.TYPE_VALUES_KEY) ->
                jsonInput.json.decodeFromJsonElement(W3cCredentialMeta.serializer(), jsonElement)

            jsonElement.containsKey(SdJwtVcMeta.VCT_VALUES_KEY) ->
                jsonInput.json.decodeFromJsonElement(SdJwtVcMeta.serializer(), jsonElement)

            jsonElement.containsKey(MsoMdocMeta.DOCTYPE_VALUE_KEY) ->
                jsonInput.json.decodeFromJsonElement(MsoMdocMeta.serializer(), jsonElement)

            jsonElement.containsKey("properties") && jsonElement["properties"] is JsonObject -> // For GenericMeta
                jsonInput.json.decodeFromJsonElement(GenericMeta.serializer(), jsonElement)

            else -> {
                // If it's not empty and doesn't match known structures, it's problematic.
                // Could try GenericMeta as a fallback if it has any properties not named "properties"
                // but this is getting very heuristic.
                // For now, if it's not empty and not recognized, it's an error.
                throw SerializationException("Could not determine specific CredentialQueryMeta type from non-empty content: $jsonElement. Ensure 'format' aligns with 'meta' structure or use an empty '{}' for NoMeta.")
            }
        }
    }
}
