package id.walt.ktorauthnz.security

import kotlinx.serialization.Serializable

@Serializable
data class PasswordHashingConfiguration(

    val pepper: String = "waltid-ktor-authnz",
    var selectedPwHashAlgorithm: PasswordHashingAlgorithm = PasswordHashingAlgorithm.ARGON2,
    var selectedHashConversions: Map<PasswordHashingAlgorithm?, PasswordHashingAlgorithm> = mapOf(
        null to PasswordHashingAlgorithm.ARGON2
    ),
)
