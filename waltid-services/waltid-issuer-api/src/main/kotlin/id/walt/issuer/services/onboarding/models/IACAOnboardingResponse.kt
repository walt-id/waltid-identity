package id.walt.issuer.services.onboarding.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class IACAOnboardingResponse(
    val iacaKey: JsonObject,
    val certificatePEM: String,
    val certificateData: IACACertificateData,
)