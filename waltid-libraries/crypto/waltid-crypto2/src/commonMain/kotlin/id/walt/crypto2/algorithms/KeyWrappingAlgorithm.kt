package id.walt.crypto2.algorithms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface KeyWrappingAlgorithm {
    @Serializable
    @SerialName("builtin")
    data class BuiltIn(val id: String) : KeyWrappingAlgorithm {
        init {
            require(id.isNotBlank()) { "Built-in key-wrapping algorithm ID cannot be blank" }
        }
    }

    @Serializable
    @SerialName("named")
    data class Named(
        val id: String,
        val parameters: Map<String, String> = emptyMap(),
    ) : KeyWrappingAlgorithm {
        init {
            require(id.isNotBlank()) { "Named key-wrapping algorithm ID cannot be blank" }
        }
    }

    @Serializable
    @SerialName("custom")
    data class Custom(
        val id: String,
        val parameters: Map<String, String> = emptyMap(),
    ) : KeyWrappingAlgorithm {
        init {
            require(id.isNotBlank()) { "Custom key-wrapping algorithm ID cannot be blank" }
        }
    }
}
