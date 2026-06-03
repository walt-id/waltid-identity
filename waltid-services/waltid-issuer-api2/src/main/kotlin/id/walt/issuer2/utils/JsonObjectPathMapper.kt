package id.walt.issuer2.utils

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.Option
import com.nfeld.jsonpathkt.kotlinx.resolveOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import com.jayway.jsonpath.JsonPath as JaywayJsonPath
import com.nfeld.jsonpathkt.JsonPath as KotlinxJsonPath

object JsonObjectPathMapper {
    fun validateJsonObjectContainsPaths(
        jsonObject: JsonObject,
        jsonPathList: List<String>,
    ) {
        jsonPathList.forEach { jsonPath ->
            requireNotNull(
                jsonObject.resolveOrNull(
                    path = KotlinxJsonPath.compile(jsonPath),
                )
            ) {
                "JSON path $jsonPath does not exist, or evaluates to null, when applied to object $jsonObject. " +
                    "All specified JSON paths must exist and resolve to non-null values in target JSON objects."
            }
        }
    }

    fun fromSourceToDestinationJsonPathsMap(
        source: JsonObject,
        destination: JsonObject,
        jsonPathMapConfig: Map<String, String>,
    ): JsonObject {
        validateJsonObjectContainsPaths(
            jsonObject = source,
            jsonPathList = jsonPathMapConfig.keys.toList(),
        )
        validateJsonObjectContainsPaths(
            jsonObject = destination,
            jsonPathList = jsonPathMapConfig.values.toList(),
        )

        val destinationContext = JaywayJsonPath
            .using(
                Configuration.defaultConfiguration().addOptions(
                    Option.SUPPRESS_EXCEPTIONS,
                    Option.DEFAULT_PATH_LEAF_TO_NULL,
                )
            )
            .parse(Json.encodeToString(destination))

        jsonPathMapConfig.forEach { (sourcePath, destinationPath) ->
            val sourceValue = source.resolveOrNull(
                path = KotlinxJsonPath.compile(sourcePath),
            )!!

            destinationContext.set(
                destinationPath,
                kotlinxSerializationJsonToJsonSmartTypeConversion(sourceValue),
            )
        }

        return Json.decodeFromString(destinationContext.jsonString())
    }

    private fun kotlinxSerializationJsonToJsonSmartTypeConversion(
        element: JsonElement,
    ): Any = when (element) {
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.boolean
            element.intOrNull != null -> element.int
            element.longOrNull != null -> element.long
            element.floatOrNull != null -> element.float
            element.doubleOrNull != null -> element.double
            else -> error("JsonPrimitive to JsonSmart conversion case that should be unreachable")
        }

        is JsonObject -> element.toMutableMap<String, Any>().apply {
            forEach { (key, value) ->
                this[key] = kotlinxSerializationJsonToJsonSmartTypeConversion(value as JsonElement)
            }
        }

        is JsonArray -> element.map {
            kotlinxSerializationJsonToJsonSmartTypeConversion(it)
        }
    }
}
