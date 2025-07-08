package id.walt.issuer.services.onboarding.models

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class KeyGenerationRequestParams(
    val backend: String,
    val config: JsonObject? = null,
) {

    fun toKeyGenerationRequest() = KeyGenerationRequest(
        backend = backend,
        keyType = KeyType.secp256r1,
        config = config,
    )
}
