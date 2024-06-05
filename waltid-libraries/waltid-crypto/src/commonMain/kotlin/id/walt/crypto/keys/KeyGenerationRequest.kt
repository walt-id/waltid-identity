package id.walt.crypto.keys

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class KeyGenerationRequest(
    val backend: String = "jwk",
    val keyType: KeyType = KeyType.Ed25519,

    var config: JsonObject? = null,
)
