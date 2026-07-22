@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.crypto2.algorithms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
sealed interface SignatureAlgorithm {

    @Serializable
    @SerialName("ecdsa")
    data class Ecdsa(
        val digest: DigestAlgorithm,
        val encoding: EcdsaSignatureEncoding = EcdsaSignatureEncoding.IEEE_P1363,
    ) : SignatureAlgorithm

    @Serializable
    @SerialName("eddsa")
    data object EdDsa : SignatureAlgorithm

    @Serializable
    @SerialName("rsa-pkcs1")
    data class RsaPkcs1(val digest: DigestAlgorithm) : SignatureAlgorithm

    @Serializable
    @SerialName("rsa-pss")
    data class RsaPss(
        val digest: DigestAlgorithm,
        val mgfDigest: DigestAlgorithm = digest,
        val saltLengthBytes: Int? = null,
    ) : SignatureAlgorithm {
        init {
            require(saltLengthBytes == null || saltLengthBytes >= 0) { "RSA-PSS salt length cannot be negative" }
        }
    }

    @Serializable
    @SerialName("custom")
    data class Custom(
        val id: String,
        val parameters: Map<String, String> = emptyMap(),
    ) : SignatureAlgorithm {
        init {
            require(id.isNotBlank()) { "Custom signature algorithm ID cannot be blank" }
        }
    }
}

@Serializable
enum class EcdsaSignatureEncoding {
    IEEE_P1363,
    DER,
}
