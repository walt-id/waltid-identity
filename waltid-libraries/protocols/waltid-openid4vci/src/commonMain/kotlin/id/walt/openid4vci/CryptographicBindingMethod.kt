package id.walt.openid4vci

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Cryptographic Binding Methods (OpenID4VCI 1.0).
 */
@Serializable(with = CryptographicBindingMethodSerializer::class)
sealed class CryptographicBindingMethod(val value: String) {
    object Jwk : CryptographicBindingMethod("jwk")
    object CoseKey : CryptographicBindingMethod("cose_key")
    data class Did(val method: String) : CryptographicBindingMethod("did:$method") {
        init {
            require(method.isNotBlank()) { "DID method name must not be blank" }
            require(!method.contains(":")) { "DID method name must not include a method-specific id" }
        }
    }

    companion object {
        val DidKey = Did("key")
        val DidJwk = Did("jwk")
        val DidWeb = Did("web")
        val DidEbsi = Did("ebsi")

        fun fromValue(value: String): CryptographicBindingMethod {
            return when {
                value == Jwk.value -> Jwk
                value == CoseKey.value -> CoseKey
                value.startsWith("did:") -> {
                    val method = value.removePrefix("did:")
                    Did(method)
                }
                else -> throw IllegalArgumentException("Unsupported cryptographic binding method: $value")
            }
        }
    }
}

object CryptographicBindingMethodSerializer : KSerializer<CryptographicBindingMethod> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CryptographicBindingMethod", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CryptographicBindingMethod) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): CryptographicBindingMethod {
        return CryptographicBindingMethod.fromValue(decoder.decodeString())
    }
}
