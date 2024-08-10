package id.walt.issuer.issuance

import id.walt.commons.web.WebException
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import io.ktor.http.*
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

fun validateOnboardingRequest(request: OnboardingRequest) {
    require(request.key.backend.isNotEmpty()) {
        throw WebException(HttpStatusCode.BadRequest, "Key backend must not be empty")
    }
    require(request.did.method.isNotEmpty()) {
        throw WebException(HttpStatusCode.BadRequest, "DID method must not be empty")
    }
    require(request.did.method in listOf("jwk", "key", "web")) {
        throw WebException(HttpStatusCode.BadRequest, "Unsupported DID method: ${request.did.method}")
    }
}
