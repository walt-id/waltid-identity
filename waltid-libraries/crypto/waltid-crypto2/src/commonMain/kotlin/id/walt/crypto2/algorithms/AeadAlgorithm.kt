package id.walt.crypto2.algorithms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface AeadAlgorithm {
    @Serializable
    @SerialName("aes-gcm")
    data class AesGcm(
        val keySizeBits: Int,
        val tagSizeBits: Int = 128,
    ) : AeadAlgorithm {
        init {
            require(keySizeBits == 128 || keySizeBits == 192 || keySizeBits == 256) {
                "AES-GCM key size must be 128, 192, or 256 bits"
            }
            require(tagSizeBits in setOf(96, 104, 112, 120, 128)) { "Unsupported AES-GCM tag size" }
        }
    }

    @Serializable
    @SerialName("chacha20-poly1305")
    data object ChaCha20Poly1305 : AeadAlgorithm

    @Serializable
    @SerialName("custom")
    data class Custom(
        val id: String,
        val parameters: Map<String, String> = emptyMap(),
    ) : AeadAlgorithm {
        init {
            require(id.isNotBlank()) { "Custom AEAD algorithm ID cannot be blank" }
        }
    }
}
