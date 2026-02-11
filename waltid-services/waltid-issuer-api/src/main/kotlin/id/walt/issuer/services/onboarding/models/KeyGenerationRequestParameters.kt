package id.walt.issuer.services.onboarding.models

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class KeyGenerationRequestParameters(
    val backend: String,
    val keyType: KeyType = KeyType.secp256r1,
    val config: JsonObject? = null,
) {

    fun toKeyGenerationRequest() = KeyGenerationRequest(
        backend = backend,
        keyType = keyType,
        config = config,
    )
}
