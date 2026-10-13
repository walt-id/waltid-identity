package id.walt.openid4vci.proofs

import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration

/** Shared by the coordinator and provider so custom verifiers cannot bypass request guards. */
internal fun validateCredentialProofRequest(
    proofs: Proofs?,
    configuration: CredentialConfiguration,
    context: CredentialProofValidationContext?,
): CredentialProofCollection? {
    if (proofs == null) {
        if (configuration.proofTypesSupported != null) throw invalidCredentialProof("Credential request is missing proofs")
        return null
    }
    val collection = try { proofs.normalized() } catch (e: IllegalArgumentException) {
        throw invalidCredentialProof(e.message ?: "Invalid credential proofs", e)
    }
    val supported = configuration.proofTypesSupported
    // Omitted metadata permits optional proofs; an explicit list restricts the selected type.
    if (supported != null && collection.type.value !in supported) {
        throw invalidCredentialProof("Credential proof type ${collection.type} is not advertised")
    }
    val maximumCount = context?.batchCredentialIssuance?.batchSize ?: 1
    if (collection.values.size > maximumCount) {
        throw CredentialProofValidationException(CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST,
            "Credential proof count exceeds the maximum batch size $maximumCount")
    }
    return collection
}

internal fun validateCredentialProofResult(
    collection: CredentialProofCollection?,
    result: CredentialProofVerificationResult,
) {
    val count = collection?.values?.size ?: 0
    val indices = (0 until count).toSet()
    if (result.proofs.size != count || result.proofs.any { it.proofType != collection?.type } ||
        (count == 0 && result.bindings.isNotEmpty()) || (count > 0 && result.bindings.isEmpty()) ||
        result.bindings.any { it.proofIndexes.isEmpty() || !indices.containsAll(it.proofIndexes) } ||
        result.bindings.flatMap { it.proofIndexes }.toSet() != indices
    ) throw CredentialProofServiceException("Credential proof verification result has inconsistent evidence or binding provenance")
}
