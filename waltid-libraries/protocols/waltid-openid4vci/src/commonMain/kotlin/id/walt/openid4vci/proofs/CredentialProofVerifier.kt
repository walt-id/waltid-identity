package id.walt.openid4vci.proofs

import id.walt.crypto2.keys.Key
import id.walt.openid4vci.proofs.attestation.KeyAttestationVerificationOptions
import id.walt.openid4vci.proofs.attestation.VerifiedKeyAttestation
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.metadata.issuer.BatchCredentialIssuance
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.requests.credential.CredentialRequest
import kotlinx.serialization.json.JsonObject

fun interface CredentialProofVerifier {
    suspend fun verify(
        credentialRequest: CredentialRequest,
        credentialConfiguration: CredentialConfiguration,
        context: CredentialProofValidationContext,
    ): CredentialProofVerificationResult
}

/** Evidence is counted per submitted proof; bindings are counted per credential to issue. */
data class CredentialProofVerificationResult(
    val proofs: List<VerifiedCredentialProof>,
    val bindings: List<VerifiedCredentialBinding> = proofs.mapIndexed { index, proof -> proof.binding(index) },
)

/** A selected credential key. Identifiers belong to this key, never to the attester. */
data class VerifiedCredentialBinding(
    val holderKey: Key,
    val holderKid: String? = null,
    val holderDid: String? = null,
    val proofIndexes: Set<Int> = emptySet(),
)

data class CredentialProofValidationContext(
    val credentialIssuer: String,
    val clientId: String? = null,
    val anonymousPreAuthorizedAccess: Boolean = false,
    val nonceValidation: CredentialNonceValidationContext? = null,
    val batchCredentialIssuance: BatchCredentialIssuance? = null,
    val keyAttestation: KeyAttestationVerificationOptions? = null,
) {
    init {
        require(credentialIssuer.isNotBlank()) { "credentialIssuer must not be blank" }
        clientId?.let { require(it.isNotBlank()) { "clientId must not be blank" } }
    }
}

data class VerifiedCredentialProof(
    val proofType: String,
    val jwt: String,
    val algorithm: String,
    val header: JsonObject,
    val payload: JsonObject,
    val holderKey: Key,
    val holderKid: String?,
    val holderDid: String?,
    val nonce: String?,
    val keyAttestation: VerifiedKeyAttestation? = null,
) {
    fun binding(proofIndex: Int = 0): VerifiedCredentialBinding =
        VerifiedCredentialBinding(holderKey, holderKid, holderDid, setOf(proofIndex))
}

class CredentialProofValidationException(
    val errorCode: String,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

internal fun invalidCredentialProof(message: String, cause: Throwable? = null): CredentialProofValidationException =
    CredentialProofValidationException(CredentialErrorCodes.INVALID_PROOF, message, cause)

internal fun invalidCredentialNonce(message: String, cause: Throwable? = null): CredentialProofValidationException =
    CredentialProofValidationException(CredentialErrorCodes.INVALID_NONCE, message, cause)
