package id.walt.core.crypto.utils

import id.walt.core.crypto.keys.KeyType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object JwsUtils {

    fun KeyType.jwsAlg() = when (this) {
        KeyType.Ed25519 -> "EdDSA"
        KeyType.secp256r1 -> "ES256"
        KeyType.secp256k1 -> "ES256K"
        KeyType.RSA -> "RS256" // TODO: RS384 RS512
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun String.decodeJwsPart(): JsonObject =
        Json.parseToJsonElement(Base64.decode(this).decodeToString()).jsonObject

    data class JwsParts(val header: JsonObject, val payload: JsonObject)

    fun String.decodeJws(): JwsParts {
        check(startsWith("ey")) { "String does not look like JWS: $this" }
        check(count { it == '.' } == 2) { "String does not have JWS part amount of 3 (= 2 dots): $this" }

        val splitted = split(".")
        val header = runCatching { splitted[0].decodeJwsPart() }.getOrElse { throw IllegalArgumentException("Could not parse JWT header (base64/json issue): ${splitted[0]}", it) }
        val payload = runCatching { splitted[1].decodeJwsPart() }.getOrElse { throw IllegalArgumentException("Could not parse JWT payload (base64/json issue): ${splitted[1]}", it) }

        return JwsParts(header, payload)
    }

}
