package id.walt.x509.iso

import id.walt.certificate.x509.createCertificateTestKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType

internal suspend fun createIsoTestKey(
    keyType: KeyType,
    hasPrivateKey: Boolean = true,
): Key {
    val key = createCertificateTestKey(keyType)
    return if (hasPrivateKey) key else key.getPublicKey()
}
