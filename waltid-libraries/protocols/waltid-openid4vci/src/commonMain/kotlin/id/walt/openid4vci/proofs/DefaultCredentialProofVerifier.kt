package id.walt.openid4vci.proofs

import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.requests.credential.CredentialRequest
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant

class DefaultCredentialProofVerifier(
    proofMaxAgeSeconds: Long = 300,
    clockSkewSeconds: Long = 60,
    now: () -> Instant = { Clock.System.now() },
    val handlers: CredentialProofHandlers = CredentialProofHandlers.defaults(proofMaxAgeSeconds, clockSkewSeconds, now),
) : CredentialProofVerifier {
    override suspend fun verify(
        credentialRequest: CredentialRequest,
        credentialConfiguration: CredentialConfiguration,
        context: CredentialProofValidationContext,
    ): CredentialProofVerificationResult {
        val collection = validateCredentialProofRequest(credentialRequest.proofs, credentialConfiguration, context)
            ?: return CredentialProofVerificationResult(emptyList(), emptyList())
        val handler = handlers[collection.type]
            ?: throw invalidCredentialProof("Unsupported credential proof type: ${collection.type}")
        val metadata = credentialConfiguration.proofTypesSupported?.get(collection.type.value)
        val results = collection.values.map { proof ->
            try {
                handler.verify(proof, metadata, credentialConfiguration, context).also {
                    if (it.evidence.proofType != collection.type || it.candidates.isEmpty()) {
                        throw CredentialProofServiceException("Credential proof handler returned inconsistent evidence or no bindings")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: CredentialProofValidationException) {
                throw e
            } catch (e: CredentialProofServiceException) {
                throw e
            } catch (e: Exception) {
                throw CredentialProofServiceException("Credential proof handler failed", e)
            }
        }
        return try {
            CredentialProofVerificationResult(
                results.map { it.evidence },
                selectCredentialBindings(results, credentialConfiguration),
            ).also { validateCredentialProofResult(collection, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: CredentialProofValidationException) {
            throw e
        } catch (e: CredentialProofServiceException) {
            throw e
        } catch (e: Exception) {
            throw CredentialProofServiceException("Credential binding selection failed", e)
        }
    }
}
