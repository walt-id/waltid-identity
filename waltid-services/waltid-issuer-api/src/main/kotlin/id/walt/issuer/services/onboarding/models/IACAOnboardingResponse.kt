package id.walt.issuer.services.onboarding.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class IACAOnboardingResponse(
    val iacaKey: JsonElement,
    val certificatePEM: String,
    val certificateData: IACACertificateData,
)