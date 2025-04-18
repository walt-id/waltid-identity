package id.walt.mdoc.deviceengagement

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ReaderEngagementSerializer::class)
data class ReaderEngagement(
    val version: String = "1.0",
    val optional: Map<Int, AnyDataElement> = emptyMap(),
) {

    init {
        require(optional.keys.none { it in reservedCBORMapIntKeys })
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReaderEngagement) return false

        if (version != other.version) return false

        //for some reason the following shenanigans is needed otherwise we get false
        //when comparing two identical (in content) instances. Something fishy going
        //on with the equals() method of the DataElement 🤷
        if (optional.size != other.optional.size) return false

        optional.entries.forEach { curEntry ->
            if (!other.optional.containsKey(curEntry.key)) return false

            if (other.optional[curEntry.key]!!.toCBORHex() != curEntry.value.toCBORHex()) return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + optional.hashCode()
        return result
    }

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            put(MapKey(0), version.toDataElement())
            optional.takeIf { it.isNotEmpty() }?.entries?.forEach { (key, value) ->
                put(MapKey(key), value)
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

        private val reservedCBORMapIntKeys = setOf(0)

        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<ReaderEngagement>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<ReaderEngagement>(cbor)

        /**
         * Convert from CBOR map element
         */
        fun fromMapElement(element: MapElement) = ReaderEngagement(
            version = element.value[MapKey(0)]!!.let {
                (it as StringElement).value
            },
            optional = element
                .value
                .filter {
                    it.key.int !in reservedCBORMapIntKeys
                }.takeIf {
                    it.isNotEmpty()
                }?.entries?.associate { it.key.int to it.value } ?: emptyMap(),
        )
    }
}

internal object ReaderEngagementSerializer : KSerializer<ReaderEngagement> {

    override val descriptor = buildClassSerialDescriptor("ReaderEngagement")

    override fun serialize(encoder: Encoder, value: ReaderEngagement) {
        encoder.encodeSerializableValue(DataElementSerializer, value.toMapElement())
    }

    override fun deserialize(decoder: Decoder): ReaderEngagement {
        return ReaderEngagement.fromMapElement(decoder.decodeSerializableValue(DataElementSerializer) as MapElement)
    }
}
