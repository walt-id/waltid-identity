package id.walt.openid4vp.conformance.utils

import kotlinx.serialization.json.Json

object JsonUtils {

    val lenientJson = Json {
        ignoreUnknownKeys = true
    }

    inline fun <reified T> String.fromJson() = lenientJson.decodeFromString<T>(this)

}
