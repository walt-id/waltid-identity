package id.walt.verifier.oidc.models.presentedcredentials

import kotlinx.serialization.Serializable

@Serializable
enum class PresentedCredentialsViewMode {
    simple,
    verbose;
}