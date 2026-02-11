package id.walt.issuer.services.onboarding.models

import id.walt.crypto.keys.KeyType
import kotlinx.serialization.Serializable

@Serializable
data class IACAOnboardingRequest(
    val certificateData: IACACertificateRequestData,
    val ecKeyGenRequestParams: KeyGenerationRequestParameters = KeyGenerationRequestParameters(
        backend = "jwk",
        keyType = KeyType.secp256r1,
        config = null,
    ),
)