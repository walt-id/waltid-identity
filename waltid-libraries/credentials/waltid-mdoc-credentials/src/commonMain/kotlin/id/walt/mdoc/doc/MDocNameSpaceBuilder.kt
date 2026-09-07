package id.walt.mdoc.doc

import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig
import id.walt.mdoc.dataelement.json.toDataElement
import kotlinx.serialization.json.JsonObject

object MDocNameSpaceBuilder {

    fun fromJsonObjectMappingConfig(
        nameSpaceId: String,
        jsonData: JsonObject,
        dataMappingConfig: JsonObjectToCborMappingConfig,
    ) = MDocNameSpace(
        nameSpaceId = nameSpaceId,
        claimsMap = dataMappingConfig.executeMapping(jsonData),
    )

    fun fromJsonObject(
        nameSpaceId: String,
        jsonData: JsonObject,
    ) = MDocNameSpace(
        nameSpaceId = nameSpaceId,
        claimsMap = jsonData.toDataElement() as MapElement,
    )
}


