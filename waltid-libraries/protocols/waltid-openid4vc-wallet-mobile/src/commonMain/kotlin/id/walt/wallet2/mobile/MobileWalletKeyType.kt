package id.walt.wallet2.mobile

import id.walt.crypto.keys.KeyType

/**
 * Key algorithms supported by the mobile wallet bootstrap flow.
 */
enum class MobileWalletKeyType {
    /** Ed25519 signing key. */
    Ed25519,

    /** secp256k1 elliptic-curve signing key. */
    secp256k1,

    /** NIST P-256 elliptic-curve signing key. */
    secp256r1,

    /** NIST P-384 elliptic-curve signing key. */
    secp384r1,

    /** NIST P-521 elliptic-curve signing key. */
    secp521r1,

    /** Default RSA signing key size used by the core crypto layer. */
    RSA,

    /** RSA signing key with a 3072-bit modulus. */
    RSA3072,

    /** RSA signing key with a 4096-bit modulus. */
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
