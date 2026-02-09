package id.walt.issuer.services.onboarding.models

import kotlinx.serialization.Serializable

@Serializable
data class IssuerAlternativeNameConfiguration(
    val email: String? = null,
    val uri: String? = null,
)