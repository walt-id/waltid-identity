package id.walt.issuer.services.onboarding.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class IACAOnboardingResponse(
    val iacaKey: JsonObject,
    val certificatePEM: String,
    val certificateData: IACACertificateData,
    val certificateValidationResult: List<CertificateValidationLogLine>,
) {
    @Serializable
    data class CertificateValidationLogLine(
        val validatorId: String,
        val severity: String,
        val message: String
    )
}