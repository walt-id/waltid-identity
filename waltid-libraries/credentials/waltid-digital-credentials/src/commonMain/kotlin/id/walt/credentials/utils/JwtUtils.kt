package id.walt.credentials.utils

import id.walt.crypto.utils.Base64Utils.base64Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object JwtUtils {

    // Per RFC 9901 §4 ABNF: SD-JWT-KB = SD-JWT KB-JWT, where SD-JWT = JWT "~" *(DISCLOSURE "~").
    // The full SD-JWT+KB string contains the KB-JWT after the last "~", which adds more dots.
    // We must only check the issuer-signed JWT part (before the first "~") for the 3-part JWT structure.
    fun String.isJwt() = substringBefore("~").let { it.startsWith("ey") && it.count { c -> c == '.' } == 2 }

    fun parseJwt(jwt: String): Triple<JsonObject, JsonObject, String> = jwt.split(".").let {
        check(it.size == 3)
        fun parsePart(part: String): JsonObject = Json.decodeFromString<JsonObject>(base64Url.decode(part).decodeToString())

        val header = parsePart(it[0])
        val body = parsePart(it[1])
        val signature = it[2]
        Triple(header, body, signature)
    }

}
