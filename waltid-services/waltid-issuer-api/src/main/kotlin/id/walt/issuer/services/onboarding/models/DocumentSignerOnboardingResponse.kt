package id.walt.issuer.services.onboarding.models

import id.walt.issuer.services.onboarding.models.IACAOnboardingResponse.CertificateValidationLogLine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DocumentSignerOnboardingResponse(
    val documentSignerKey: JsonElement,
    val certificatePEM: String,
    val certificateData: DocumentSignerCertificateData,
    val certificateValidationResult: List<CertificateValidationLogLine>,
)