package id.walt.certificate.x509

import id.walt.certificate.x509.signum.SignumPublicKeyInfo
import id.walt.crypto2.keys.Key
import id.walt.crypto.keys.Key as Crypto1Key

actual suspend fun PublicKeyInfo.Companion.ofKey(key: Crypto1Key): PublicKeyInfo {
    val signumKey = SignumPublicKeyInfoUtil.publicKeyInfoOfKey(key)
    return SignumPublicKeyInfo.ofCryptoPublicKey(signumKey)
}

actual suspend fun PublicKeyInfo.Companion.ofKey(key: Key): PublicKeyInfo {
    TODO("Not yet implemented")
}