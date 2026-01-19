@file:OptIn(ExperimentalTime::class)

package id.walt.issuer.services.onboarding.models

import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

@Serializable
data class DocumentSignerOnboardingRequest(
    val iacaSigner: IACASignerData,
    val certificateData: DocumentSignerCertificateRequestData,
    val ecKeyGenRequestParams: KeyGenerationRequestParameters = KeyGenerationRequestParameters(
        backend = "jwk",
        config = null,
    ),
)
