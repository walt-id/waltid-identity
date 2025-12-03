package id.walt.openid4vp.verifier.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object JsonUtils {

    fun String.parseAsJsonObject(errorMessage: String = "Could not parse string as JSON Object") =
        runCatching { Json.decodeFromString<JsonObject>(this) }
            .getOrElse {
                throw IllegalArgumentException(
                    "$errorMessage: ${it.message ?: "see exception"}", it
                )
            }

}
