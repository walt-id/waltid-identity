package id.walt.issuer.issuance

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import io.ktor.server.plugins.*
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
        keyType = KeyType.Ed25519
    ),

    val did: OnboardRequestDid = OnboardRequestDid(
        method = "jwk"
    )
) {
    init {
        require(key.backend.isNotEmpty()) {
            throw BadRequestException("Key backend in the request body cannot be empty")
        }
        require(did.method.isNotEmpty()) {
            throw BadRequestException("DID method in the request body cannot be empty")
        }
        require(did.method in listOf("jwk", "key", "web")) {
            throw BadRequestException("Unsupported DID method: ${did.method}")
        }
    }
}
