package id.walt.issuer.issuance

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class OnboardRequestDid(
    val method: String,
    val config: JsonObject? = null
)

@Serializable
data class OnboardingRequest(
    val key: KeyGenerationRequest = KeyGenerationRequest(
        backend = "jwk",
        keyType =  KeyType.Ed25519
    ),

    val did: OnboardRequestDid = OnboardRequestDid(
        method = "jwk"
    )
)
