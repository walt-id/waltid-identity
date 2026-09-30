package id.walt.openid4vci.proofs.attestation

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.X509CertificateChain
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto2.keys.Key
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.invalidCredentialProof
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Deployment trust for credential keys, independently configured from OAuth client attestation. */
@Serializable
data class KeyAttestationConfig(
    val verificationMethod: KeyAttestationVerificationMethod,
    val limits: KeyAttestationLimits = KeyAttestationLimits(),
)

@Serializable
data class KeyAttestationLimits(
    val maxAttestedKeys: Int = 32,
    val maxCredentials: Int = 32,
    val maxJwtLength: Int = 65_536,
) {
    init {
        require(maxAttestedKeys > 0 && maxCredentials > 0 && maxJwtLength > 0) {
            "Key attestation limits must be positive"
        }
    }
}

@Serializable
sealed class KeyAttestationVerificationMethod {
    @Serializable
    @SerialName("static-jwk")
    data class StaticJwk(val jwk: JsonObject) : KeyAttestationVerificationMethod()

    @Serializable
    @SerialName("key-reference")
    data class KeyReference(val reference: String) : KeyAttestationVerificationMethod() {
        init { require(reference.isNotBlank()) { "Key attestation key reference must not be blank" } }
    }

    @Serializable
    @SerialName("x509-chain")
    data class X509Chain(val trustedRootCertificatesPem: List<String>) : KeyAttestationVerificationMethod() {
        init { require(trustedRootCertificatesPem.isNotEmpty()) { "Key attestation trust roots must not be empty" } }
    }
}

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
    val limits: KeyAttestationLimits = KeyAttestationLimits(),
    val policy: KeyAttestationPolicy? = null,
)

/** An issuer/deployment failure, not invalid evidence from the wallet. */
class KeyAttestationServiceException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

fun validateKeyAttestationConfiguration(
    configurations: Iterable<CredentialConfiguration>,
    config: KeyAttestationConfig?,
) {
    configurations.forEach { credential ->
        credential.proofTypesSupported?.forEach { (type, proof) ->
            if (proof.keyAttestationsRequired != null) {
                require(type == "jwt") { "Key attestation requirements are currently supported only for JWT proofs" }
                require(config != null) { "Required key attestations need keyAttestationConfig trust material" }
            }
        }
    }
}

suspend fun KeyAttestationConfig.toVerificationOptions(
    keyReferenceResolver: KeyAttestationKeyReferenceResolver? = null,
): KeyAttestationVerificationOptions {
    val resolver = when (val method = verificationMethod) {
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
                    if (certificates.isEmpty() || certificates.size > 8) {
                        throw invalidCredentialProof("Key attestation certificate chain must contain 1 to 8 certificates")
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
    return KeyAttestationVerificationOptions(resolver, limits)
}
