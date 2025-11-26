@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.sdjwt.metadata.type

import id.walt.sdjwt.utils.JsonDataObject
import id.walt.sdjwt.utils.JsonDataObjectFactory
import id.walt.sdjwt.utils.JsonDataObjectSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@KeepGeneratedSerializer
@Serializable(with = SDJWTVCTypeMetadataDraft04Serializer::class)
data class SdJwtVcTypeMetadataDraft04(
    val vct: String? = null, //vct not defined in format section of type metadata, but is included in the example payload provided in the spec ...
    val name: String? = null,
    val description: String? = null,
    val extends: String? = null,
    @SerialName("extends#integrity")
    val extendsIntegrity: String? = null,
    @SerialName("schema")
    val schema: JsonObject? = null,
    @SerialName("schema_uri")
    val schemaUri: String? = null,
    @SerialName("schema_uri#integrity")
    val schemaUriIntegrity: String? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf(),
): JsonDataObject() {

    init {

        schema?.let {
            require(schemaUri == null) { "Schema URI must be null when schema property is used" }
        }

        schemaUri?.let {
            require(schema == null) { "Schema must be null when schema_uri property is used" }
        }

        schemaUriIntegrity?.let {
            requireNotNull(schemaUri) { "Schema URI integrity assumes that schema_uri property is used" }
        }

    }

    override fun toJSON(): JsonObject {
        return Json.encodeToJsonElement(SDJWTVCTypeMetadataDraft04Serializer, this).jsonObject
    }

    companion object : JsonDataObjectFactory<SdJwtVcTypeMetadataDraft04>() {
        override fun fromJSON(jsonObject: JsonObject): SdJwtVcTypeMetadataDraft04 =
            Json.decodeFromJsonElement(SDJWTVCTypeMetadataDraft04Serializer, jsonObject)
    }
}

private object SDJWTVCTypeMetadataDraft04Serializer :
    JsonDataObjectSerializer<SdJwtVcTypeMetadataDraft04>(SdJwtVcTypeMetadataDraft04.generatedSerializer())
