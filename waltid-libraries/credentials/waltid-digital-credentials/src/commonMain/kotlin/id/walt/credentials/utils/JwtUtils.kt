package id.walt.credentials.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object JwtUtils {

    internal val base64Url = Base64.Default.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    fun String.isJwt() = startsWith("ey") && count { it == '.' } == 2

    fun parseJwt(jwt: String): Triple<JsonObject, JsonObject, String> = jwt.split(".").let {
        check(it.size == 3)
        fun parsePart(part: String): JsonObject = Json.Default.decodeFromString<JsonObject>(base64Url.decode(part).decodeToString())

        val header = parsePart(it[0])
        val body = parsePart(it[1])
        val signature = it[2]
        Triple(header, body, signature)
    }

}
