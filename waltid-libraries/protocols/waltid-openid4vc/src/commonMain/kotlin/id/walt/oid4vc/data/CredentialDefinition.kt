package id.walt.oid4vc.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = CredentialDefinitionSerializer::class)
data class CredentialDefinition (
    val credentialSubject: JsonObject? = null,
    val type: List<String>? = null,
)

object CredentialDefinitionSerializer: KSerializer<CredentialDefinition> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CredentialDefinition")

    override fun deserialize(decoder: Decoder): CredentialDefinition {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("Expected JsonDecoder")

        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        val credentialSubject = jsonObject["credentialSubject"] as? JsonObject
        val type = jsonObject["type"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }

        return CredentialDefinition(
            credentialSubject = credentialSubject,
            type = type
        )
    }

    override fun serialize(
        encoder: Encoder,
        value: CredentialDefinition
    ) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("Expected JsonEncoder")

        val jsonObject = buildJsonObject {
            value.credentialSubject?.let { put("credentialSubject", it) }
            value.type?.let { put("type", JsonArray(it.map(::JsonPrimitive))) }
        }

        jsonEncoder.encodeJsonElement(jsonObject)
    }


}