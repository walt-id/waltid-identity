package id.walt.ktorauthnz.methods.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("jwt-config")
data class JwtAuthConfiguration(
    val verifyKey: String,
    val identifyClaim: String = "sub",
) : AuthMethodConfiguration
