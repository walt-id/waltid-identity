package id.walt.webwallet.config

import com.sksamuel.hoplite.*
import com.sksamuel.hoplite.decoder.Decoder
import com.sksamuel.hoplite.fp.Validated
import kotlinx.serialization.json.*
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType

class JsonElementDecoder : Decoder<JsonElement> {

    private fun Node.toSpecificJson(): JsonElement = when (this) {
        is MapNode -> JsonObject(this.map.mapValues { it.value.toSpecificJson() })
        is ArrayNode -> JsonArray(elements.map { it.toSpecificJson() })
        is StringNode -> JsonPrimitive(value)
        is BooleanNode -> JsonPrimitive(value)
        is LongNode -> JsonPrimitive(value)
        is DoubleNode -> JsonPrimitive(value)
        is NullNode -> JsonNull
        Undefined -> error("Undefined node type")
    }

    override fun decode(node: Node, type: KType, context: DecoderContext): ConfigResult<JsonElement> =
        Validated.Valid(node.toSpecificJson())

    override fun supports(type: KType): Boolean =
        type.isSubtypeOf(JsonElement::class.starProjectedType)
}
