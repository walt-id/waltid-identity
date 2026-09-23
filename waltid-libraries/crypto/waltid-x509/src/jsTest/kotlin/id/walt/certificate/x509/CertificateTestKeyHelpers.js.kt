package id.walt.certificate.x509

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType

actual suspend fun createCertificateTestKey(keyType: KeyType): Key =
    KeyManager.createKey(
        KeyGenerationRequest(
            backend = "jwk",
            keyType = keyType,
        )
    )
