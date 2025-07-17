package id.walt.oid4vc.data.dif

import id.walt.oid4vc.data.JsonDataObject
import id.walt.oid4vc.data.JsonDataObjectFactory
import id.walt.oid4vc.data.JsonDataObjectSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = PresentationSubmissionSerializer::class)
data class PresentationSubmission(
    val id: String,
    @SerialName("definition_id") val definitionId: String,
    @SerialName("descriptor_map") @Serializable(DescriptorMappingListSerializer::class) val descriptorMap: List<DescriptorMapping>,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(PresentationSubmissionSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<PresentationSubmission>() {
        override fun fromJSON(jsonObject: JsonObject): PresentationSubmission =
            Json.decodeFromJsonElement(PresentationSubmissionSerializer, jsonObject)
    }
}

internal object PresentationSubmissionSerializer :
    JsonDataObjectSerializer<PresentationSubmission>(PresentationSubmission.generatedSerializer())
