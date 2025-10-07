package id.walt.webwallet.service.keys

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class SingleKeyResponse(
    val algorithm: String,
    val cryptoProvider: String,
    val keyId: KeyId,
    val keyPair: JsonObject,
    val keysetHandle: JsonElement,
    val name: String? = null,
) {
    @Serializable
    data class KeyId(val id: String)
}
