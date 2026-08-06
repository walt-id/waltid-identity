package id.walt.certificate.x509

import id.walt.crypto.keys.Key

actual suspend fun PublicKeyInfo.Companion.ofKey(key: Key): PublicKeyInfo =
    BouncyPublicKeyInfoUtil.publicKeyInfoOfKey(key)