package id.walt.issuer.services.onboarding.models

import kotlinx.serialization.Serializable

@Serializable
data class IssuerAlternativeNameConfiguration(
    val email: String? = null,
    val uri: String? = null,
) {

    init {
        require(!email.isNullOrBlank() || !uri.isNullOrBlank()) {
            "At least one of 'email' or 'uri' must be specified"
        }
    }
}