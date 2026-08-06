package id.walt.wallet2.config

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
import id.walt.crypto.keys.DirectSerializedKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.KType
import kotlin.time.Duration

fun registerWallet2ConfigDecoders() {
    ConfigManager.registerCustomDecoder(UrlHopliteDecoder())
    ConfigManager.registerCustomDecoder(DirectSerializedKeyDecoder())
    ConfigManager.registerCustomDecoder(IsoDurationDecoder())
}

private class DirectSerializedKeyDecoder : Decoder<DirectSerializedKey> {
    override fun supports(type: KType): Boolean =
        type.classifier == DirectSerializedKey::class

    override fun decode(
        node: Node,
        type: KType,
        context: DecoderContext,
    ): ConfigResult<DirectSerializedKey> = try {
        Validated.Valid(json.decodeFromJsonElement(DirectSerializedKey.serializer(), node.toJsonElement()))
    } catch (_: Exception) {
        Validated.Invalid(ConfigFailure.DecodeError(node, type))
    }

    private fun Node.toJsonElement(): JsonElement = when (this) {
        is MapNode -> JsonObject(map.mapValues { it.value.toJsonElement() })
        is ArrayNode -> JsonArray(elements.map { it.toJsonElement() })
        is StringNode -> JsonPrimitive(value)
        is BooleanNode -> JsonPrimitive(value)
        is LongNode -> JsonPrimitive(value)
        is DoubleNode -> JsonPrimitive(value)
        is NullNode, Undefined -> JsonNull
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = false
            explicitNulls = true
        }
    }
}

private class IsoDurationDecoder : Decoder<Duration> {
    override fun supports(type: KType): Boolean =
        type.classifier == Duration::class

    override fun decode(node: Node, type: KType, context: DecoderContext): ConfigResult<Duration> =
        try {
            Validated.Valid(Duration.parseIsoString((node as StringNode).value))
        } catch (_: Exception) {
            Validated.Invalid(ConfigFailure.DecodeError(node, type))
        }
}
