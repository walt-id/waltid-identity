package id.walt.webwallet.web.model

import id.walt.crypto.keys.KeyType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class KeyGenerationRequest(
    val backend: String = "jwk",
    val config: JsonObject,

    val keyType: KeyType = KeyType.Ed25519,
)

fun x() {

}
