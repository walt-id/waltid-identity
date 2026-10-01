package id.walt.openid4vci.proofs.jwt

import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.crypto2.jose.supportsJwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.did.dids.DidUtils
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.ProofTypeMetadata
import id.walt.openid4vci.proofs.CredentialBindingMultiplicity
import id.walt.openid4vci.proofs.CredentialProofCapabilities
import id.walt.openid4vci.proofs.CredentialProofHandler
import id.walt.openid4vci.proofs.CredentialProofHandlerResult
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.ProofType
import id.walt.openid4vci.proofs.VerifiedCredentialBindingCandidate
import id.walt.openid4vci.proofs.VerifiedJwtProof
import id.walt.openid4vci.proofs.attestation.KeyAttestationVerifier
import id.walt.openid4vci.proofs.invalidCredentialProof
import id.walt.openid4vci.proofs.validateCredentialNonce
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Instant

class JwtCredentialProofHandler(
    private val proofMaxAgeSeconds: Long = DEFAULT_PROOF_MAX_AGE_SECONDS,
    private val clockSkewSeconds: Long = DEFAULT_CLOCK_SKEW_SECONDS,
    private val now: () -> Instant = { Clock.System.now() },
) : CredentialProofHandler {
    override val proofType: ProofType = ProofType.JWT

    private val crypto2Runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val didKeyResolver = Crypto2JwtKeyResolver()
    private val keyAttestationVerifier = KeyAttestationVerifier(clockSkewSeconds, now)

    init {
        require(proofMaxAgeSeconds > 0) { "Credential proof maximum age must be positive" }
        require(clockSkewSeconds >= 0) { "Credential proof clock skew must not be negative" }
    }

    override fun validateConfiguration(
        proofMetadata: ProofTypeMetadata,
        configuration: CredentialConfiguration,
        capabilities: CredentialProofCapabilities,
    ) {
        require(proofMetadata.keyAttestationsRequired == null || capabilities.keyAttestation) {
            "Required key attestations need keyAttestationConfig trust material"
        }
    }

    override suspend fun verify(
        proof: JsonElement,
        proofMetadata: ProofTypeMetadata?,
        configuration: CredentialConfiguration,
        context: CredentialProofValidationContext,
    ): CredentialProofHandlerResult {
        val jwt = (proof as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw invalidCredentialProof("JWT proof must be a string")
        val evidence = verifyJwtProof(jwt, proofMetadata, configuration, context)
        val signer = VerifiedCredentialBindingCandidate(evidence.holderKey, evidence.holderKid, evidence.holderDid)
        val attestation = evidence.keyAttestation
        if (attestation == null) return CredentialProofHandlerResult(
            evidence, listOf(signer), CredentialBindingMultiplicity.PRESERVE_OCCURRENCES,
        )
        val signingThumbprint = Jwk.sha256Thumbprint(evidence.holderKey.exportPublicJwk())
        return CredentialProofHandlerResult(
            evidence,
            attestation.attestedKeys.map { key ->
                if (Jwk.sha256Thumbprint(key.exportPublicJwk()) == signingThumbprint) signer
                else VerifiedCredentialBindingCandidate(key)
            },
            CredentialBindingMultiplicity.DISTINCT_KEYS,
        )
    }

    private suspend fun verifyJwtProof(
        proofJwt: String,
        proofType: ProofTypeMetadata?,
        credentialConfiguration: CredentialConfiguration,
        context: CredentialProofValidationContext,
    ): VerifiedJwtProof {
        val decoded = runCatching { proofJwt.decodeJws() }
            .getOrElse {
                if (it is CancellationException) throw it
                throw invalidCredentialProof("Invalid credential proof JWT", it)
            }

        val algorithm = decoded.header.requiredStringHeader(JwtHeaderParams.ALGORITHM)
        requireCredentialProof(algorithm in supportedAsymmetricAlgorithms) {
            "Unsupported credential proof signing algorithm: $algorithm"
        }
        proofType?.let {
            requireCredentialProof(algorithm in it.proofSigningAlgValuesSupported) {
                "Credential proof signing algorithm is not advertised: $algorithm"
            }
        }

        requireCredentialProof(decoded.header.optionalStringHeader(JwtHeaderParams.TYPE) == JWT_TYPE) {
            "Credential proof ${JwtHeaderParams.TYPE} header must be $JWT_TYPE"
        }
        rejectUnsupportedTrustHeaders(decoded.header)

        val resolvedHolderKey = resolveHolderKey(decoded.header, credentialConfiguration)
        val verifiedPayload = runCatching {
            CompactJws.verify(proofJwt, resolvedHolderKey.key, setOf(JwsAlgorithm.parse(algorithm)))
        }.getOrElse {
            if (it is CancellationException) throw it
            throw invalidCredentialProof("Invalid credential proof signature", it)
        }.payload.decodeToString().let { payload ->
            runCatching { Json.parseToJsonElement(payload) }.getOrNull() as? JsonObject
                ?: throw invalidCredentialProof("Credential proof payload must be a JSON object")
        }

        validateAudience(verifiedPayload, context.credentialIssuer)
        validateIssuedAt(verifiedPayload)
        validateIssuer(verifiedPayload, context)
        validateCredentialNonce(verifiedPayload, context)

        val attestationJwt = decoded.header.optionalStringHeader(JWT_HEADER_KEY_ATTESTATION)
        if (attestationJwt == null && proofType?.keyAttestationsRequired != null) {
            throw invalidCredentialProof("Key attestation is required for this credential configuration")
        }
        val attestation = attestationJwt?.let { jwt ->
            val options = context.keyAttestation
                ?: throw invalidCredentialProof("Key attestation verification is not configured")
            keyAttestationVerifier.verify(jwt, proofType, context, credentialConfiguration, options).also { evidence ->
                val signerThumbprint = Jwk.sha256Thumbprint(resolvedHolderKey.key.exportPublicJwk())
                requireCredentialProof(evidence.attestedKeys.any { Jwk.sha256Thumbprint(it.exportPublicJwk()) == signerThumbprint }) {
                    "Credential proof signing key is not an attested key"
                }
            }
        }

        return VerifiedJwtProof(
            jwt = proofJwt,
            algorithm = algorithm,
            header = decoded.header,
            payload = verifiedPayload,
            holderKey = resolvedHolderKey.key,
            holderKid = resolvedHolderKey.kid,
            holderDid = resolvedHolderKey.did,
            nonce = verifiedPayload.optionalStringClaim(PROOF_NONCE_CLAIM),
            keyAttestation = attestation,
        )
    }

    private suspend fun resolveHolderKey(
        header: JsonObject,
        credentialConfiguration: CredentialConfiguration,
    ): ResolvedHolderKey {
        val hasJwk = JwtHeaderParams.JSON_WEB_KEY in header
        val hasKid = JwtHeaderParams.KEY_ID in header
        val hasX5c = JWT_HEADER_X5C in header
        val sourceCount = listOf(hasJwk, hasKid, hasX5c).count { it }
        requireCredentialProof(sourceCount == 1) {
            "Credential proof JWT header must contain exactly one holder key source"
        }
        if (hasX5c) {
            throw invalidCredentialProof("Credential proof x5c holder key source is not supported")
        }

        return when {
            hasJwk -> {
                validateBindingMethod(credentialConfiguration, setOf(CryptographicBindingMethod.Jwk, CryptographicBindingMethod.CoseKey))
                val jwk = header[JwtHeaderParams.JSON_WEB_KEY] as? JsonObject
                    ?: throw invalidCredentialProof("Credential proof jwk header must be a JSON object")
                jwk.optionalString(JwtHeaderParams.ALGORITHM)?.let { jwkAlgorithm ->
                    val proofAlgorithm = header.requiredStringHeader(JwtHeaderParams.ALGORITHM)
                    requireCredentialProof(jwkAlgorithm == proofAlgorithm) {
                        "Credential proof JWK algorithm does not match the proof algorithm"
                    }
                }
                validateJwkUse(jwk)
                requireCredentialProof(!Jwk.containsPrivateMaterial(jwk)) {
                    "Credential proof JWK must not contain private key material"
                }
                val publicKey = restoreHolderJwk(jwk)
                validateKeyAlgorithm(publicKey, header.requiredStringHeader(JwtHeaderParams.ALGORITHM))
                ResolvedHolderKey(key = publicKey, kid = null, did = null)
            }

            hasKid -> {
                val holderKid = header.requiredStringHeader(JwtHeaderParams.KEY_ID)
                requireCredentialProof(DidUtils.isDidUrl(holderKid)) {
                    "Credential proof kid must be a DID URL when using kid-based holder key resolution: $holderKid"
                }
                validateDidBindingMethod(credentialConfiguration, holderKid)
                val did = holderKid.substringBefore("#")
                val key = resolveHolderDidKey(did, holderKid)
                validateKeyAlgorithm(key, header.requiredStringHeader(JwtHeaderParams.ALGORITHM))
                ResolvedHolderKey(key = key, kid = holderKid, did = did)
            }

            else -> throw invalidCredentialProof("Credential proof JWT header must contain kid or jwk")
        }
    }

    private fun rejectUnsupportedTrustHeaders(header: JsonObject) {
        if (JWT_HEADER_TRUST_CHAIN in header) {
            throw invalidCredentialProof("Credential proof trust_chain is not supported")
        }
    }

    /**
     * Resolves the holder key referenced by a proof `kid`.
     *
     * Wallets do not always use the DID verification method ID as `kid` (a JWK thumbprint fragment is
     * common), so a DID exposing exactly one verification method is accepted without fragment matching.
     */
    private suspend fun resolveHolderDidKey(did: String, holderKid: String): Key =
        runCatching { didKeyResolver.resolveFromDid(did, holderKid) }
            .recoverCatching {
                if (it is CancellationException) throw it
                didKeyResolver.resolveFromDid(did)
            }
            .getOrElse {
                if (it is CancellationException) throw it
                throw invalidCredentialProof("Could not resolve credential proof DID key", it)
            }

    /** Restores an inline holder JWK as a verification-only crypto2 key. */
    private suspend fun restoreHolderJwk(jwk: JsonObject): Key {
        val encoded = EncodedKey.Jwk(
            data = BinaryData(Json.encodeToString(JsonObject.serializer(), jwk).encodeToByteArray()),
            privateMaterial = false,
        )
        return runCatching {
            crypto2Runtime.restore(
                encoded.toStoredSoftwareKey(KeyId(Jwk.sha256Thumbprint(encoded)), setOf(KeyUsage.VERIFY)),
            )
        }.getOrElse {
            if (it is CancellationException) throw it
            throw invalidCredentialProof("Credential proof contains an invalid JWK", it)
        }
    }

    private fun validateKeyAlgorithm(key: Key, algorithm: String) {
        val jwsAlgorithm = runCatching { JwsAlgorithm.parse(algorithm) }.getOrElse {
            if (it is CancellationException) throw it
            throw invalidCredentialProof("Unsupported credential proof signing algorithm: $algorithm")
        }
        requireCredentialProof(key.spec.supportsJwsAlgorithm(jwsAlgorithm)) {
            "Credential proof holder key type does not match algorithm $algorithm"
        }
    }

    private fun validateBindingMethod(
        credentialConfiguration: CredentialConfiguration,
        acceptedMethods: Set<CryptographicBindingMethod>,
    ) {
        val configured = credentialConfiguration.cryptographicBindingMethodsSupported ?: return
        requireCredentialProof(configured.any { it in acceptedMethods }) {
            "Credential proof holder key source is not supported by this credential configuration"
        }
    }

    private fun validateDidBindingMethod(
        credentialConfiguration: CredentialConfiguration,
        holderKid: String,
    ) {
        val configured = credentialConfiguration.cryptographicBindingMethodsSupported ?: return
        val didMethod = holderKid.removePrefix("did:").substringBefore(":")
        requireCredentialProof(CryptographicBindingMethod.Did(didMethod) in configured) {
            "Credential proof DID method is not supported by this credential configuration: did:$didMethod"
        }
    }

    private fun validateJwkUse(jwk: JsonObject) {
        jwk.optionalString(JWK_USE)?.let { use ->
            requireCredentialProof(use == JWK_SIGNATURE_USE) {
                "Credential proof JWK use must be $JWK_SIGNATURE_USE"
            }
        }
        jwk[JWK_KEY_OPERATIONS]?.let { value ->
            val operations = value as? JsonArray
                ?: throw invalidCredentialProof("Credential proof JWK $JWK_KEY_OPERATIONS must be an array")
            val operationNames = operations.map { operation ->
                (operation as? JsonPrimitive)
                    ?.takeIf { it.isString && it.content.isNotBlank() }
                    ?.content
                    ?: throw invalidCredentialProof(
                        "Credential proof JWK $JWK_KEY_OPERATIONS entries must be non-empty strings",
                    )
            }
            requireCredentialProof(JWK_VERIFY_OPERATION in operationNames) {
                "Credential proof JWK key_ops must allow verification"
            }
        }
    }

    private fun validateAudience(payload: JsonObject, credentialIssuer: String) {
        val audience = payload.optionalStringClaim(JwtPayloadClaims.AUDIENCE)
            ?: throw invalidCredentialProof("Credential proof audience claim is required")
        requireCredentialProof(audience == credentialIssuer) {
            "Credential proof audience must be the Credential Issuer Identifier"
        }
    }

    private fun validateIssuedAt(payload: JsonObject) {
        val issuedAt = payload.requiredLongClaim(JwtPayloadClaims.ISSUED_AT)
        val currentTime = now().epochSeconds
        val earliest = currentTime - proofMaxAgeSeconds - clockSkewSeconds
        val latest = currentTime + clockSkewSeconds
        requireCredentialProof(issuedAt in earliest..latest) {
            "Credential proof is outside the accepted age window"
        }
    }

    private fun validateIssuer(payload: JsonObject, context: CredentialProofValidationContext) {
        val issuer = payload.optionalStringClaim(JwtPayloadClaims.ISSUER)
        if (context.anonymousPreAuthorizedAccess) {
            requireCredentialProof(issuer == null) {
                "Credential proof issuer claim must be omitted for anonymous pre-authorized access"
            }
            return
        }
        // OpenID4VCI 1.0 Appendix F.1: the proof `iss` claim is OPTIONAL, but when present its
        // value MUST be the access token `client_id`. A client-bound proof may still omit `iss`,
        // so its absence is accepted here.
        if (issuer != null) {
            requireCredentialProof(context.clientId != null && issuer == context.clientId) {
                "Credential proof issuer claim must match the access token client_id"
            }
        }
    }

    private fun JsonObject.requiredStringHeader(name: String): String =
        optionalStringHeader(name) ?: throw invalidCredentialProof("Credential proof JWT header is missing $name")

    private fun JsonObject.optionalStringHeader(name: String): String? =
        optionalString(name, "Credential proof JWT header")

    private fun JsonObject.optionalString(name: String, location: String = "Credential proof JWK"): String? {
        val value = this[name] ?: return null
        return (value as? JsonPrimitive)
            ?.takeIf { it.isString && it.content.isNotBlank() }
            ?.content
            ?: throw invalidCredentialProof("$location $name must be a non-empty string")
    }

    private fun JsonObject.optionalStringClaim(name: String): String? {
        val value = this[name] ?: return null
        return (value as? JsonPrimitive)
            ?.takeIf { it.isString && it.content.isNotBlank() }
            ?.content
            ?: throw invalidCredentialProof("Credential proof claim $name must be a non-empty string")
    }

    private fun JsonObject.requiredLongClaim(name: String): Long {
        val value = this[name] ?: throw invalidCredentialProof("Credential proof claim $name is required")
        return (value as? JsonPrimitive)
            ?.takeUnless { it.isString }
            ?.longOrNull
            ?: throw invalidCredentialProof("Credential proof claim $name must be an integer")
    }

    private fun requireCredentialProof(value: Boolean, lazyMessage: () -> String) {
        if (!value) throw invalidCredentialProof(lazyMessage())
    }

    private data class ResolvedHolderKey(
        val key: Key,
        val kid: String?,
        val did: String?,
    )

    private companion object {
        const val DEFAULT_PROOF_MAX_AGE_SECONDS = 300L
        const val DEFAULT_CLOCK_SKEW_SECONDS = 60L
        const val JWT_TYPE = "openid4vci-proof+jwt"
        const val PROOF_NONCE_CLAIM = "nonce"
        const val JWT_HEADER_X5C = "x5c"
        const val JWT_HEADER_KEY_ATTESTATION = "key_attestation"
        const val JWT_HEADER_TRUST_CHAIN = "trust_chain"
        const val JWK_USE = "use"
        const val JWK_SIGNATURE_USE = "sig"
        const val JWK_KEY_OPERATIONS = "key_ops"
        const val JWK_VERIFY_OPERATION = "verify"
        val supportedAsymmetricAlgorithms = JwsAlgorithm.entries.map { it.identifier }.toSet()
    }
}
