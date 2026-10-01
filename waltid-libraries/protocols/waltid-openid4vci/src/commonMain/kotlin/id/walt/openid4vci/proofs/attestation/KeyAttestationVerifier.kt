package id.walt.openid4vci.proofs.attestation

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.*
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.ProofTypeMetadata
import id.walt.openid4vci.proofs.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Instant

data class VerifiedKeyAttestation(
    val jwt: String,
    val header: JsonObject,
    val payload: JsonObject,
    val attesterKey: Key,
    val attestedKeys: List<Key>,
)

/** Shared attestation verification. This entry point currently applies the nested-JWT rules. */
class KeyAttestationVerifier(
    private val clockSkewSeconds: Long = 60,
    private val now: () -> Instant = { Clock.System.now() },
) {
    init { require(clockSkewSeconds >= 0) }

    suspend fun verify(
        jwt: String,
        proofType: ProofTypeMetadata?,
        context: CredentialProofValidationContext,
        configuration: CredentialConfiguration,
        options: KeyAttestationVerificationOptions,
    ): VerifiedKeyAttestation {
        val decoded = attestationInput { CompactJws.decodeUnverified(jwt) }
        val header = decoded.protectedHeader
        if (header.string("typ") != "key-attestation+jwt") throw invalidCredentialProof("Invalid key attestation type")
        if ("trust_chain" in header) throw invalidCredentialProof("Key attestation trust_chain is not supported")
        if ("jwk" in header) throw invalidCredentialProof("Inline attester JWKs are not supported")
        header.string("kid")
        if (proofType != null && decoded.algorithm.identifier !in proofType.proofSigningAlgValuesSupported) {
            throw invalidCredentialProof("Key attestation signing algorithm is not advertised")
        }
        val keys = try {
            options.trustResolver.resolve(header, context, configuration)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CredentialProofValidationException) {
            throw e
        } catch (e: Exception) {
            throw KeyAttestationServiceException("Key attestation trust resolution failed", e)
        }
        val signer = keys.firstOrNull { key ->
            // Preserve configured algorithm restrictions before restoring public material.
            val declaredAlgorithm = (key as? StorableKey)?.storedKey?.metadata?.get(JWK_ALGORITHM_METADATA_KEY)
            if (declaredAlgorithm != null && declaredAlgorithm != decoded.algorithm.identifier ||
                !key.spec.supportsJwsAlgorithm(decoded.algorithm) ||
                !key.capabilities.supportsSignatureAlgorithm(decoded.algorithm.toSignatureAlgorithm())
            ) return@firstOrNull false
            // Remote verifier errors can be indistinguishable from invalid signatures.
            // Retrieve trusted public material outside the wallet-input error boundary,
            // then use the local software provider for cryptographic verification.
            val publicKey = try {
                restoreAttestedKey(key.exportPublicJwkObject())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw KeyAttestationServiceException("Key attestation public-key retrieval failed", e)
            }
            try {
                CompactJws.verify(jwt, publicKey, decoded.algorithm)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        } ?: throw invalidCredentialProof("Key attestation signature is invalid or its signer is not trusted")
        val payload = attestationInput {
            Json.parseToJsonElement(decoded.payload.decodeToString()) as? JsonObject
                ?: throw invalidCredentialProof("Key attestation payload must be an object")
        }
        val issuedAt = payload.integer("iat")
        val expiresAt = payload.integer("exp")
        val currentInstant = now()
        val currentTime = currentInstant.epochSeconds
        if (issuedAt > currentTime + clockSkewSeconds || expiresAt <= currentTime - clockSkewSeconds || expiresAt <= issuedAt) {
            throw invalidCredentialProof("Key attestation is outside its valid lifetime")
        }
        payload["nbf"]?.let { value ->
            val notBefore = (value as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
                ?.takeIf { it.isFinite() }
                ?: throw invalidCredentialProof("Key attestation nbf must be a NumericDate")
            val currentNumericDate = currentTime.toDouble() + currentInstant.nanosecondsOfSecond / 1_000_000_000.0
            if (notBefore > currentNumericDate + clockSkewSeconds) {
                throw invalidCredentialProof("Key attestation is not yet valid")
            }
        }
        validateCredentialNonce(payload, context)
        payload.string("nonce")
        payload.string("certification")
        if ("status" in payload && payload["status"] !is JsonObject) throw invalidCredentialProof("Key attestation status must be an object")
        val storage = payload.strings("key_storage")
        val authentication = payload.strings("user_authentication")
        proofType?.keyAttestationsRequired?.let { requirements ->
            if (requirements.keyStorage?.let { accepted -> storage.none { it in accepted } } == true ||
                requirements.userAuthentication?.let { accepted -> authentication.none { it in accepted } } == true
            ) throw invalidCredentialProof("Key attestation does not meet the required assurances")
        }
        val attestedKeys = payload["attested_keys"] as? JsonArray
            ?: throw invalidCredentialProof("Key attestation attested_keys must be an array")
        if (attestedKeys.isEmpty()) {
            throw invalidCredentialProof("Key attestation attested_keys must not be empty")
        }
        val verified = VerifiedKeyAttestation(jwt, header, payload, signer, attestedKeys.map {
            restoreAttestedKey(it as? JsonObject ?: throw invalidCredentialProof("Attested keys must be JWK objects"))
        })
        val accepted = try {
            options.policy?.accepts(verified, context, configuration) ?: true
        } catch (e: CancellationException) {
            throw e
        } catch (e: CredentialProofValidationException) {
            throw e
        } catch (e: Exception) {
            throw KeyAttestationServiceException("Key attestation policy evaluation failed", e)
        }
        if (!accepted) throw invalidCredentialProof("Key attestation rejected by issuer policy")
        return verified
    }
}

private val publicKeyRuntime = CryptoRuntime(defaultSoftwareKeyProviders())

internal suspend fun restoreAttestedKey(jwk: JsonObject): Key = attestationInput {
    if (Jwk.containsPrivateMaterial(jwk)) throw invalidCredentialProof("Attested JWK must not contain private material")
    jwk.string("use")?.let { if (it != "sig") throw invalidCredentialProof("Attested JWK use must be sig") }
    if ("key_ops" in jwk && "verify" !in jwk.strings("key_ops")) {
        throw invalidCredentialProof("Attested JWK key_ops must allow verification")
    }
    val encoded = EncodedKey.Jwk(BinaryData(jwk.toString().encodeToByteArray()), privateMaterial = false)
    val key = publicKeyRuntime.restore(encoded.toStoredSoftwareKey(KeyId(Jwk.sha256Thumbprint(encoded)), setOf(KeyUsage.VERIFY)))
    if (JwsAlgorithm.entries.none { key.spec.supportsJwsAlgorithm(it) }) {
        throw invalidCredentialProof("Attested JWK is not a supported signing key")
    }
    jwk.string("alg")?.let { algorithm ->
        if (!key.spec.supportsJwsAlgorithm(JwsAlgorithm.parse(algorithm))) {
            throw invalidCredentialProof("Attested JWK algorithm does not match its key")
        }
    }
    key
}

internal suspend fun <T> attestationInput(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: CredentialProofValidationException) {
    throw e
} catch (e: Exception) {
    throw invalidCredentialProof("Invalid key attestation", e)
}

private fun JsonObject.string(name: String): String? {
    val value = this[name] ?: return null
    return (value as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content
        ?: throw invalidCredentialProof("Key attestation $name must be a non-empty string")
}

private fun JsonObject.integer(name: String): Long = (this[name] as? JsonPrimitive)
    ?.takeUnless { it.isString }?.longOrNull
    ?: throw invalidCredentialProof("Key attestation $name must be an integer")

private fun JsonObject.strings(name: String): Set<String> {
    val value = this[name] ?: return emptySet()
    val array = value as? JsonArray ?: throw invalidCredentialProof("Key attestation $name must be an array")
    if (array.isEmpty()) throw invalidCredentialProof("Key attestation $name must not be empty")
    return array.map {
        (it as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content
            ?: throw invalidCredentialProof("Key attestation $name values must be non-empty strings")
    }.toSet()
}
