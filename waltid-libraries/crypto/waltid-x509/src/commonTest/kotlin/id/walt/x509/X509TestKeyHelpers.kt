package id.walt.x509

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType

expect suspend fun createX509TestKey(
    keyType: KeyType,
    hasPrivateKey: Boolean = true,
): Key
