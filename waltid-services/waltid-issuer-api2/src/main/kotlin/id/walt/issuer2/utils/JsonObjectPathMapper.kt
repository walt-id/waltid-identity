package id.walt.issuer2.utils

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.Option
import com.nfeld.jsonpathkt.kotlinx.resolveOrNull
import io.klogging.logger
import kotlinx.serialization.json.*
import com.jayway.jsonpath.JsonPath as JaywayJsonPath
import com.nfeld.jsonpathkt.JsonPath as EygraberJsonPath

/**
 * Utility for mapping JSON values between objects using JSONPath expressions.
 * Used for mapping ID token claims to credential data in the authorization code flow.
 */
object JsonObjectPathMapper {

    private val log = logger("JsonObjectPathMapper")

    suspend fun validateJsonObjectContainsPaths(
        jsonObject: JsonObject,
        jsonPathList: List<String>,
    ) {
        log.debug {
            "Validating whether input $jsonObject contains all paths $jsonPathList"
        }

        jsonPathList.forEach { curJsonPathStr ->
            requireNotNull(
                jsonObject.resolveOrNull(
                    path = EygraberJsonPath.compile(
                        path = curJsonPathStr,
                    ),
                )
            ) {
                "JSON path $curJsonPathStr does not exist, or evaluates to null, when applied to object $jsonObject. " +
                        "All specified JSON paths must exist and resolve to non-null values in target JSON objects."
            }
        }

        log.debug {
            "All paths found to exist in input"
        }
    }

    /**
     * Maps values from source JSON object to destination JSON object using JSONPath expressions.
     * 
     * @param source The source JSON object (e.g., ID token claims)
     * @param destination The destination JSON object (e.g., credential data template)
     * @param jsonPathMapConfig Map of source JSONPath -> destination JSONPath
     * @return Updated destination object with values from source
     */
    suspend fun fromSourceToDestinationJsonPathsMap(
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

        log.debug {
            "Going to map claims from source object $source to destination object $destination " +
                    "according to the json path associations: $jsonPathMapConfig"
        }

        val conf = Configuration
            .defaultConfiguration()
            .addOptions(
                Option.SUPPRESS_EXCEPTIONS,
                Option.DEFAULT_PATH_LEAF_TO_NULL,
            )

        val destinationContext = JaywayJsonPath
            .using(conf)
            .parse(Json.encodeToString(destination))

        jsonPathMapConfig.forEach { (sourcePathStr, destPathStr) ->

            log.debug {
                "Evaluating replacement from source path $sourcePathStr to destination $destPathStr"
            }

            val sourceValue = source.resolveOrNull(
                path = EygraberJsonPath.compile(
                    path = sourcePathStr,
                ),
            )!!

            log.debug {
                "Candidate replacement value $sourceValue retrieved from source object"
            }

            destinationContext.set(
                destPathStr,
                kotlinxSerializationJsonToJsonSmartTypeConversion(
                    element = sourceValue,
                )
            )
        }

        val result = Json.decodeFromString<JsonObject>(destinationContext.jsonString())

        log.debug {
            "Final result: $result"
        }

        return result
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

            else -> error("JsonPrimitive to JsonSmart conversion case that should be unreachable - this is a bug")
        }

        is JsonObject -> {
            element.toMutableMap<String, Any>().apply {
                this.forEach { (key, value) ->
                    this[key] = kotlinxSerializationJsonToJsonSmartTypeConversion(value as JsonElement)
                }
            }
        }

        is JsonArray -> {
            element.toList().map {
                kotlinxSerializationJsonToJsonSmartTypeConversion(it)
            }
        }
    }
}
