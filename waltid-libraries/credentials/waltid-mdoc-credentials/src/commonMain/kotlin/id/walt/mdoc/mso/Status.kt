package id.walt.mdoc.mso

import cbor.Cbor
import id.walt.mdoc.dataelement.DataElementSerializer
import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.MapKey
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = StatusSerializer::class)
data class Status(
    val identifierList: IdentifierListInfo? = null,
    val statusList: StatusListInfo? = null,
) {

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            identifierList?.let {
                put(MapKey("identifier_list"), it.toMapElement())
            }
            statusList?.let {
                put(MapKey("status_list"), it.toMapElement())
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

    companion object {

        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<Status>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<Status>(cbor)

        fun fromMapElement(element: MapElement) = Status(
            identifierList = element.value[MapKey("identifier_list")]?.let {
                IdentifierListInfo.fromMapElement(it as MapElement)
            },
            statusList = element.value[MapKey("status_list")]?.let {
                StatusListInfo.fromMapElement(it as MapElement)
            },
        )

    }
}

internal object StatusSerializer : KSerializer<Status> {

    override val descriptor = buildClassSerialDescriptor("Status")

    override fun serialize(encoder: Encoder, value: Status) {
        encoder.encodeSerializableValue(DataElementSerializer, value.toMapElement())
    }

    override fun deserialize(decoder: Decoder): Status {
        return Status.fromMapElement(decoder.decodeSerializableValue(DataElementSerializer) as MapElement)
    }
}