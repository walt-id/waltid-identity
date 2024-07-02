package id.walt.crypto.utils

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

actual object JweUtils {
    actual fun toJWE(
        payload: JsonObject,
        jwk: String,
        alg: String,
        enc: String,
        headerParams: Map<String, JsonElement>
    ): String {
        TODO("Not yet implemented")
    }

    actual fun parseJWE(
        jwe: String,
        jwk: String
    ): JwsUtils.JwsParts {
        TODO("Not yet implemented")
    }
}