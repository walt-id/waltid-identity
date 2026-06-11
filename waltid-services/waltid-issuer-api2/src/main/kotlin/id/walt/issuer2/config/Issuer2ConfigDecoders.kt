package id.walt.issuer2.config

import com.sksamuel.hoplite.ArrayNode
import com.sksamuel.hoplite.BooleanNode
import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.DoubleNode
import com.sksamuel.hoplite.LongNode
import com.sksamuel.hoplite.MapNode
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.NullNode
import com.sksamuel.hoplite.StringNode
import com.sksamuel.hoplite.Undefined
import com.sksamuel.hoplite.decoder.Decoder
import com.sksamuel.hoplite.fp.Validated
import id.walt.commons.config.ConfigManager
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig
import id.walt.sdjwt.SDMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.KClass
import kotlin.reflect.KType

fun registerIssuer2ConfigDecoders() {
    ConfigManager.registerCustomDecoder(Issuer2KotlinxConfigDecoder(SDMap::class, SDMap.serializer()))
    ConfigManager.registerCustomDecoder(
        Issuer2KotlinxConfigDecoder(
            JsonObjectToCborMappingConfig::class,
            JsonObjectToCborMappingConfig.serializer(),
        )
    )
}

private class Issuer2KotlinxConfigDecoder<T : Any>(
    private val supportedClass: KClass<T>,
    private val serializer: KSerializer<T>,
) : Decoder<T> {
    override fun supports(type: KType): Boolean =
        type.classifier == supportedClass

    override fun decode(node: Node, type: KType, context: DecoderContext): ConfigResult<T> =
        try {
            Validated.Valid(json.decodeFromJsonElement(serializer, node.toJsonElement()))
        } catch (ex: Exception) {
            Validated.Invalid(ConfigFailure.DecodeError(node, type))
        }

    private fun Node.toJsonElement(): JsonElement = when (this) {
        is MapNode -> JsonObject(map.mapValues { it.value.toJsonElement() })
        is ArrayNode -> JsonArray(elements.map { it.toJsonElement() })
        is StringNode -> JsonPrimitive(value)
        is BooleanNode -> JsonPrimitive(value)
        is LongNode -> JsonPrimitive(value)
        is DoubleNode -> JsonPrimitive(value)
        is NullNode -> JsonNull
        Undefined -> JsonNull
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
