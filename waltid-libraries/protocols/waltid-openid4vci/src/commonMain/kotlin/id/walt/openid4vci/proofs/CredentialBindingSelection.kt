package id.walt.openid4vci.proofs

import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration

/** Select only after all evidence passed validation. No proof-specific fields are inspected here. */
internal suspend fun selectCredentialBindings(
    results: List<CredentialProofHandlerResult>,
    configuration: CredentialConfiguration,
): List<VerifiedCredentialBinding> {
    val distinct = results.any { it.multiplicity == CredentialBindingMultiplicity.DISTINCT_KEYS }
    if (!distinct) {
        val bindings = results.flatMapIndexed { index, result ->
            result.candidates.map { VerifiedCredentialBinding(it.holderKey, it.holderKid, it.holderDid, setOf(index)) }
        }
        bindings.forEach { validateSelectedBinding(it, configuration) }
        return bindings
    }
    val selected = linkedMapOf<String, VerifiedCredentialBinding>()
    results.forEachIndexed { index, result ->
        result.candidates.forEach { candidate ->
            val thumbprint = Jwk.sha256Thumbprint(candidate.holderKey.exportPublicJwk())
            val previous = selected[thumbprint]
            // Keep the first identity unless this is the first verified DID for the key.
            val keepIdentity = previous != null && (previous.holderDid != null || candidate.holderDid == null)
            selected[thumbprint] = VerifiedCredentialBinding(
                holderKey = candidate.holderKey,
                holderKid = if (keepIdentity) previous?.holderKid else candidate.holderKid,
                holderDid = if (keepIdentity) previous?.holderDid else candidate.holderDid,
                proofIndexes = previous?.proofIndexes.orEmpty() + index,
            )
        }
    }
    selected.values.forEach { validateSelectedBinding(it, configuration) }
    return selected.values.toList()
}

private fun validateSelectedBinding(binding: VerifiedCredentialBinding, configuration: CredentialConfiguration) {
    val methods = configuration.cryptographicBindingMethodsSupported
    if (binding.holderDid == null && methods != null &&
        methods.none { it == CryptographicBindingMethod.Jwk || it == CryptographicBindingMethod.CoseKey }) {
        throw invalidCredentialProof("A selected key has no verified identifier for the supported binding methods")
    }
}
