package id.walt.openid4vci.proofs.attestation

import id.walt.openid4vci.attestation.AttestationStatusPolicy
import id.walt.openid4vci.attestation.AttestationStatusResolver

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.X509CertificateChain
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.Key
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.proofs.CredentialProofServiceException
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.invalidCredentialProof
import id.walt.trust.decision.TrustCheck
import id.walt.trust.decision.TrustDecisionEvaluator
import id.walt.trust.decision.TrustDecisionRequest
import id.walt.trust.decision.TrustPurpose
import id.walt.trust.decision.TrustRequirement
import id.walt.trust.decision.TrustSubject
import id.walt.trust.decision.check
import id.walt.trust.model.TrustDecisionCode
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64

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
    /** Maximum entries per attestation, checked before key import; defaults to 20. Explicit null disables the limit. */
    val maxAttestedKeys: Int? = 20,
    val statusPolicy: AttestationStatusPolicy = AttestationStatusPolicy(),
    val statusResolver: AttestationStatusResolver? = null,
) {
    init {
        require(maxAttestedKeys == null || maxAttestedKeys > 0) { "maxAttestedKeys must be positive" }
    }
}

/** An issuer/deployment failure, not invalid evidence from the wallet. */
class KeyAttestationServiceException(message: String, cause: Throwable? = null) :
    CredentialProofServiceException(message, cause)

internal suspend fun createKeyAttestationTrustResolver(
    method: KeyAttestationVerificationMethod,
    keyReferenceResolver: KeyAttestationKeyReferenceResolver?,
    trustEvaluator: TrustDecisionEvaluator? = null,
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
                val chain = leafFirstX5cChain(header)
                if (!X509CertificateUtil.validateCertificateChain(chain, roots).valid) {
                    throw invalidCredentialProof("Key attestation certificate chain is not trusted")
                }
                // Resolve only the validated leaf; other token headers cannot redirect this lookup.
                val key = chain.first().restoreSubjectPublicKey(CryptoRuntime(defaultSoftwareKeyProviders()))
                listOf(key)
            }
        }
    }

    is KeyAttestationVerificationMethod.X509TrustRegistry -> KeyAttestationTrustResolver { header, _, _ ->
        val chain = attestationInput { leafFirstX5cChain(header) }
        // Outside attestationInput: an issuer that cannot decide trust must not report an invalid proof.
        val evaluator = trustEvaluator
            ?: throw KeyAttestationServiceException("Key attestation trust registry is not configured")
        val request = TrustDecisionRequest(
            purpose = TrustPurpose.ISSUER_WUA_SIGNER,
            subject = TrustSubject.CertificateChain(chain.map { Base64.encode(it.encodedDer.toByteArray()) }),
            expectedEntityTypes = method.expectedEntityTypes,
        )
        when (val check = evaluator.check(request, TrustRequirement.RegistryFresh(method.requireAuthenticatedSource))) {
            is TrustCheck.Accepted -> Unit
            is TrustCheck.Rejected -> {
                if (check.decision?.decision == TrustDecisionCode.UNKNOWN) {
                    log.warn { "Key attestation trust source is unavailable: ${check.decision?.warnings}" }
                    throw KeyAttestationServiceException("Key attestation trust source is unavailable")
                }
                log.info { "Key attestation signer is not trusted: ${check.reason}" }
                throw invalidCredentialProof("Key attestation certificate chain is not trusted")
            }

            TrustCheck.Skipped -> throw KeyAttestationServiceException("A registry trust requirement is never skipped")
        }
        attestationInput { listOf(chain.first().restoreSubjectPublicKey(CryptoRuntime(defaultSoftwareKeyProviders()))) }
    }
}

/** The x5c certificates, which must form one chain in leaf-first order. Call within [attestationInput]. */
private fun leafFirstX5cChain(header: JsonObject): List<X509Certificate> {
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
    return chain
}

private val log = KotlinLogging.logger { }
