package id.walt.openid4vp.conformance.utils

import kotlinx.serialization.json.Json

object JsonUtils {

    inline fun <reified T> String.fromJson() = Json.decodeFromString<T>(this)

}
