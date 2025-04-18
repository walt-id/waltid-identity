package id.walt.mdoc.deviceengagement.retrieval.methods.server

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ServerRetrievalInformationSerializer::class)
data class ServerRetrievalInformation(
    val version: UInt = 1U,
    val issuerURL: String,
    val serverRetrievalToken: String,
) {

    /**
     * Convert to CBOR array element
     */
    fun toListElement() = listOf(
        version.toDataElement(),
        issuerURL.toDataElement(),
        serverRetrievalToken.toDataElement(),
    ).toDataElement()

    /**
     * Serialize to CBOR data
     */
    fun toCBOR() = toListElement().toCBOR()

    /**
     * Serialize to CBOR hex string
     */
    fun toCBORHex() = toListElement().toCBORHex()

    companion object {

        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<ServerRetrievalInformation>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<ServerRetrievalInformation>(cbor)

        /**
         * Convert from CBOR array element
         */
        fun fromListElement(element: ListElement): ServerRetrievalInformation {
            require(element.value.size == 3) {
                "ServerRetrievalInformation is CBOR encoded as an array of 3 elements, instead found ${element.value.size} elements"
            }
            require(element.value[0].type == DEType.number) {
                "Version field of ServerRetrievalInformation must be a number (specifically UInt), instead was found to be of type ${element.value[0].type}"
            }
            require(element.value[1].type == DEType.textString) {
                "Issuer URL field of ServerRetrievalInformation must be a text string, instead was found to be of type ${element.value[1].type}"
            }
            require(element.value[2].type == DEType.textString) {
                "Server retrieval token field of ServerRetrievalInformation must be a text string, instead was found to be of type ${element.value[2].type}"
            }
            return ServerRetrievalInformation(
                version = (element.value[0] as NumberElement).value.toLong().toUInt(), //not really correct, but life gives you lemons, you make lemonade
                issuerURL = (element.value[1] as StringElement).value,
                serverRetrievalToken = (element.value[2] as StringElement).value,
            )
        }
    }
}

internal object ServerRetrievalInformationSerializer : KSerializer<ServerRetrievalInformation> {

    override val descriptor = buildClassSerialDescriptor("ServerRetrievalInformation")

    override fun serialize(encoder: Encoder, value: ServerRetrievalInformation) {
        encoder.encodeSerializableValue(DataElementSerializer, value.toListElement())
    }

    override fun deserialize(decoder: Decoder): ServerRetrievalInformation {
        return ServerRetrievalInformation.fromListElement(decoder.decodeSerializableValue(DataElementSerializer) as ListElement)
    }
}