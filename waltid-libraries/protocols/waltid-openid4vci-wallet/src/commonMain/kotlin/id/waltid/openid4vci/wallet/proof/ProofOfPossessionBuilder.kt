package id.waltid.openid4vci.wallet.proof

import id.walt.crypto.keys.Key
import id.walt.openid4vci.prooftypes.Proofs

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
     * @param nonce The c_nonce from the issuer
     * @return Proofs object containing the proof
     */
    suspend fun buildProof(
        key: Key,
        audience: String,
        nonce: String,
    ): Proofs

    /**
     * Gets the proof type identifier
     */
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
     * Validates required parameters for proof generation
     */
    fun validateProofParameters(
        audience: String,
        nonce: String,
    ) {
        require(audience.isNotBlank()) { "Audience (issuer URL) cannot be blank" }
        require(nonce.isNotBlank()) { "Nonce (c_nonce) cannot be blank" }
    }
}
