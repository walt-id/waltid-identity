package id.walt.commons.config

import com.sksamuel.hoplite.*
import com.sksamuel.hoplite.decoder.Decoder
import com.sksamuel.hoplite.fp.Validated
import kotlinx.serialization.json.*
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.full.withNullability

class JsonElementDecoder : Decoder<JsonElement?> {

    override fun decode(node: Node, type: KType, context: DecoderContext): ConfigResult<JsonElement?> =
        if (node is Undefined) Validated.Valid(null)
        else Validated.Valid(node.toJsonElement())

    override fun supports(type: KType): Boolean =
        type.withNullability(false).isSubtypeOf(JsonElement::class.starProjectedType)
}

/** Converts a Hoplite config node to JSON without reinterpreting string values. */
fun Node.toJsonElement(): JsonElement = when (this) {
    is MapNode -> JsonObject(map.mapValues { it.value.toJsonElement() })
    is ArrayNode -> JsonArray(elements.map { it.toJsonElement() })
    is StringNode -> JsonPrimitive(value)
    is BooleanNode -> JsonPrimitive(value)
    is LongNode -> JsonPrimitive(value)
    is DoubleNode -> JsonPrimitive(value)
    is NullNode, Undefined -> JsonNull
}
