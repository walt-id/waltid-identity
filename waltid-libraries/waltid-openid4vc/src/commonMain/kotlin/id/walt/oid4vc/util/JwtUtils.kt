package id.walt.oid4vc.util

import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object JwtUtils {
    fun parseJWTPayload(token: String): JsonObject {
        return token.substringAfter(".").substringBefore(".").let {
            Json.decodeFromString(it.base64UrlDecode().decodeToString())
        }
    }

    fun parseJWTHeader(token: String): JsonObject {
        return token.substringBefore(".").let {
            Json.decodeFromString(it.base64UrlDecode().decodeToString())
        }
    }
}