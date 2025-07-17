package id.walt.oid4vc.data.dif

import id.walt.oid4vc.data.JsonDataObject
import id.walt.oid4vc.data.JsonDataObjectFactory
import id.walt.oid4vc.data.JsonDataObjectSerializer
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = SubmissionRequirementSerializer::class)
data class SubmissionRequirement(
    val rule: SubmissionRequirementRule,
    val from: String? = null,
    @SerialName("from_nested") @Serializable(SubmissionRequirementListSerializer::class) val fromNested: List<SubmissionRequirement>? = null,
    val name: String? = null,
    val purpose: String? = null,
    val count: Int? = null,
    val min: Int? = null,
    val max: Int? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf(),
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(SubmissionRequirementSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<SubmissionRequirement>() {
        override fun fromJSON(jsonObject: JsonObject): SubmissionRequirement =
            Json.decodeFromJsonElement(SubmissionRequirementSerializer, jsonObject)
    }
}

internal object SubmissionRequirementSerializer :
    JsonDataObjectSerializer<SubmissionRequirement>(SubmissionRequirement.generatedSerializer())

internal object SubmissionRequirementListSerializer : KSerializer<List<SubmissionRequirement>> {
    private val internalSerializer = ListSerializer(SubmissionRequirementSerializer)
    override val descriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder) = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: List<SubmissionRequirement>) =
        internalSerializer.serialize(encoder, value)
}
