package id.walt.crypto.utils

import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.Base64Utils.base64toBase64Url
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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

    private fun String.decodeJwsPart(): JsonObject =
        Json.parseToJsonElement(this.base64toBase64Url().decodeFromBase64Url().decodeToString()).jsonObject

    @Serializable
    data class JwsParts(val header: JsonObject, val payload: JsonObject, val signature: String) {
        override fun toString() = "${Json.encodeToString(header).encodeToByteArray().encodeToBase64Url()}.${
            Json.encodeToString(payload).encodeToByteArray().encodeToBase64Url()
        }.$signature"
    }

    @Serializable
    data class JwsPartsSdJwt(val jwsParts: JwsParts, val sdJwtDisclosures: List<String>) {

        fun sdJwtDisclosuresString() = if (sdJwtDisclosures.isNotEmpty()) "~${sdJwtDisclosures.joinToString("~")}" else ""
        override fun toString() = "$jwsParts${sdJwtDisclosuresString()}"

    }

    data class JwsStringParts(val header: String, val payload: String, val signature: String) {
        fun getSignable() = "$header.$payload"
    }

    private fun checkJwsPreconditions(jws: String, allowMissingSignature: Boolean) {
        require(jws.startsWith("ey")) { "String does not look like JWS: $jws" }
        val dots = jws.count { it == '.' }
        require(
            dots == 2
                    || (allowMissingSignature && dots == 1)
        ) { "String does not have correct JWS part amount (dots=$dots, allowMissingSignature=$allowMissingSignature): $jws" }
    }

    fun String.decodeJwsStrings(): JwsStringParts {
        checkJwsPreconditions(this, false)
        val splitted = split(".")
        val (header, payload, signature) = splitted
        return JwsStringParts(header, payload, signature)
    }

    fun String.decodeJws(withSignature: Boolean = true, allowMissingSignature: Boolean = false): JwsParts {
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

    fun String.decodeJwsOrSdjwt(): JwsPartsSdJwt {
        val jws = substringBefore("~")
        val sdJwtClaims = substringAfter("~", "").split("~")

        val jwsParts = jws.decodeJws()

        return JwsPartsSdJwt(jwsParts, sdJwtClaims)
    }

}
