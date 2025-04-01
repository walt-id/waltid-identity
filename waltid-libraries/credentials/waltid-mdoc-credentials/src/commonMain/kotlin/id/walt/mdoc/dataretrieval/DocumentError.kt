package id.walt.mdoc.dataretrieval

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString

/**
 * Error code for unreturned document according to Section 8.3.2.1.2.3 of ISO/IEC 18013-5
 * where it is defined as:
 * DocumentError = {
 *      DocType => ErrorCode
 * }
 *
 * ErrorCode = int; Error code
 */
@Serializable
class DocumentError(
    val docType: String,
    val errorCode: Int,
) {

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = mapOf(
        MapKey(docType) to errorCode.toDataElement(),
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
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<DeviceResponse>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<DeviceResponse>(cbor)

    }
}