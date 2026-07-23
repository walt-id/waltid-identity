package id.walt.x509.iso

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey

internal suspend fun createIsoTestKey(
    keyType: KeyType,
    hasPrivateKey: Boolean = true,
): Key = JWKKey.generate(keyType)
