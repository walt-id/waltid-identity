package id.walt.crypto2.algorithms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface KeyAgreementAlgorithm {

    @Serializable
    @SerialName("ecdh")
    data object Ecdh : KeyAgreementAlgorithm

    @Serializable
    @SerialName("xdh")
    data object Xdh : KeyAgreementAlgorithm

    @Serializable
    @SerialName("custom")
    data class Custom(
        val id: String,
        val parameters: Map<String, String> = emptyMap(),
    ) : KeyAgreementAlgorithm {
        init {
            require(id.isNotBlank()) { "Custom key-agreement algorithm ID cannot be blank" }
        }
    }

    @Serializable
    @SerialName("named")
    data class Named(
        val id: String,
        val parameters: Map<String, String> = emptyMap(),
    ) : KeyAgreementAlgorithm {
        init {
            require(id.isNotBlank()) { "Named key-agreement algorithm ID cannot be blank" }
        }
    }
}
