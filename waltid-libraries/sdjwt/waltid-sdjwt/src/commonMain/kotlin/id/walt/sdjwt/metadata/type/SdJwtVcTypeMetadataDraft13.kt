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
@Serializable(with = SDJWTVCTypeMetadataDraft13Serializer::class)
data class SdJwtVcTypeMetadataDraft13(
    val vct: String,
    val name: String? = null,
    val description: String? = null,
    val extends: String? = null,
    @SerialName("extends#integrity")
    val extendsIntegrity: String? = null,
    val display: List<DisplayInformation>? = null,
    val claims: List<ClaimInformation>? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf(),
) : JsonDataObject() {

    override fun toJSON(): JsonObject {
        return Json.encodeToJsonElement(SDJWTVCTypeMetadataDraft13Serializer, this).jsonObject
    }

    companion object : JsonDataObjectFactory<SdJwtVcTypeMetadataDraft13>() {
        override fun fromJSON(jsonObject: JsonObject): SdJwtVcTypeMetadataDraft13 =
            Json.decodeFromJsonElement(SDJWTVCTypeMetadataDraft13Serializer, jsonObject)
    }
}

private object SDJWTVCTypeMetadataDraft13Serializer :
    JsonDataObjectSerializer<SdJwtVcTypeMetadataDraft13>(SdJwtVcTypeMetadataDraft13.generatedSerializer())

@Serializable
data class DisplayInformation(
    val locale: String,
    val name: String,
    val description: String? = null,
    val rendering: RenderingMetadata? = null,
)

@Serializable
data class RenderingMetadata(
    val simple: SimpleRenderingMethod? = null,
    @SerialName("svg_templates")
    val svgTemplates: List<SvgTemplateRenderingMethod>? = null,
)

@Serializable
data class SvgTemplateRenderingMethod(
    val uri: String,
    @SerialName("uri#integrity")
    val uriIntegrity: String? = null,
    val properties: SvgTemplateProperties? = null,
)

@Serializable
data class SvgTemplateProperties(
    val orientation: SvgTemplateOrientation? = null,
    @SerialName("color_scheme")
    val colorScheme: SvgTemplateColorScheme? = null,
    val contrast: SvgTemplateContrast? = null,
)

@Serializable
enum class SvgTemplateOrientation {
    @SerialName("portrait")
    Portrait,

    @SerialName("landscape")
    Landscape
}

@Serializable
enum class SvgTemplateColorScheme {
    @SerialName("light")
    Light,

    @SerialName("dark")
    Dark,
}

@Serializable
enum class SvgTemplateContrast {
    @SerialName("normal")
    Normal,

    @SerialName("high")
    High,
}

@Serializable
data class SimpleRenderingMethod(
    val logo: LogoMetadata? = null,
    @SerialName("background_image")
    val backgroundImage: BackgroundImageMetadata? = null,
    @SerialName("background_color")
    val backgroundColour: String? = null,
    @SerialName("text_color")
    val textColor: String? = null,
)

@Serializable
data class LogoMetadata(
    val uri: String,
    @SerialName("uri#integrity")
    val uriIntegrity: String? = null,
    @SerialName("alt_text")
    val alternativeText: String? = null,
)

@Serializable
data class BackgroundImageMetadata(
    val uri: String,
    @SerialName("uri#integrity")
    val uriIntegrity: String? = null,
)

@Serializable
data class ClaimInformation(
    val path: List<String?>, //may contain null as per the specification
    val display: List<ClaimDisplayMetadata>? = null,
    val mandatory: Boolean? = false,
    val sd: ClaimSdMetadata? = ClaimSdMetadata.Allowed,
    @SerialName("svg_id")
    val svgId: String? = null,
)

@Serializable
data class ClaimDisplayMetadata(
    val locale: String,
    val label: String,
    val description: String? = null,
)

@Serializable
enum class ClaimSdMetadata {

    @SerialName("always")
    Always,

    @SerialName("allowed")
    Allowed,

    @SerialName("never")
    Never
}
