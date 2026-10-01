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
