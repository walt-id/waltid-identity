package id.walt.mdoc.deviceengagement

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = OriginInfoSerializer::class)
data class OriginInfo(
    val category: UInt,
    val type: UInt,
    val details: Map<String, AnyDataElement>,
) {

    init {
        require(details.isNotEmpty()) { "Details field of OriginInfo structure must not be empty" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OriginInfo) return false

        if (category != other.category) return false
        if (type != other.type) return false

        //for some reason the following shenanigans is needed otherwise we get false
        //when comparing two identical (in content) instances. Something fishy going
        //on with the equals() method of the DataElement 🤷
        if (details.size != other.details.size) return false

        details.entries.forEach { curEntry ->
            if (!other.details.containsKey(curEntry.key)) return false

            if (other.details[curEntry.key]!!.toCBORHex() != curEntry.value.toCBORHex()) return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = category.hashCode()
        result = 31 * result + type.hashCode()

        val detailsHash = details.entries.fold(0) { acc, (key, value) ->
            acc + (key.hashCode() * 31 + value.hashCode())
        }

        result = 31 * result + detailsHash
        return result
    }

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = mapOf(
        MapKey("cat") to category.toDataElement(),
        MapKey("type") to type.toDataElement(),
        MapKey("details") to details.toDataElement(),
    ).toDataElement()


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
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<OriginInfo>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<OriginInfo>(cbor)

        /**
         * Convert from CBOR map element
         */
        fun fromMapElement(element: MapElement) = OriginInfo(
            category = (element.value[MapKey("cat")]!! as NumberElement).value.toLong().toUInt(),
            type = (element.value[MapKey("type")]!! as NumberElement).value.toLong().toUInt(),
            details = (element.value[MapKey("details")]!! as MapElement).value
                .entries
                .associate {
                    it.key.str to it.value
                },
        )
    }
}


internal object OriginInfoSerializer : KSerializer<OriginInfo> {

    override val descriptor = buildClassSerialDescriptor("OriginInfo")

    override fun serialize(encoder: Encoder, value: OriginInfo) {
        encoder.encodeSerializableValue(DataElementSerializer, value.toMapElement())
    }

    override fun deserialize(decoder: Decoder): OriginInfo {
        return OriginInfo.fromMapElement(decoder.decodeSerializableValue(DataElementSerializer) as MapElement)
    }
}
