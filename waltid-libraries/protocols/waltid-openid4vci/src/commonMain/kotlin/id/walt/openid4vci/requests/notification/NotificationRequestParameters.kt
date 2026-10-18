package id.walt.openid4vci.requests.notification

import id.walt.openid4vci.json.jsonHasDuplicateMembers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Converts a JSON body to endpoint parameters, rejecting duplicate members and non-string
 * recognized fields. Unknown fields are ignored. Invalid bodies become empty parameters
 * so access-token verification still precedes notification parameter validation.
 */
fun notificationRequestParameters(body: String): Map<String, List<String>> {
    if (jsonHasDuplicateMembers(body)) return emptyMap()
    val element = try {
        Json.parseToJsonElement(body) as? JsonObject ?: return emptyMap()
    } catch (_: Exception) {
        return emptyMap()
    }
    val parameters = mutableMapOf<String, List<String>>()
    for (name in listOf("notification_id", "event", "event_description")) {
        val value = element[name] ?: continue
        if (value !is JsonPrimitive || !value.isString) return emptyMap()
        parameters[name] = listOf(value.content)
    }
    return parameters
}
