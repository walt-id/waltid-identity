package id.walt.wallet2.mobile

import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeySpec

/**
 * Key algorithms supported by the mobile wallet bootstrap flow.
 */
public enum class MobileWalletKeyType {
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


internal fun MobileWalletKeyType.toKeySpec(): KeySpec = when (this) {
    MobileWalletKeyType.Ed25519 -> KeySpec.Edwards(EdwardsCurve.ED25519)
    MobileWalletKeyType.secp256k1 -> KeySpec.Ec(EcCurve.SECP256K1)
    MobileWalletKeyType.secp256r1 -> KeySpec.Ec(EcCurve.P256)
    MobileWalletKeyType.secp384r1 -> KeySpec.Ec(EcCurve.P384)
    MobileWalletKeyType.secp521r1 -> KeySpec.Ec(EcCurve.P521)
    MobileWalletKeyType.RSA -> KeySpec.Rsa(2048)
    MobileWalletKeyType.RSA3072 -> KeySpec.Rsa(3072)
    MobileWalletKeyType.RSA4096 -> KeySpec.Rsa(4096)
}
