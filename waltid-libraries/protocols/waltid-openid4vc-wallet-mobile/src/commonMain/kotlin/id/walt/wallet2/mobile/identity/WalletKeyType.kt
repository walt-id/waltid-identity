package id.walt.wallet2.mobile.identity

import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeySpec

/**
 * Key algorithms supported by the mobile wallet bootstrap flow.
 */
public enum class WalletKeyType {
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


internal fun WalletKeyType.toKeySpec(): KeySpec = when (this) {
    WalletKeyType.Ed25519 -> KeySpec.Edwards(EdwardsCurve.ED25519)
    WalletKeyType.secp256k1 -> KeySpec.Ec(EcCurve.SECP256K1)
    WalletKeyType.secp256r1 -> KeySpec.Ec(EcCurve.P256)
    WalletKeyType.secp384r1 -> KeySpec.Ec(EcCurve.P384)
    WalletKeyType.secp521r1 -> KeySpec.Ec(EcCurve.P521)
    WalletKeyType.RSA -> KeySpec.Rsa(2048)
    WalletKeyType.RSA3072 -> KeySpec.Rsa(3072)
    WalletKeyType.RSA4096 -> KeySpec.Rsa(4096)
}
