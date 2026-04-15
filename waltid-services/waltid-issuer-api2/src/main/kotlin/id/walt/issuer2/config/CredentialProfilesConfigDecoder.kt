package id.walt.issuer2.config

import com.sksamuel.hoplite.*
import com.sksamuel.hoplite.decoder.Decoder
import com.sksamuel.hoplite.fp.Validated
import io.klogging.noCoLogger
import kotlinx.serialization.json.*
import kotlin.reflect.KType

class CredentialProfilesConfigDecoder : Decoder<CredentialProfilesConfig> {

    private val log = noCoLogger("CredentialProfilesConfigDecoder")

    private fun Node.toJsonElement(): JsonElement = when (this) {
        is MapNode -> JsonObject(this.map.mapValues { it.value.toJsonElement() })
        is ArrayNode -> JsonArray(elements.map { it.toJsonElement() })
        is StringNode -> JsonPrimitive(value)
        is BooleanNode -> JsonPrimitive(value)
        is LongNode -> JsonPrimitive(value)
        is DoubleNode -> JsonPrimitive(value)
        is NullNode -> JsonNull
        Undefined -> JsonNull
    }

    override fun decode(node: Node, type: KType, context: DecoderContext): ConfigResult<CredentialProfilesConfig> {
        return try {
            log.debug { "Decoding CredentialProfilesConfig from node type: ${node::class.simpleName}" }
            val jsonElement = node.toJsonElement()
            require(jsonElement is JsonObject) { "CredentialProfilesConfig must be an object, got: ${jsonElement::class.simpleName}" }

            val json = Json { 
                ignoreUnknownKeys = true 
                isLenient = true
            }
            val config = json.decodeFromJsonElement(CredentialProfilesConfig.serializer(), jsonElement)
            log.debug { "Successfully decoded CredentialProfilesConfig with ${config.profiles.size} profiles" }

            Validated.Valid(config)
        } catch (e: Exception) {
            log.error { "Failed to decode CredentialProfilesConfig: ${e.message}" }
            e.printStackTrace()
            Validated.Invalid(ConfigFailure.Generic("Failed to decode CredentialProfilesConfig: ${e.message}"))
        }
    }

    override fun supports(type: KType): Boolean {
        return type.classifier == CredentialProfilesConfig::class
    }
}
