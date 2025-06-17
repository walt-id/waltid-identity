package id.walt.issuer.services.onboarding.models

import kotlinx.serialization.Serializable

@Serializable
data class IACAOnboardingRequest(
    val certificateData: IACACertificateData,
    val ecKeyGenRequestParams: KeyGenerationRequestParams = KeyGenerationRequestParams(
        backend = "jwk",
        config = null,
    ),
)