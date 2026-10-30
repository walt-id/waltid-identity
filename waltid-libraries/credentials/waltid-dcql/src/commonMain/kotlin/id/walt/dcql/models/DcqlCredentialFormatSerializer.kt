package id.walt.dcql.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Decodes the `format` of a DCQL Credential Query, rejecting identifiers this library does not
 * support with a descriptive error instead of the generic enum decoding failure.
 *
 * Encoding and the descriptor are those of [CredentialFormat], so the wire format and the
 * generated OpenAPI schema are unchanged.
 */
internal object DcqlCredentialFormatSerializer : KSerializer<CredentialFormat> {

    private const val LEGACY_SD_JWT_VC_FORMAT = "vc+sd-jwt"

    override val descriptor: SerialDescriptor = CredentialFormat.serializer().descriptor

    override fun serialize(encoder: Encoder, value: CredentialFormat) =
        encoder.encodeSerializableValue(CredentialFormat.serializer(), value)

    override fun deserialize(decoder: Decoder): CredentialFormat {
        val format = decoder.decodeString()
        val index = descriptor.getElementIndex(format)
        if (index == CompositeDecoder.UNKNOWN_NAME) {
            throw SerializationException(unsupportedFormatMessage(format))
        }
        return CredentialFormat.entries[index]
    }

    private fun unsupportedFormatMessage(format: String): String {
        val recognized = (0 until descriptor.elementsCount).joinToString { descriptor.getElementName(it) }
        val hint = if (format == LEGACY_SD_JWT_VC_FORMAT) {
            " SD-JWT VC Credential Queries must use \"${CredentialFormat.DC_SD_JWT.id.first()}\"."
        } else ""
        return "Unsupported DCQL credential format \"$format\".$hint Recognized formats: $recognized."
    }
}
