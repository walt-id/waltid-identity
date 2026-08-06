package id.walt.certificate.x509

import id.walt.certificate.x509.signum.SignumPublicKeyInfo
import id.walt.crypto.keys.Key

actual suspend fun PublicKeyInfo.Companion.ofKey(key: Key): PublicKeyInfo {
    val signumKey = SignumPublicKeyInfoUtil.publicKeyInfoOfKey(key)
    return SignumPublicKeyInfo.ofCryptoPublicKey(signumKey)
}