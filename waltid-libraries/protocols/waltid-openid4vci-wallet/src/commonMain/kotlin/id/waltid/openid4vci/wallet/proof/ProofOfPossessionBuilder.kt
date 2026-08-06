package id.waltid.openid4vci.wallet.proof

import id.walt.crypto.keys.Key
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.openid4vci.prooftypes.Proofs
import id.walt.crypto2.keys.Key as Crypto2Key

/**
 * How the proof JWT identifies its signing key.
 *
 * OpenID4VCI 1.0 §7.2.1 requires exactly one of `kid` or `jwk` in the proof header, so this is a
 * closed choice rather than an independent key identifier plus a boolean - the latter can express
 * "both" and "neither", neither of which is a valid proof.
 */
sealed interface ProofKeyBinding {

    /** `kid` header referencing an externally resolvable key, e.g. a DID verification method. */
    data class KeyId(val keyId: String) : ProofKeyBinding {
        init {
            require(keyId.isNotBlank()) { "Proof key binding kid cannot be blank" }
        }
    }

    /** Embedded `jwk` header carrying the public proof key. */
    data object Jwk : ProofKeyBinding

    /** `kid` header carrying the JWK SHA-256 thumbprint of the proof key. */
    data object JwkThumbprint : ProofKeyBinding
}

/**
 * Builder interface for generating proof of possession for OpenID4VCI credential requests.
 * Implements §7.2 of OpenID4VCI 1.0 specification (Proof Types).
 */
interface ProofOfPossessionBuilder {

    /**
     * Builds a proof of possession for a credential request
     *
     * @param key The cryptographic key to use for signing
     * @param audience The credential issuer URL (aud claim)
     * @param nonce The optional c_nonce obtained from the issuer's Nonce Endpoint
     * @param binding How the proof header identifies [key]
     * @return Proofs object containing the proof
     */
    @Deprecated("Use Crypto2ProofOfPossessionBuilder.buildProof")
    suspend fun buildProof(
        key: Key,
        audience: String,
        nonce: String?,
        binding: ProofKeyBinding,
    ): Proofs

    /**
     * Gets the proof type identifier
     */
    val proofType: String
}

/**
 * Crypto2 counterpart of [ProofOfPossessionBuilder].
 *
 * This is the only proof-building contract for crypto2 keys - callers must not carry their own proof
 * assembly, so the interface has to be able to express every valid OpenID4VCI proof request. That
 * includes an absent [nonce]: the c_nonce is only available when the Credential Issuer advertises a
 * Nonce Endpoint (OpenID4VCI 1.0 §7.2.1.1), so [nonce] is nullable here and the `nonce` claim is
 * omitted from the proof when it is absent. Blank-but-present values remain invalid.
 */
interface Crypto2ProofOfPossessionBuilder {

    /**
     * @param key The crypto2 key to use for signing
     * @param algorithm The JWS algorithm to sign with, negotiated against `proof_signing_alg_values_supported`
     * @param audience The credential issuer URL (aud claim)
     * @param nonce The c_nonce obtained from the issuer's Nonce Endpoint, or null when the issuer has none
     * @param binding How the proof header identifies [key]
     */
    suspend fun buildProof(
        key: Crypto2Key,
        algorithm: JwsAlgorithm,
        audience: String,
        nonce: String?,
        binding: ProofKeyBinding,
    ): Proofs

    /** Gets the proof type identifier */
    val proofType: String
}

/**
 * Common utilities for proof builders
 */
object ProofBuilderUtils {

    /**
     * Gets the current timestamp in seconds since epoch
     */
    fun currentTimestampSeconds(): Long =
        kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000

    /**
     * Validates required parameters for proof generation.
     * An absent nonce is valid (issuers without a Nonce Endpoint), a blank one is not.
     */
    fun validateProofParameters(
        audience: String,
        nonce: String?,
    ) {
        require(audience.isNotBlank()) { "Audience (issuer URL) cannot be blank" }
        require(nonce == null || nonce.isNotBlank()) { "Nonce (c_nonce) cannot be blank" }
    }
}
