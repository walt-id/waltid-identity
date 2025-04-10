@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.dataretrieval

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Error code for unreturned document according to Section 8.3.2.1.2.3 of ISO/IEC 18013-5
 * where it is defined as:
 * DocumentError = {
 *      DocType => ErrorCode
 * }
 *
 * ErrorCode = int; Error code
 */
@Serializable(with = DocumentErrorSerializer::class)
data class DocumentError(
    val docTypeToErrorCodeMap: Map<String, Int>,
) {

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = docTypeToErrorCodeMap.map {
        MapKey(it.key) to it.value.toDataElement()
    }.toMap().toDataElement()

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
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<DocumentError>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<DocumentError>(cbor)

        /**
         * Convert from CBOR map element
         */
        fun fromMapElement(element: MapElement): DocumentError {
            require(element.value.keys.all { it.type == MapKeyType.string }) {
                "DocumentError CBOR map keys must all be of type ${MapKeyType.string}"
            }
            require(element.value.values.all { it.type == DEType.number }) {
                "DocumentError CBOR map values must all be of type ${DEType.number}"
            }
            return DocumentError(
                docTypeToErrorCodeMap = element.value.map {
                    it.key.str to (it.value as NumberElement).value.toInt()
                }.toMap(),
            )
        }
    }

}

internal object DocumentErrorSerializer : KSerializer<DocumentError> {

    override val descriptor = buildClassSerialDescriptor("DocumentError")

    override fun serialize(encoder: Encoder, value: DocumentError) {
        encoder.encodeSerializableValue(DataElementSerializer, value.toMapElement())
    }

    override fun deserialize(decoder: Decoder): DocumentError {
        return DocumentError.fromMapElement(decoder.decodeSerializableValue(DataElementSerializer) as MapElement)
    }
}
