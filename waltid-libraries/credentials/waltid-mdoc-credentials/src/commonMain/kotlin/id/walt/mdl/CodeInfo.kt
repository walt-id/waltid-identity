package id.walt.mdl

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.dataelement.DataElementSerializer
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

//TODO: Fix all fields of this data class to comply with ISO/IEC 18013-2 Annex A when said document becomes available

@Serializable(with = CodeInfoSerializer::class)
data class CodeInfo(
    val code: String,
    val sign: String? = null,
    val value: String? = null,
) {

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            put(MapKey("code"), code.toDataElement())
            sign?.let {
                put(MapKey("sign"), sign.toDataElement())
            }
            value?.let {
                put(MapKey("value"), value.toDataElement())
            }
        }
    )

    /**
     * Serialize to CBOR data
     */
    fun toCBOR() = toMapElement().toCBOR()

    /**
     * Serialize to CBOR hex string
     */
    fun toCBORHex() = toMapElement().toCBORHex()

    /**
     * Serialize to JSON object
     * */
    fun toJSON() = buildJsonObject {
        put("code", Json.encodeToJsonElement(code))
        sign?.let {
            put("sign", Json.encodeToJsonElement(sign))
        }
        value?.let {
            put("value", Json.encodeToJsonElement(value))
        }
    }

    companion object {

        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<CodeInfo>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<CodeInfo>(cbor)

        fun fromMapElement(element: MapElement): CodeInfo {
            require(element.value.containsKey(MapKey("code"))) {
                "CodeInfo CBOR map of DrivingPrivilege must contain string key code"
            }
            require(element.value[MapKey("code")]!!.type == DEType.textString) {
                "Value of code key of CodeInfo CBOR map expected to be of type ${DEType.textString}, but instead was found to be of type ${element.value[MapKey("code")]!!.type}"
            }
            return CodeInfo(
                code = (element.value[MapKey("code")]!! as StringElement).value,
                sign = element.value[MapKey("sign")]?.let {
                    (it as StringElement).value
                },
                value = element.value[MapKey("value")]?.let {
                    (it as StringElement).value
                },
            )
        }

        fun fromJSON(jsonObject: JsonObject) = CodeInfo(
            code = jsonObject.getValue("code").jsonPrimitive.content,
            sign = jsonObject["sign"]?.jsonPrimitive?.content,
            value = jsonObject["value"]?.jsonPrimitive?.content,
        )

    }
}

internal object CodeInfoSerializer: KSerializer<CodeInfo> {

    override val descriptor = buildClassSerialDescriptor("CodeInfo")

    override fun serialize(encoder: Encoder, value: CodeInfo) {
        when(encoder) {
            is JsonEncoder -> {
                encoder.encodeSerializableValue(JsonObject.serializer(), value.toJSON())
            }

            else -> {
                encoder.encodeSerializableValue(DataElementSerializer, value.toMapElement())
            }
        }
    }

    override fun deserialize(decoder: Decoder): CodeInfo {
        return when(decoder) {
            is JsonDecoder -> {
                CodeInfo.fromJSON(decoder.decodeJsonElement().jsonObject)
            }

            else -> {
                CodeInfo.fromMapElement(decoder.decodeSerializableValue(DataElementSerializer) as MapElement)
            }
        }
    }
}