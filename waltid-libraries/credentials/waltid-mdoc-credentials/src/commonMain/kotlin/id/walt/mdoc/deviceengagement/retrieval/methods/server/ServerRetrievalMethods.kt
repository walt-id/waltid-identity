package id.walt.mdoc.deviceengagement.retrieval.methods.server

import cbor.Cbor
import id.walt.mdoc.dataelement.DataElementSerializer
import id.walt.mdoc.dataelement.ListElement
import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.MapKey
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ServerRetrievalMethodsSerializer::class)
data class ServerRetrievalMethods(
    val webAPI: ServerRetrievalInformation? = null,
    val oidc: ServerRetrievalInformation? = null,
) {

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            webAPI?.let {
                put(MapKey("webApi"), it.toListElement())
            }
            oidc?.let {
                put(MapKey("oidc"), it.toListElement())
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
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<ServerRetrievalMethods>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<ServerRetrievalMethods>(cbor)

        /**
         * Convert from CBOR map element
         */
        fun fromMapElement(element: MapElement) = ServerRetrievalMethods(
            webAPI = element.value[MapKey("webApi")]?.let {
                ServerRetrievalInformation.fromListElement(it as ListElement)
            },
            oidc = element.value[MapKey("oidc")]?.let {
                ServerRetrievalInformation.fromListElement(it as ListElement)
            },
        )
    }
}


internal object ServerRetrievalMethodsSerializer : KSerializer<ServerRetrievalMethods> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ServerRetrievalMethods")

    override fun serialize(encoder: Encoder, value: ServerRetrievalMethods) {
        encoder.encodeSerializableValue(DataElementSerializer, value.toMapElement())
    }

    override fun deserialize(decoder: Decoder): ServerRetrievalMethods {
        return ServerRetrievalMethods.fromMapElement(decoder.decodeSerializableValue(DataElementSerializer) as MapElement)
    }
}