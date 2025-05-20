package id.walt.crypto.utils

import id.walt.crypto.utils.JwsUtils.JwsParts
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
        throw UnsupportedOperationException("Not implemented for macOS")
    }

    actual fun parseJWE(jwe: String, jwk: String): JwsParts {
        throw UnsupportedOperationException("Not implemented for macOS")
    }
}

