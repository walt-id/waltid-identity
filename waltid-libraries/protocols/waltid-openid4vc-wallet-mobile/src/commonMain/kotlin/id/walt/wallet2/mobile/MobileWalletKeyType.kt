package id.walt.wallet2.mobile

import id.walt.crypto.keys.KeyType

enum class MobileWalletKeyType {
    Ed25519,
    secp256k1,
    secp256r1,
    secp384r1,
    secp521r1,
    RSA,
    RSA3072,
    RSA4096,
}

internal fun MobileWalletKeyType.toKeyType(): KeyType = when (this) {
    MobileWalletKeyType.Ed25519 -> KeyType.Ed25519
    MobileWalletKeyType.secp256k1 -> KeyType.secp256k1
    MobileWalletKeyType.secp256r1 -> KeyType.secp256r1
    MobileWalletKeyType.secp384r1 -> KeyType.secp384r1
    MobileWalletKeyType.secp521r1 -> KeyType.secp521r1
    MobileWalletKeyType.RSA -> KeyType.RSA
    MobileWalletKeyType.RSA3072 -> KeyType.RSA3072
    MobileWalletKeyType.RSA4096 -> KeyType.RSA4096
}
