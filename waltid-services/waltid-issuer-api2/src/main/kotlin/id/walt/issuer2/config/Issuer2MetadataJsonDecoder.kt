package id.walt.issuer2.config

import com.sksamuel.hoplite.ArrayNode
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.MapNode
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.StringNode
import com.sksamuel.hoplite.Undefined
import com.sksamuel.hoplite.decoder.Decoder
import com.sksamuel.hoplite.fp.Validated
import id.walt.commons.config.JsonElementDecoder
import id.walt.commons.config.toJsonElement
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlin.reflect.KType

/** Applies mdoc path semantics to the effective metadata, after Hoplite has merged overrides. */
internal class Issuer2MetadataJsonDecoder : Decoder<JsonElement?> {
    private val ordinary = JsonElementDecoder()

    override fun supports(type: KType): Boolean = ordinary.supports(type)

    // Hoplite picks the first registered decoder when priorities tie. Select this one explicitly.
    override fun priority(): Int = ordinary.priority() + 1

    override fun decode(node: Node, type: KType, context: DecoderContext): ConfigResult<JsonElement?> {
        if (node is Undefined) return Validated.Valid(null)
        val path = node.path.keys
        val mdoc = path.size == 2 && path[0].equals("credentialConfigurations", ignoreCase = true) &&
            (node.atKey("format") as? StringNode)?.value == "mso_mdoc"
        return Validated.Valid(if (mdoc) node.toMdocMetadataJson(emptyList()) else node.toJsonElement())
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun Node.toMdocMetadataJson(path: List<String>): JsonElement = when (this) {
        is MapNode -> JsonObject(map.mapValues { (key, value) -> value.toMdocMetadataJson(path + key) })
        is ArrayNode -> JsonArray(elements.mapIndexed { index, value -> value.toMdocMetadataJson(path + index.toString()) })
        is StringNode -> if (
            path.size == 5 && path[0] == "credential_metadata" && path[1] == "claims" &&
            path[3] == "path" && (path[4].toIntOrNull() ?: -1) >= 2 &&
            meta[Issuer2MetadataHoconParser.LARGE_INTEGER_META] == true &&
            Issuer2MetadataHoconParser.isIntegerBeyondInt64(value)
        ) JsonUnquotedLiteral(value) else toJsonElement()
        else -> toJsonElement()
    }
}
