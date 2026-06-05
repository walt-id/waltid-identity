package id.walt.wallet2.config

import com.sksamuel.hoplite.*
import com.sksamuel.hoplite.decoder.Decoder
import com.sksamuel.hoplite.fp.Validated
import io.ktor.http.*
import kotlin.reflect.KType

/**
 * Custom Hoplite decoder for Ktor's Url type.
 * Converts string values from HOCON config to io.ktor.http.Url instances.
 */
class UrlHopliteDecoder : Decoder<Url> {

    override fun decode(node: Node, type: KType, context: DecoderContext): ConfigResult<Url> {
        return when (node) {
            is StringNode -> {
                try {
                    Validated.Valid(Url(node.value))
                } catch (e: Exception) {
                    Validated.Invalid(ConfigFailure.DecodeError(node, type))
                }
            }
            else -> Validated.Invalid(ConfigFailure.DecodeError(node, type))
        }
    }

    override fun supports(type: KType): Boolean {
        return type.classifier == Url::class
    }
}
