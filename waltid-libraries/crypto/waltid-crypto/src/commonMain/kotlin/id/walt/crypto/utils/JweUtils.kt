package id.walt.crypto.utils

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

expect object JweUtils {
  fun toJWE(payload: JsonObject, jwk: String, alg: String = "ECDH-ES", enc: String = "A256GCM", headerParams: Map<String, JsonElement> = mapOf()): String
  fun parseJWE(jwe: String, jwk: String): JwsUtils.JwsParts
}
