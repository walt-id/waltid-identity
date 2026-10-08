package id.walt.wallet2.handlers

/**
 * Selects a provider and its independently trusted verification key for a credential issuer.
 * Resolution happens only when the selected credential requires key attestation. Returning null
 * rejects that issuance; providers are never inferred from the returned attestation.
 */
fun interface KeyAttestationProviderResolver {
    suspend fun resolve(credentialIssuer: String): KeyAttestationProvider?
}
