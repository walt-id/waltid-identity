package id.walt.openid4vp.clientidprefix

import id.walt.crypto.keys.KeyType

/**
 * Enforces the JOSE algorithm and certificate public-key binding before signature verification.
 *
 * This check is intentionally independent of the crypto backend: some backends retry malformed
 * ECDSA JWS signatures using an algorithm inferred from the key type, which must never allow the
 * Request Object's declared `alg` to be ignored.
 */
internal fun requestObjectAlgorithmMatchesKey(algorithm: String?, keyType: KeyType): Boolean =
    algorithm == keyType.jwsAlg
