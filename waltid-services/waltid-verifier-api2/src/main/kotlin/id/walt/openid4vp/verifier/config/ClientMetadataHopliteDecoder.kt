package id.walt.openid4vp.verifier.config

import com.sksamuel.hoplite.*
import com.sksamuel.hoplite.decoder.Decoder
import com.sksamuel.hoplite.fp.Validated
import id.walt.verifier.openid.models.authorization.ClientMetadata
import kotlinx.serialization.json.*
import kotlin.reflect.KType

/**
 * Custom Hoplite decoder for ClientMetadata that properly handles RFC 7591 internationalized fields.
 * 
 * This decoder converts HOCON nodes to JSON and uses the ClientMetadataSerializer to deserialize,
 * ensuring that language-tagged fields (e.g., "client_name#fr-FR") are properly parsed.
 */
class ClientMetadataHopliteDecoder : Decoder<ClientMetadata> {

    private fun Node.toJsonElement(): JsonElement = when (this) {
        is MapNode -> JsonObject(this.map.mapValues { it.value.toJsonElement() })
        is ArrayNode -> JsonArray(elements.map { it.toJsonElement() })
        is StringNode -> JsonPrimitive(value)
        is BooleanNode -> JsonPrimitive(value)
        is LongNode -> JsonPrimitive(value)
        is DoubleNode -> JsonPrimitive(value)
        is NullNode -> JsonNull
        Undefined -> error("Undefined node type")
    }

    override fun decode(node: Node, type: KType, context: DecoderContext): ConfigResult<ClientMetadata> {
        return try {
            val jsonElement = node.toJsonElement()
            require(jsonElement is JsonObject) { "ClientMetadata must be an object" }
            
            // Use Kotlinx Serialization with our custom serializer
            val json = Json { ignoreUnknownKeys = true }
            val clientMetadata = json.decodeFromJsonElement(ClientMetadata.serializer(), jsonElement)
            
            Validated.Valid(clientMetadata)
        } catch (e: Exception) {
            Validated.Invalid(ConfigFailure.DecodeError(node, type))
        }
    }

    override fun supports(type: KType): Boolean {
        return type.classifier == ClientMetadata::class
    }
}

