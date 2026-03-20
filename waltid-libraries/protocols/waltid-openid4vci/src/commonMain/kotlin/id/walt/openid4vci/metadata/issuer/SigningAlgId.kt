package id.walt.openid4vci.metadata.issuer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull

/**
 * Credential signing algorithm identifiers (OpenID4VCI 1.0).
 */
@Serializable(with = SigningAlgIdSerializer::class)
sealed class SigningAlgId {
    data class Jose(val value: String) : SigningAlgId()
    data class LdSuite(val value: String) : SigningAlgId()
    data class CoseValue(val value: Int) : SigningAlgId()
    data class CoseName(val value: String) : SigningAlgId()

    companion object {
        fun jose(value: String): SigningAlgId = Jose(value)
        fun ldSuite(value: String): SigningAlgId = LdSuite(value)
        fun coseValue(value: Int): SigningAlgId = CoseValue(value)
        fun coseName(value: String): SigningAlgId = CoseName(value)
    }
}

internal object SigningAlgIdSerializer : KSerializer<SigningAlgId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SigningAlgId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SigningAlgId) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("SigningAlgId only supports JSON encoding")
        val element = when (value) {
            is SigningAlgId.Jose -> JsonPrimitive(value.value)
            is SigningAlgId.LdSuite -> JsonPrimitive(value.value)
            is SigningAlgId.CoseName -> JsonPrimitive(value.value)
            is SigningAlgId.CoseValue -> JsonPrimitive(value.value)
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): SigningAlgId {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("SigningAlgId only supports JSON decoding")
        val element = jsonDecoder.decodeJsonElement()
        val primitive = element as? JsonPrimitive
            ?: throw SerializationException("SigningAlgId must be a JSON primitive")
        return when {
            primitive.isString -> SigningAlgId.Jose(primitive.content)
            primitive.intOrNull != null -> SigningAlgId.CoseValue(primitive.int)
            else -> throw SerializationException("Unsupported signing algorithm value: $element")
        }
    }
}

internal object SigningAlgIdSetSerializer : KSerializer<Set<SigningAlgId>> {
    private val delegate = kotlinx.serialization.builtins.SetSerializer(SigningAlgIdSerializer)
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: Set<SigningAlgId>) =
        delegate.serialize(encoder, value)
    override fun deserialize(decoder: Decoder): Set<SigningAlgId> =
        delegate.deserialize(decoder)
}
