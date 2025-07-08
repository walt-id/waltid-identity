package id.walt.mdoc.dataretrieval

import cbor.Cbor
import id.walt.mdoc.cose.COSESign1
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.docrequest.MDocRequest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString

/**
 * Device request structure containing MDoc requests
 */
@Serializable
class DeviceRequest(
    val docRequests: List<MDocRequest>,
    val version: StringElement = "1.0".toDataElement(),
    val deviceRequestInfo: EncodedCBORElement? = null,
    val readerAuthAll: COSESign1? = null,
) {
    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            put(MapKey("version"), version)
            put(MapKey("docRequests"), ListElement(docRequests.map { it.toMapElement() }))
            deviceRequestInfo?.let {
                put(MapKey("deviceRequestInfo"), it)
            }
            readerAuthAll?.let {
                put(MapKey("readerAuthAll"), it.data.toDataElement())
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
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<DeviceRequest>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<DeviceRequest>(cbor)
    }
}
