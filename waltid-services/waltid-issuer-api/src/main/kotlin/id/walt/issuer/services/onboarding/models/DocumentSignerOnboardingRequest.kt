@file:OptIn(ExperimentalTime::class)

package id.walt.issuer.services.onboarding.models

import id.walt.crypto.keys.KeyType
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

@Serializable
data class DocumentSignerOnboardingRequest(
    val iacaSigner: IACASignerData,
    val certificateData: DocumentSignerCertificateRequestData,
    val ecKeyGenRequestParams: KeyGenerationRequestParameters = KeyGenerationRequestParameters(
        backend = "jwk",
        keyType = KeyType.secp256r1,
        config = null,
    ),
)
