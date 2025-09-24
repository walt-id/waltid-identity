package id.walt.mdoc.dataelement.json

import id.walt.mdoc.dataelement.DataElement
import id.walt.mdoc.dataelement.ListElement
import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.MapKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
sealed class JsonElementToCborMappingConfig {
    abstract fun executeMapping(
        json: JsonElement,
    ): DataElement
}

@Serializable
@SerialName("string")
data class JsonStringToCborMappingConfig(
    val conversionType: StringToCborTypeConversion
) : JsonElementToCborMappingConfig() {

    override fun executeMapping(
        json: JsonElement,
    ): DataElement {
        require(json is JsonPrimitive && json.isString) {
            "this is not a json string"
        }
        return StringToCborElementConverter.convert(
            s = json.jsonPrimitive.content,
            conversionHint = conversionType,
        )
    }
}

@Serializable
@SerialName("object")
data class JsonObjectToCborMappingConfig(
    val entriesConfigMap: Map<String, JsonElementToCborMappingConfig>
) : JsonElementToCborMappingConfig() {

    override fun executeMapping(
        json: JsonElement,
    ): MapElement {
        require(json is JsonObject) {
            "this is not a json object"
        }
        require(json.jsonObject.keys.containsAll(entriesConfigMap.keys)) {
            "did not find all keys"
        }
        val claimsMap = mutableMapOf<MapKey, DataElement>()
        json.jsonObject.forEach { (key, value) ->
            claimsMap[MapKey(key)] = entriesConfigMap[key]?.executeMapping(value)
                ?: value.toDataElement()
        }
        return MapElement(claimsMap)
    }
}

@Serializable
@SerialName("array")
data class JsonArrayToCborMappingConfig(
    val arrayConfig: List<JsonElementToCborMappingConfig>
) : JsonElementToCborMappingConfig() {
    override fun executeMapping(
        json: JsonElement,
    ): DataElement {
        require(json is JsonArray){
            "this is not a json array"
        }
        require(json.jsonArray.size == arrayConfig.size) {
            "arrays are not of the same size!"
        }
        arrayConfig.zip(json.jsonArray).map { pair ->
            pair.first.executeMapping(pair.second)
        }.run {
            return ListElement(this)
        }
    }

}