package id.walt.openid4vci.proofs.attestation

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.X509CertificateChain
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.Key
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.invalidCredentialProof
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Header values are untrusted hints. Return only authorized keys with exportable public material.
 * Signatures are verified locally; public-key retrieval failures are issuer service errors.
 */
fun interface KeyAttestationTrustResolver {
    suspend fun resolve(
        header: JsonObject,
        context: CredentialProofValidationContext,
        configuration: CredentialConfiguration,
    ): List<Key>
}

fun interface KeyAttestationKeyReferenceResolver {
    suspend fun resolve(reference: String): List<Key>
}

/** Optional deployment checks, e.g. certification or revocation. Only verified evidence is supplied. */
fun interface KeyAttestationPolicy {
    suspend fun accepts(
        attestation: VerifiedKeyAttestation,
        context: CredentialProofValidationContext,
        configuration: CredentialConfiguration,
    ): Boolean
}

data class KeyAttestationVerificationOptions(
    val trustResolver: KeyAttestationTrustResolver,
    val policy: KeyAttestationPolicy? = null,
)

/** An issuer/deployment failure, not invalid evidence from the wallet. */
class KeyAttestationServiceException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

internal suspend fun createKeyAttestationTrustResolver(
    method: KeyAttestationVerificationMethod,
    keyReferenceResolver: KeyAttestationKeyReferenceResolver?,
): KeyAttestationTrustResolver = when (method) {
    is KeyAttestationVerificationMethod.StaticJwk -> {
        val key = try {
            restoreAttestedKey(method.jwk)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw KeyAttestationServiceException("Invalid configured key attestation public key", e)
        }
        KeyAttestationTrustResolver { _, _, _ -> listOf(key) }
    }
    is KeyAttestationVerificationMethod.KeyReference -> {
        val delegate = keyReferenceResolver
            ?: throw KeyAttestationServiceException("Key attestation key-reference resolver is not configured")
        KeyAttestationTrustResolver { _, _, _ ->
            val keys = delegate.resolve(method.reference)
            if (keys.isEmpty()) throw KeyAttestationServiceException("Configured key attestation key is unavailable")
            keys
        }
    }
    is KeyAttestationVerificationMethod.X509Chain -> {
        val roots = try {
            InMemoryTrustStore(method.trustedRootCertificatesPem.map(X509CertificateUtil::parseCertificatePem))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw KeyAttestationServiceException("Invalid configured key attestation trust roots", e)
        }
        KeyAttestationTrustResolver { header, _, _ ->
            attestationInput {
                val certificates = header["x5c"] as? JsonArray
                    ?: throw invalidCredentialProof("Key attestation x5c chain is required")
                if (certificates.isEmpty()) {
                    throw invalidCredentialProof("Key attestation certificate chain must not be empty")
                }
                val chain = certificates.map { element ->
                    val encoded = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
                        ?: throw invalidCredentialProof("Key attestation certificates must be strings")
                    X509CertificateUtil.parseCertificateDerEncoded(ByteString(encoded.decodeFromBase64()))
                }
                val ordered = X509CertificateChain.of(chain)
                if (ordered.size != chain.size || chain.indices.any {
                        chain[it].fingerprintSha256 != ordered[ordered.size - 1 - it].fingerprintSha256
                    }) {
                    throw invalidCredentialProof("Key attestation x5c must contain one leaf-first certificate chain")
                }
                if (!X509CertificateUtil.validateCertificateChain(chain, roots).valid) {
                    throw invalidCredentialProof("Key attestation certificate chain is not trusted")
                }
                // Resolve only the validated leaf; other token headers cannot redirect this lookup.
                val key = chain.first().restoreSubjectPublicKey(CryptoRuntime(defaultSoftwareKeyProviders()))
                listOf(key)
            }
        }
    }
}
