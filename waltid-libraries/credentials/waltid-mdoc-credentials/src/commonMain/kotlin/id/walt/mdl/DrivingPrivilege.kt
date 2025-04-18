package id.walt.mdl

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

//TODO: Fix vehicle category code as per ISO/IEC 18013-1 Annex B when said document becomes available

@Serializable(with = DrivingPrivilegeSerializer::class)
data class DrivingPrivilege(
    val vehicleCategoryCode: String,
    val issueDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
    val codes: List<CodeInfo>? = null,
) {

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            put(MapKey("vehicle_category_code"), vehicleCategoryCode.toDataElement())
            issueDate?.let {
                put(MapKey("issue_date"), it.toDataElement())
            }
            expiryDate?.let {
                put(MapKey("expiry_date"), it.toDataElement())
            }
            codes?.let {
                put(MapKey("codes"), ListElement(it.map { it.toMapElement() }))
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
        put("vehicle_category_code", Json.encodeToJsonElement(vehicleCategoryCode))
        issueDate?.let {
            put("issue_date", Json.encodeToJsonElement(issueDate))
        }
        expiryDate?.let {
            put("expiry_date", Json.encodeToJsonElement(expiryDate))
        }
        codes?.let {
            put("codes", Json.encodeToJsonElement(codes))
        }
    }

    companion object {

        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<DrivingPrivilege>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<DrivingPrivilege>(cbor)

        fun fromMapElement(element: MapElement): DrivingPrivilege {
            require(element.value.containsKey(MapKey("vehicle_category_code"))) {
                "DrivingPrivilege CBOR map must contain string key vehicle_category_code"
            }
            require(element.value[MapKey("vehicle_category_code")]!!.type == DEType.textString) {
                "vehicle_category_code key of DrivingPrivilege CBOR map expected to be of type ${DEType.textString}, but " +
                        "instead was found to be of type ${element.value[MapKey("vehicle_category_code")]!!.type}"
            }
            return DrivingPrivilege(
                vehicleCategoryCode = (element.value[MapKey("vehicle_category_code")]!! as StringElement).value,
                issueDate = element.value[MapKey("issue_date")]?.let {
                    (it as FullDateElement).value
                },
                expiryDate = element.value[MapKey("expiry_date")]?.let {
                    (it as FullDateElement).value
                },
                codes = element.value[MapKey("codes")]?.let {
                    (it as ListElement).value.map { CodeInfo.fromMapElement(it as MapElement) }
                },
            )
        }

        fun fromJSON(jsonObject: JsonObject) = DrivingPrivilege(
            vehicleCategoryCode = jsonObject.getValue("vehicle_category_code").jsonPrimitive.content,
            issueDate = jsonObject["issue_date"]?.let {
                Json.decodeFromJsonElement<LocalDate>(it)
            },
            expiryDate = jsonObject["expiry_date"]?.let {
                Json.decodeFromJsonElement<LocalDate>(it)
            },
            codes = jsonObject["codes"]?.let {
                Json.decodeFromJsonElement<List<CodeInfo>>(it)
            },
        )

    }
}

internal object DrivingPrivilegeSerializer: KSerializer<DrivingPrivilege> {

    override val descriptor = buildClassSerialDescriptor("DrivingPrivilege")

    override fun serialize(encoder: Encoder, value: DrivingPrivilege) {
        when(encoder) {
            is JsonEncoder -> {
                encoder.encodeSerializableValue(JsonObject.serializer(), value.toJSON())
            }

            else -> {
                encoder.encodeSerializableValue(DataElementSerializer, value.toMapElement())
            }
        }
    }

    override fun deserialize(decoder: Decoder): DrivingPrivilege {
        return when(decoder) {
            is JsonDecoder -> {
                DrivingPrivilege.fromJSON(decoder.decodeJsonElement().jsonObject)
            }

            else -> {
                DrivingPrivilege.fromMapElement(decoder.decodeSerializableValue(DataElementSerializer) as MapElement)
            }
        }
    }
}

