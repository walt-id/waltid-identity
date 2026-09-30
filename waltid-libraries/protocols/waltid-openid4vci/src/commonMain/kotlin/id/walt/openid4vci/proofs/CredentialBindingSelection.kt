package id.walt.openid4vci.proofs

import id.walt.cose.toCoseKey
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import kotlinx.coroutines.CancellationException

/** Select only after every proof has been verified, before allocating issuance inputs or status entries. */
internal suspend fun selectCredentialBindings(
    proofs: List<VerifiedCredentialProof>,
    configuration: CredentialConfiguration,
    context: CredentialProofValidationContext,
): List<VerifiedCredentialBinding> {
    // Preserve ordinary JWT issuance, including its existing duplicate-key behavior.
    if (proofs.none { it.keyAttestation != null }) return proofs.mapIndexed { index, proof -> proof.binding(index) }
    val options = context.keyAttestation ?: throw invalidCredentialProof("Key attestation verification is not configured")
    val signers = proofs.mapIndexed { index, proof ->
        Jwk.sha256Thumbprint(proof.holderKey.exportPublicJwk()) to proof.binding(index)
    }.groupBy({ it.first }, { it.second })
    val bindings = linkedMapOf<String, VerifiedCredentialBinding>()
    proofs.forEachIndexed { index, proof ->
        (proof.keyAttestation?.attestedKeys ?: listOf(proof.holderKey)).forEach { key ->
            val thumbprint = Jwk.sha256Thumbprint(key.exportPublicJwk())
            val signer = signers[thumbprint]?.firstOrNull { it.holderDid != null }
                ?: signers[thumbprint]?.firstOrNull()
            val binding = VerifiedCredentialBinding(key, signer?.holderKid, signer?.holderDid, setOf(index))
            val methods = configuration.cryptographicBindingMethodsSupported
            if (binding.holderDid == null && methods != null &&
                methods.none { it == CryptographicBindingMethod.Jwk || it == CryptographicBindingMethod.CoseKey }) {
                throw invalidCredentialProof("An attested key has no verified identifier for the supported binding methods")
            }
            if (configuration.format !in setOf(CredentialFormat.SD_JWT_VC, CredentialFormat.MSO_MDOC) && binding.holderDid == null) {
                throw invalidCredentialProof("This credential format requires a verified DID for each attested key")
            }
            if (configuration.format == CredentialFormat.MSO_MDOC) {
                try {
                    key.exportPublicJwk().toCoseKey()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw invalidCredentialProof("An attested key cannot be represented by the mdoc credential handler", e)
                }
            }
            val previous = bindings[thumbprint]
            bindings[thumbprint] = binding.copy(proofIndexes = previous?.proofIndexes.orEmpty() + index)
            if (bindings.size > options.limits.maxCredentials) {
                throw invalidCredentialProof("Attested credential count exceeds the configured limit")
            }
        }
    }
    return bindings.values.toList()
}
