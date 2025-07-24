package id.walt.sdjwt

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
@Serializable(with = SDJWTVCTypeMetadataSerializer::class)
data class SDJWTVCTypeMetadata(
    @SerialName("name") val name: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("extends") val extends: String? = null,
    @SerialName("schema") val schema: JsonObject? = null,
    @SerialName("schema_uri") val schemaUri: String? = null,
    @SerialName("vct") val vct: String,
    @SerialName("vct#integrity") val vctIntegrity: String? = null,
    @SerialName("extends#integrity") val extendsIntegrity: String? = null,
    @SerialName("schema#integrity") val schemaIntegrity: String? = null,
    @SerialName("schema_uri#integrity") val schemaUriIntegrity: String? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {
    override fun toJSON(): JsonObject = Json.encodeToJsonElement(SDJWTVCTypeMetadataSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<SDJWTVCTypeMetadata>() {
        override fun fromJSON(jsonObject: JsonObject): SDJWTVCTypeMetadata =
            Json.decodeFromJsonElement(SDJWTVCTypeMetadataSerializer, jsonObject)
    }
}

internal object SDJWTVCTypeMetadataSerializer :
    JsonDataObjectSerializer<SDJWTVCTypeMetadata>(SDJWTVCTypeMetadata.generatedSerializer())

