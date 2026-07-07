package id.walt.x509

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType

actual suspend fun createX509TestKey(
    keyType: KeyType,
    hasPrivateKey: Boolean,
): Key {
    val key = KeyManager.createKey(
        KeyGenerationRequest(
            backend = "jwk",
            keyType = keyType,
        )
    )
    return if (hasPrivateKey) key else key.getPublicKey()
}
