package id.walt.x509.iso

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.x509.createX509TestKey

internal suspend fun createIsoTestKey(
    keyType: KeyType,
    hasPrivateKey: Boolean = true,
): Key = createX509TestKey(keyType, hasPrivateKey)
