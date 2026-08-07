package id.walt.certificate.x509

import id.walt.crypto2.keys.Key
import id.walt.crypto.keys.Key as Crypto1Key

actual suspend fun PublicKeyInfo.Companion.ofKey(key: Crypto1Key): PublicKeyInfo {
    TODO("Not yet implemented")
}

actual suspend fun PublicKeyInfo.Companion.ofKey(key: Key): PublicKeyInfo {
    TODO("Not yet implemented")
}