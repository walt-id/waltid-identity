@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.issuer2.config

import id.walt.mdoc.schema.MdocsSchemaMappingFunction
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.time.Instant

/**
 * JSON-to-CBOR mapping configuration for mDOC credential issuance.
 * This allows converting JSON values to proper CBOR types (dates, byte strings, etc.)
 * 
 * Based on the enterprise issuer2 implementation, compatible with waltid-mdoc-credentials2.
 */
@JsonClassDiscriminator("type")
@Serializable
sealed class JsonElementToCborMappingConfig {
    abstract fun executeMapping(json: JsonElement): CborElement

    protected fun JsonElement.defaultMapping(): CborElement = MdocsSchemaMappingFunction.run {
        this@defaultMapping.jsonToCborElement()
    }
}

@Serializable
@SerialName("string")
data class JsonStringToCborMappingConfig(
    val conversionType: StringToCborTypeConversion,
) : JsonElementToCborMappingConfig() {
    override fun executeMapping(json: JsonElement): CborElement {
        require(json is JsonPrimitive && json.isString) {
            "Expected to execute $conversionType from json string, but input $json is not a string primitive"
        }

        val value = json.content
        return when (conversionType) {
            StringToCborTypeConversion.STRING_TO_T_DATE -> CborString(Instant.parse(value).toString(), 0u)
            StringToCborTypeConversion.STRING_TO_FULL_DATE -> CborString(LocalDate.parse(value).toString(), 1004u)
            StringToCborTypeConversion.BASE64_STRING_TO_BYTE_STRING -> CborByteString(Base64.decode(value))
            StringToCborTypeConversion.BASE64URL_STRING_TO_BYTE_STRING -> BASE64_URL.decode(value).let { CborByteString(it) }
        }
    }
}

@Serializable
@SerialName("object")
data class JsonObjectToCborMappingConfig(
    val entriesConfigMap: Map<String, JsonElementToCborMappingConfig>,
) : JsonElementToCborMappingConfig() {
    override fun executeMapping(json: JsonElement): CborElement {
        require(json is JsonObject) {
            "Expected to execute conversion from json object, but input $json is not a json object"
        }
        require(json.keys.containsAll(entriesConfigMap.keys)) {
            "Json keys specified in JSON object config map must all exist in input JSON object"
        }

        return CborMap(
            json.map { (key, value) ->
                CborString(key) to (entriesConfigMap[key]?.executeMapping(value) ?: value.defaultMapping())
            }.toMap()
        )
    }
}

@Serializable
@SerialName("array")
data class JsonArrayToCborMappingConfig(
    val arrayConfig: List<JsonElementToCborMappingConfig>,
) : JsonElementToCborMappingConfig() {
    override fun executeMapping(json: JsonElement): CborElement {
        require(json is JsonArray) {
            "Expected to execute conversion from json array, but input $json is not a json array"
        }
        require(json.size == arrayConfig.size) {
            "Json array sizes (input & config) are not equal"
        }

        return CborArray(arrayConfig.zip(json).map { (config, value) -> config.executeMapping(value) })
    }
}

@Serializable
enum class StringToCborTypeConversion {
    @SerialName("stringToFullDate")
    STRING_TO_FULL_DATE,

    @SerialName("stringToTDate")
    STRING_TO_T_DATE,

    @SerialName("base64StringToByteString")
    BASE64_STRING_TO_BYTE_STRING,

    @SerialName("base64UrlStringToByteString")
    BASE64URL_STRING_TO_BYTE_STRING,
}

private val BASE64_URL = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
