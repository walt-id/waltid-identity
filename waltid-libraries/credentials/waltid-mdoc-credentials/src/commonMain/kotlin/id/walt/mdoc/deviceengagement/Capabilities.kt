package id.walt.mdoc.deviceengagement

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.dataelement.DataElementSerializer
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = CapabilitiesSerializer::class)
data class Capabilities(
    val handoverSessionEstablishmentSupport: Boolean? = null,
    val readerAuthAllSupport: Boolean? = null,
    val optional: Map<Int, AnyDataElement> = emptyMap(),
) {

    init {
        require(optional.keys.none { it in reservedCBORMapIntKeys })

        /**
         * The HandoverSessionEstablishmentSupport element in the Capabilities structure may be
         * used by the mdoc to indicate whether it supports receiving the SessionEstablishment message
         * during Negotiated Handover as defined in clause 8.2.2.4. If HandoverSessionEstablishmentAll is  <-- most likely typo error in the specification
         * present its value shall be True.
         * */
        handoverSessionEstablishmentSupport?.let {
            require(handoverSessionEstablishmentSupport == true) {
                "When the HandoverSessionEstablishmentSupport field of the Capabilities structure is defined, its value must be set to true."
            }
        }

        /**
         * The ReaderAuthAllSupport element in the Capabilities structure may be used by the mdoc to
         * indicate whether it supports receiving the ReaderAuthAll structure in the mdoc request as defined in
         * clause 8.3.2.1.2.1. If ReaderAuthAllSupport is present its value shall be True.
         * */
        readerAuthAllSupport?.let {
            require(readerAuthAllSupport == true) {
                "When the ReaderAuthAllSupport field of the Capabilities structure is defined, its value must be set to true."
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Capabilities) return false

        if (handoverSessionEstablishmentSupport != other.handoverSessionEstablishmentSupport) return false
        if (readerAuthAllSupport != other.readerAuthAllSupport) return false

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
        var result = handoverSessionEstablishmentSupport?.hashCode() ?: 0
        result = 31 * result + (readerAuthAllSupport?.hashCode() ?: 0)
        result = 31 * result + optional.hashCode()
        return result
    }

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            handoverSessionEstablishmentSupport?.let {
                put(MapKey(2), handoverSessionEstablishmentSupport.toDataElement())
            }
            readerAuthAllSupport?.let {
                put(MapKey(3), readerAuthAllSupport.toDataElement())
            }
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

        private val reservedCBORMapIntKeys = setOf(2, 3)

        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<Capabilities>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<Capabilities>(cbor)

        /**
         * Convert from CBOR map element
         */
        fun fromMapElement(element: MapElement) = Capabilities(
            handoverSessionEstablishmentSupport = element.value[MapKey(2)]?.let {
                (it as BooleanElement).value
            },
            readerAuthAllSupport = element.value[MapKey(3)]?.let {
                (it as BooleanElement).value
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

internal object CapabilitiesSerializer : KSerializer<Capabilities> {

    override val descriptor = buildClassSerialDescriptor("Capabilities")

    override fun serialize(encoder: Encoder, value: Capabilities) {
        encoder.encodeSerializableValue(DataElementSerializer, value.toMapElement())
    }

    override fun deserialize(decoder: Decoder): Capabilities {
        return Capabilities.fromMapElement(decoder.decodeSerializableValue(DataElementSerializer) as MapElement)
    }
}