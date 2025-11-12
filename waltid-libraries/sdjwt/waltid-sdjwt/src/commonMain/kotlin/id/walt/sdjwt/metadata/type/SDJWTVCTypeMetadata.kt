@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.sdjwt.metadata.type

import id.walt.sdjwt.utils.JsonDataObject
import id.walt.sdjwt.utils.JsonDataObjectFactory
import id.walt.sdjwt.utils.JsonDataObjectSerializer
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = SDJWTVCTypeMetadataSerializer::class)
sealed class SDJWTVCTypeMetadata : JsonDataObject() {
    abstract val name: String?
    abstract val description: String?
    abstract val extends: String?
    abstract val extendsIntegrity: String?

    companion object : JsonDataObjectFactory<SDJWTVCTypeMetadata>() {
        override fun fromJSON(jsonObject: JsonObject): SDJWTVCTypeMetadata =
            Json.decodeFromJsonElement(SDJWTVCTypeMetadataSerializer, jsonObject)
    }

    @KeepGeneratedSerializer
    @Serializable(with = SDJWTVCTypeMetadataDraft04Serializer::class)
    data class Draft04(
        @SerialName("name")
        override val name: String? = null,
        @SerialName("description")
        override val description: String? = null,
        @SerialName("extends")
        override val extends: String? = null,
        @SerialName("extends#integrity")
        override val extendsIntegrity: String? = null,
        override val customParameters: Map<String, JsonElement>? = mapOf(),
        @SerialName("schema")
        val schema: JsonObject? = null,
        @SerialName("schema_uri")
        val schemaUri: String? = null,
        @SerialName("schema_uri#integrity")
        val schemaUriIntegrity: String? = null,
    ) : SDJWTVCTypeMetadata() {

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

        companion object : JsonDataObjectFactory<Draft04>() {
            override fun fromJSON(jsonObject: JsonObject): Draft04 =
                Json.decodeFromJsonElement(SDJWTVCTypeMetadataDraft04Serializer, jsonObject)
        }

    }

    @KeepGeneratedSerializer
    @Serializable(with = SDJWTVCTypeMetadataDraft13Serializer::class)
    data class Draft13(
        @SerialName("name")
        override val name: String? = null,
        @SerialName("description")
        override val description: String? = null,
        @SerialName("extends")
        override val extends: String? = null,
        @SerialName("extends#integrity")
        override val extendsIntegrity: String? = null,
        override val customParameters: Map<String, JsonElement>? = mapOf(),
        @SerialName("vct")
        val vct: String,
        @SerialName("display")
        val display: List<DisplayInformation>? = null,
        @SerialName("claims")
        val claims: List<ClaimInformation>? = null,
    ) : SDJWTVCTypeMetadata() {

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
            val path: List<String?>, //may contain null
            val display: List<ClaimDisplayMetadata>? = null,
            val mandatory: Boolean? = false,
            val sd: ClaimSdMetadata? = ClaimSdMetadata.Allowed,
            @SerialName("svg_id")
            val svgId: String? = null,
        ) {

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
        }

        override fun toJSON(): JsonObject {
            return Json.encodeToJsonElement(SDJWTVCTypeMetadataDraft13Serializer, this).jsonObject
        }

        companion object : JsonDataObjectFactory<Draft13>() {
            override fun fromJSON(jsonObject: JsonObject): Draft13 =
                Json.decodeFromJsonElement(SDJWTVCTypeMetadataDraft13Serializer, jsonObject)
        }

    }
}

private object SDJWTVCTypeMetadataDraft04Serializer :
    JsonDataObjectSerializer<SDJWTVCTypeMetadata.Draft04>(SDJWTVCTypeMetadata.Draft04.generatedSerializer())

private object SDJWTVCTypeMetadataDraft13Serializer :
    JsonDataObjectSerializer<SDJWTVCTypeMetadata.Draft13>(SDJWTVCTypeMetadata.Draft13.generatedSerializer())


internal object SDJWTVCTypeMetadataSerializer : KSerializer<SDJWTVCTypeMetadata> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("id.walt.sdjwt.SDJWTVCTypeMetadata")

    override fun serialize(encoder: Encoder, value: SDJWTVCTypeMetadata) {
        when (value) {
            is SDJWTVCTypeMetadata.Draft04 -> SDJWTVCTypeMetadataDraft04Serializer.serialize(encoder, value)
            is SDJWTVCTypeMetadata.Draft13 -> SDJWTVCTypeMetadataDraft13Serializer.serialize(encoder, value)
        }
    }

    override fun deserialize(decoder: Decoder): SDJWTVCTypeMetadata {

        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("This serializer can only be used with a JSON decoder")

        val decodedJsonObject = jsonDecoder.decodeJsonElement().jsonObject

        val deserializer = when {

            decodedJsonObject.contains("vct") -> SDJWTVCTypeMetadataDraft13Serializer

            else -> SDJWTVCTypeMetadataDraft04Serializer

        }

        return Json.decodeFromJsonElement(
            deserializer = deserializer,
            element = decodedJsonObject,
        )
    }
}

