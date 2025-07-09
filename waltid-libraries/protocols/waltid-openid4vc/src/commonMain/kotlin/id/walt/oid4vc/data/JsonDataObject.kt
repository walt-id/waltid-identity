package id.walt.oid4vc.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.*

@Serializable
abstract class JsonDataObject {
    abstract val customParameters: Map<String, JsonElement>?
    abstract fun toJSON(): JsonObject
    fun toJSONString() = toJSON().toString()
}

abstract class JsonDataObjectFactory<T : JsonDataObject> {
    abstract fun fromJSON(jsonObject: JsonObject): T
    fun fromJSONString(json: String) = fromJSON(Json.decodeFromString(json))
}

abstract class JsonDataObjectSerializer<T : JsonDataObject>(serializer: KSerializer<T>) :
    JsonTransformingSerializer<T>(serializer) {

    private val customParametersName = "customParameters"

    @OptIn(ExperimentalSerializationApi::class)
    private val knownElementNames get() = descriptor.elementNames.filter { it != customParametersName }.toSet()

    open fun serializeKnownElement(name: String, element: JsonElement, builderMap: MutableMap<String, JsonElement>) {
        builderMap[name] = element
    }

    open fun deserializeKnownElement(name: String, element: JsonElement, builderMap: MutableMap<String, JsonElement>) {
        builderMap[name] = element
    }

    override fun transformSerialize(element: JsonElement): JsonElement {
        return JsonObject(
            buildMap {
                element.jsonObject.filterKeys { knownElementNames.contains(it) }
                    .forEach { (key, value) -> serializeKnownElement(key, value, this) }
                element.jsonObject[customParametersName]?.jsonObject?.let { putAll(it) }
            }
        )
    }

    override fun transformDeserialize(element: JsonElement): JsonElement {
        val obj = JsonObject(buildMap {
            element.jsonObject.filterKeys {
                knownElementNames.contains(it)
            }.forEach { (key, value) ->
                deserializeKnownElement(key, value, this)
            }
            put(
                customParametersName,
                JsonObject(element.jsonObject.toMap().filterKeys { !knownElementNames.contains(it) })
            )
        }
        )
        return obj
    }
}
