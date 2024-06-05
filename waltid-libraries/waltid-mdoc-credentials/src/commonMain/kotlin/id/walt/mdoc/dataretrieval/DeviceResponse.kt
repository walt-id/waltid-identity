package id.walt.mdoc.dataretrieval

import cbor.Cbor
import id.walt.mdoc.doc.MDoc
import id.walt.mdoc.dataelement.*
import korlibs.crypto.encoding.base64Url
import korlibs.crypto.encoding.toBase64
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Device response data structure containing MDocs presented by device
 */
@Serializable
class DeviceResponse(
    val documents: List<MDoc>,
    val version: StringElement = "1.0".toDE(),
    val status: NumberElement = DeviceResponseStatus.OK.status.toDE(),
    val documentErrors: MapElement? = null
) {
    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            put(MapKey("version"), version)
            put(MapKey("documents"), documents.map { it.toMapElement() }.toDE())
            put(MapKey("status"), status)
            documentErrors?.let {
                put(MapKey("documentErrors"), it)
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
        fun fromCBORBase64URL(cbor: String) = fromCBOR(Base64.UrlSafe.decode(cbor))
    }
}
