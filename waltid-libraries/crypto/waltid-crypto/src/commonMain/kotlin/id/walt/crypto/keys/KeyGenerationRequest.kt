package id.walt.crypto.keys

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class KeyGenerationRequest(
    val backend: String = "jwk",
    val keyType: KeyType = KeyType.Ed25519,
    val name: String? = null,
    var config: JsonObject? = null,
) {
    fun getConfigAsMap() = config?.toMap() ?: emptyMap()
}
