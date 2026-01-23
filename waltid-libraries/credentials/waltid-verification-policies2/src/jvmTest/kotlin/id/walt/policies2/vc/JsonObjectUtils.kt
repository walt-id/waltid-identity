package id.walt.policies2.vc

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object JsonObjectUtils {
    fun updateJsonObjectPlaceholders(
        jsonObject: JsonObject, placeholder: String, valueSeparator: String, vararg placeholderValue: String
    ): JsonObject {
        val updatedMap = jsonObject.toMutableMap()

        updatedMap.replaceAll { _, value ->
            when (value) {
                is JsonPrimitive -> {
                    if (value.isString && value.content.contains(placeholder)) {
                        JsonPrimitive(
                            value.content.replace(
                                placeholder, placeholderValue.joinToString("/")
                            )
                        )
                    } else {
                        value
                    }
                }

                is JsonObject -> updateJsonObjectPlaceholders(value, placeholder, valueSeparator, *placeholderValue)
                is JsonArray -> updateJsonArrayPlaceholders(value, placeholder, valueSeparator, *placeholderValue)
            }
        }

        return JsonObject(updatedMap)
    }

    fun updateJsonArrayPlaceholders(
        jsonArray: JsonArray, placeholder: String, valueSeparator: String, vararg placeholderValue: String
    ): JsonArray {
        val updatedList = jsonArray.map { element ->
            when (element) {
                is JsonPrimitive -> {
                    if (element.isString && element.content.contains(placeholder)) {
                        JsonPrimitive(
                            element.content.replace(
                                placeholder, placeholderValue.joinToString("#")
                            )
                        )
                    } else {
                        element
                    }
                }

                is JsonObject -> updateJsonObjectPlaceholders(element, placeholder, valueSeparator, *placeholderValue)
                is JsonArray -> updateJsonArrayPlaceholders(element, placeholder, valueSeparator, *placeholderValue)
            }
        }
        return JsonArray(updatedList)
    }
}
