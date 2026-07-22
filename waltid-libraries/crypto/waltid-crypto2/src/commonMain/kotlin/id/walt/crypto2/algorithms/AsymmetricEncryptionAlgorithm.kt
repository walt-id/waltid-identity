@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.crypto2.algorithms

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
sealed interface AsymmetricEncryptionAlgorithm {

    @Serializable
    @SerialName("rsa-oaep")
    data class RsaOaep(
        val digest: DigestAlgorithm,
        val mgfDigest: DigestAlgorithm = digest,
    ) : AsymmetricEncryptionAlgorithm

    @Serializable
    @SerialName("rsa-pkcs1")
    data object RsaPkcs1 : AsymmetricEncryptionAlgorithm

    @Serializable
    @SerialName("custom")
    data class Custom(
        val id: String,
        val parameters: Map<String, String> = emptyMap(),
    ) : AsymmetricEncryptionAlgorithm {
        init {
            require(id.isNotBlank()) { "Custom encryption algorithm ID cannot be blank" }
        }
    }
}
