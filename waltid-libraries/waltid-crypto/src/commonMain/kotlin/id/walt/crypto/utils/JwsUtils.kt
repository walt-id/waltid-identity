package id.walt.crypto.utils

import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.Base64Utils.base64UrlToBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
@JsExport
object JwsUtils {

    fun KeyType.jwsAlg() = when (this) {
        KeyType.Ed25519 -> "EdDSA"
        KeyType.secp256r1 -> "ES256"
        KeyType.secp256k1 -> "ES256K"
        KeyType.RSA -> "RS256" // TODO: RS384 RS512
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun String.decodeJwsPart(): JsonObject =
        Json.parseToJsonElement(Base64.decode(this.base64UrlToBase64()).decodeToString()).jsonObject

    data class JwsParts(val header: JsonObject, val payload: JsonObject, val signature: String) {
        override fun toString() = "${Json.encodeToString(header).encodeToByteArray().encodeToBase64Url()}.${
            Json.encodeToString(payload).encodeToByteArray().encodeToBase64Url()
        }.$signature"
    }

    data class JwsStringParts(val header: String, val payload: String, val signature: String) {
        fun getSignable() = "$header.$payload"
    }

    private fun checkJwsPreconditions(jws: String, allowMissingSignature: Boolean) {
        check(jws.startsWith("ey")) { "String does not look like JWS: $this" }
        val dots = jws.count { it == '.' }
        check(
            dots == 2
                    || (allowMissingSignature && dots == 1)
        ) { "String does not have correct JWS part amount (dots=$dots, allowMissingSignature=$allowMissingSignature): $this" }
    }

    fun String.decodeJwsStrings(): JwsStringParts {
        checkJwsPreconditions(this, false)
        val splitted = split(".")
        val (header, payload, signature) = splitted
        return JwsStringParts(header, payload, signature)
    }

    fun String.decodeJws(withSignature: Boolean = false, allowMissingSignature: Boolean = false): JwsParts {
        checkJwsPreconditions(this, allowMissingSignature)

        val parts = this.decodeJwsStrings()

        val header = runCatching { parts.header.decodeJwsPart() }.getOrElse { ex ->
            throw IllegalArgumentException("Could not parse JWT header (base64/json issue): ${parts.header}", ex)
        }
        val payload = runCatching { parts.payload.decodeJwsPart() }.getOrElse { ex ->
            throw IllegalArgumentException("Could not parse JWT payload (base64/json issue): ${parts.payload}", ex)
        }
        val signature = if (withSignature) parts.signature else ""

        return JwsParts(header, payload, signature)
    }

}
