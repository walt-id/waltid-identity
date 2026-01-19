package id.walt.issuer.services.onboarding.models

import kotlinx.serialization.Serializable

@Serializable
data class IACAOnboardingRequest(
    val certificateData: IACACertificateRequestData,
    val ecKeyGenRequestParams: KeyGenerationRequestParameters = KeyGenerationRequestParameters(
        backend = "jwk",
        config = null,
    ),
)