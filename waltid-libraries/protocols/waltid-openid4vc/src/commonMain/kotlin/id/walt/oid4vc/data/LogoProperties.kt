package id.walt.oid4vc.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 *  A JSON object with information about the logo of the Credential
 *  @param url OPTIONAL. URL where the Wallet can obtain a logo of the Credential from the Credential Issuer.
 *  @param altText OPTIONAL. String value of an alternative text of a logo image.
 *  @param customParameters Other (custom) logo properties
 */
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = LogoPropertiesSerializer::class)
data class LogoProperties(
    val url: String? = null,
    @SerialName("alt_text") val altText: String? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {
    override fun toJSON(): JsonObject = Json.encodeToJsonElement(LogoPropertiesSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<LogoProperties>() {
        override fun fromJSON(jsonObject: JsonObject): LogoProperties =
            Json.decodeFromJsonElement(LogoPropertiesSerializer, jsonObject)
    }
}

object LogoPropertiesSerializer : JsonDataObjectSerializer<LogoProperties>(LogoProperties.generatedSerializer())

