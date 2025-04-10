package id.walt.mdoc.dataretrieval

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.doc.MDoc
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Device response data structure containing MDocs presented by device
 *
 * TODO: Strictly from a standard's perspective, the "documents" field is optional.
 */
@Serializable(with = DeviceResponseSerializer::class)
data class DeviceResponse(
    val documents: List<MDoc>,
    val version: StringElement = "1.0".toDataElement(),
    val status: NumberElement = DeviceResponseStatus.OK.status.toDataElement(),
    val documentErrors: List<DocumentError>? = null,
) {

    init {

        require(documents.isNotEmpty()) {
            "When a List<MDoc> is defined in a DeviceResponse structure, it must not be empty"
        }

        documentErrors?.let {
            require(documentErrors.isNotEmpty()) {
                "When a List<DocumentError> is defined in a DeviceResponse structure, it must not be empty"
            }
        }
    }

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            put(MapKey("version"), version)
            put(MapKey("documents"), documents.map { it.toMapElement() }.toDataElement())
            put(MapKey("status"), status)
            documentErrors?.let { docErrorList ->
                put(MapKey("documentErrors"), docErrorList.map { it.toMapElement() }.toDataElement())
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
     * Serialize to CBOR base64 url-encoded string
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun toCBORBase64URL() = Base64.UrlSafe.encode(toCBOR())

    companion object {
        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<DeviceResponse>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<DeviceResponse>(cbor)

        @OptIn(ExperimentalEncodingApi::class)
        fun fromCBORBase64URL(cbor: String) =
            fromCBOR(Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(cbor))

        fun fromMapElement(element: MapElement) = DeviceResponse(
            documents = (element.value[MapKey("documents")] as ListElement).value.map {
                MDoc.fromMapElement(
                    it as MapElement
                )
            },
            version = element.value[MapKey("version")] as StringElement,
            status = element.value[MapKey("status")] as NumberElement,
            documentErrors = element.value[MapKey("documentErrors")]?.let { docErrorsDataElement ->
                (docErrorsDataElement as ListElement).value.map {
                    DocumentError.fromMapElement(it as MapElement)
                }
            },
        )
    }
}

internal object DeviceResponseSerializer : KSerializer<DeviceResponse> {

    override val descriptor = buildClassSerialDescriptor("DeviceResponse")

    override fun serialize(encoder: Encoder, value: DeviceResponse) {
        encoder.encodeSerializableValue(DataElementSerializer, value.toMapElement())
    }

    override fun deserialize(decoder: Decoder): DeviceResponse {
        return DeviceResponse.fromMapElement(decoder.decodeSerializableValue(DataElementSerializer) as MapElement)
    }
}
