package id.walt.mdoc.doc

import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.MapKey
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig
import id.walt.mdoc.dataelement.json.mapPortraitCaptureDate
import id.walt.mdoc.dataelement.json.toDataElement
import kotlinx.serialization.json.JsonObject

object MDocNameSpaceBuilder {

    fun fromJsonObjectMappingConfig(
        nameSpaceId: String,
        jsonData: JsonObject,
        dataMappingConfig: JsonObjectToCborMappingConfig,
    ): MDocNameSpace {
        require(jsonData.keys.containsAll(dataMappingConfig.entriesConfigMap.keys)) {
            "Json keys specified in JSON object config map must all exist in input JSON object"
        }
        return MDocNameSpace(
            nameSpaceId = nameSpaceId,
            claimsMap = MapElement(jsonData.entries.associate { (key, value) ->
                MapKey(key) to (mapPortraitCaptureDate(nameSpaceId, key, value)
                    ?: dataMappingConfig.entriesConfigMap[key]?.executeMapping(value)
                    ?: value.toDataElement())
            }),
        )
    }

    fun fromJsonObject(
        nameSpaceId: String,
        jsonData: JsonObject,
    ) = fromJsonObjectMappingConfig(nameSpaceId, jsonData, JsonObjectToCborMappingConfig(emptyMap()))
}
