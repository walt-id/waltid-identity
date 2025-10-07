package id.walt.mdoc.objects.dcapi

import id.walt.cose.coseCompliantCbor
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.mdoc.encoding.TransformingSerializerTemplate
import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.CborArray

/**
 * Contains the encryption parameters for a DCAPI request.
 *
 * This structure is serialized as a CBOR array containing the fixed string "dcapi"
 * followed by the [EncryptionParameters] object. This is handled by a custom serializer.
 *
 * @see ISO/IEC TS 18013-7:2024(en), Annex C.2
 *
 * @property parameters The nonce and recipient public key for encryption.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("EncryptionInfo")
@CborArray
data class DCAPIEncryptionInfo(
    /** Should be set to "dcapi" */
    val type: String,
    val encryptionParameters: DCAPIEncryptionParameters
) {
    init {
        require(type == "dcapi")
    }

    /**
     * Custom serializer to handle the specific CBOR array structure `["dcapi", EncryptionParameters]`.
     */
    /*object Serializer : KSerializer<DCAPIEncryptionInfo> {
        private const val TYPE_IDENTIFIER = "dcapi"

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("EncryptionInfo") {
            element<String>("type")
            element<DCAPIEncryptionParameters>("parameters")
        }

        override fun serialize(encoder: Encoder, value: DCAPIEncryptionInfo) {
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, 0, TYPE_IDENTIFIER)
                encodeSerializableElement(descriptor, 1, DCAPIEncryptionParameters.serializer(), value.parameters)
            }
        }

        override fun deserialize(decoder: Decoder): DCAPIEncryptionInfo {
            return decoder.decodeStructure(descriptor) {
                // The structure is a fixed-size array
                require(decodeElements(descriptor) == 2) { "Expected a CBOR array with 2 elements." }

                // 1. Verify the type identifier
                val type = decodeStringElement(descriptor, 0)
                require(type == TYPE_IDENTIFIER) { "Invalid type identifier, expected '$TYPE_IDENTIFIER' but found '$type'." }

                // 2. Decode the parameters
                val parameters = decodeSerializableElement(descriptor, 1, DCAPIEncryptionParameters.serializer())
                DCAPIEncryptionInfo(parameters)
            }
        }
    }*/

    @OptIn(ExperimentalSerializationApi::class)
    object EncryptionInfoBase64UrlSerializer : TransformingSerializerTemplate<DCAPIEncryptionInfo, String>(
        parent = String.serializer(),
        encodeAs = { coseCompliantCbor.encodeToByteArray(it).encodeToBase64Url() },
        decodeAs = { coseCompliantCbor.decodeFromByteArray<DCAPIEncryptionInfo>(it.decodeFromBase64Url()) }
    )
}
