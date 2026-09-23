package id.walt.issuer2.service.openid4vci

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

internal object DisplayUriResolver {
    fun resolve(element: JsonElement, issuerHttpBaseUrl: String): JsonElement {
        val baseUrl = issuerHttpBaseUrl.trimEnd('/')
        return resolve(element) { uri ->
            if (uri.startsWith("/")) "$baseUrl$uri" else uri
        }
    }

    private fun resolve(element: JsonElement, absolutize: (String) -> String): JsonElement = when (element) {
        is JsonObject -> buildJsonObject {
            element.forEach { (key, value) ->
                if (key == "uri" && value is JsonPrimitive) {
                    val uri = value.contentOrNull
                    put(key, if (uri != null) JsonPrimitive(absolutize(uri)) else value)
                } else {
                    put(key, resolve(value, absolutize))
                }
            }
        }
        is JsonArray -> JsonArray(element.map { resolve(it, absolutize) })
        else -> element
    }
}
