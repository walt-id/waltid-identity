package id.walt.sdjwt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class SDTypeMetadata(
    @SerialName("name") val name: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("extends") val extends: String? = null,
    @SerialName("schema") val schema: JsonObject? = null,
    @SerialName("schema_uri") val schemaUri: String? = null,
    @SerialName("vct") val vct: String? = null,
    @SerialName("vct#integrity") val vctIntegrity: String? = null,
    @SerialName("extends#integrity") val extendsIntegrity: String? = null,
    @SerialName("schema#integrity") val schemaIntegrity: String? = null,
    @SerialName("schema_uri#integrity") val schemaUriIntegrity: String? = null,
    )