package id.walt.oid4vc.util

import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object JwtUtils {
    fun parseJWTPayload(token: String): JsonObject {
        return token.substringAfter(".").substringBefore(".").let {
            Json.decodeFromString(it.decodeFromBase64Url().decodeToString())
        }
    }

    fun parseJWTHeader(token: String): JsonObject {
        return token.substringBefore(".").let {
            Json.decodeFromString(it.decodeFromBase64Url().decodeToString())
        }
    }
}
