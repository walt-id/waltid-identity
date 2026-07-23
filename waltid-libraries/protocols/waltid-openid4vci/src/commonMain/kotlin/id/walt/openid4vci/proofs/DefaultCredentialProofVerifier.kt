package id.walt.openid4vci.proofs

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.ProofType
import id.walt.openid4vci.prooftypes.Proofs
import id.walt.openid4vci.prooftypes.ProofTypeId
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.Instant

class DefaultCredentialProofVerifier(
    private val proofMaxAgeSeconds: Long = DEFAULT_PROOF_MAX_AGE_SECONDS,
    private val clockSkewSeconds: Long = DEFAULT_CLOCK_SKEW_SECONDS,
    private val now: () -> Instant = { Clock.System.now() },
) : CredentialProofVerifier {

    init {
        require(proofMaxAgeSeconds > 0) { "Credential proof maximum age must be positive" }
        require(clockSkewSeconds >= 0) { "Credential proof clock skew must not be negative" }
    }

    override suspend fun verify(
        credentialRequest: CredentialRequest,
        credentialConfiguration: CredentialConfiguration,
        context: CredentialProofValidationContext,
    ): List<VerifiedCredentialProof> {
        val supportedProofTypes = credentialConfiguration.proofTypesSupported
        val proofs = credentialRequest.proofs
        if (supportedProofTypes == null && proofs == null) return emptyList()
        if (proofs == null) throw invalidCredentialProof("Credential request is missing proofs")

        val presentTypes = proofs.presentTypes()
        if (presentTypes.size != 1) {
            throw invalidCredentialProof("Credential request must contain exactly one proof type")
        }
        if (ProofTypeId.JWT.value !in presentTypes) {
            throw invalidCredentialProof("Unsupported credential proof type: ${presentTypes.single()}")
        }

        val jwtProofType = supportedProofTypes?.get(ProofTypeId.JWT.value)
            ?: supportedProofTypes?.let {
                throw invalidCredentialProof("JWT proofs are not supported for this credential configuration")
            }
        val jwtProofs = proofs.jwt ?: throw invalidCredentialProof("Credential request is missing JWT proofs")

        return jwtProofs.map { proofJwt ->
            verifyJwtProof(
                proofJwt = proofJwt,
                proofType = jwtProofType,
                credentialConfiguration = credentialConfiguration,
                context = context,
            )
        }
    }

    private suspend fun verifyJwtProof(
        proofJwt: String,
        proofType: ProofType?,
        credentialConfiguration: CredentialConfiguration,
        context: CredentialProofValidationContext,
    ): VerifiedCredentialProof {
        val decoded = runCatching { proofJwt.decodeJws() }
            .getOrElse { throw invalidCredentialProof("Invalid credential proof JWT", it) }

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
        val verifiedPayload = resolvedHolderKey.key.verifyJws(proofJwt).getOrElse {
            throw invalidCredentialProof("Invalid credential proof signature", it)
        } as? JsonObject ?: throw invalidCredentialProof("Credential proof payload must be a JSON object")

        validateAudience(verifiedPayload, context.credentialIssuer)
        validateIssuedAt(verifiedPayload)
        validateIssuer(verifiedPayload, context)
        validateNonce(verifiedPayload, context)

        return VerifiedCredentialProof(
            proofType = ProofTypeId.JWT.value,
            jwt = proofJwt,
            algorithm = algorithm,
            header = decoded.header,
            payload = verifiedPayload,
            holderKey = resolvedHolderKey.key,
            holderKid = resolvedHolderKey.kid,
            holderDid = resolvedHolderKey.did,
            nonce = verifiedPayload.optionalStringClaim(PROOF_NONCE_CLAIM),
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
                val key = JWKKey.importJWK(jwk.toString()).getOrElse {
                    throw invalidCredentialProof("Credential proof contains an invalid JWK", it)
                }
                requireCredentialProof(!key.hasPrivateKey) {
                    "Credential proof JWK must not contain private key material"
                }
                val publicKey = key.getPublicKey()
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
                val key = DidService.resolveToKey(did).getOrElse {
                    throw invalidCredentialProof("Could not resolve credential proof DID key", it)
                }.getPublicKey()
                validateKeyAlgorithm(key, header.requiredStringHeader(JwtHeaderParams.ALGORITHM))
                ResolvedHolderKey(key = key, kid = holderKid, did = did)
            }

            else -> throw invalidCredentialProof("Credential proof JWT header must contain kid or jwk")
        }
    }

    private fun rejectUnsupportedTrustHeaders(header: JsonObject) {
        if (JWT_HEADER_KEY_ATTESTATION in header) {
            throw invalidCredentialProof("Credential proof key_attestation is not supported")
        }
        if (JWT_HEADER_TRUST_CHAIN in header) {
            throw invalidCredentialProof("Credential proof trust_chain is not supported")
        }
    }

    private fun validateKeyAlgorithm(key: Key, algorithm: String) {
        requireCredentialProof(key.keyType.jwsAlg == algorithm) {
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
        val audiences = payload.extractAudience()
        requireCredentialProof(credentialIssuer in audiences) {
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
        issuer?.let {
            requireCredentialProof(context.clientId != null && it == context.clientId) {
                "Credential proof issuer claim must match the access token client_id"
            }
        }
    }

    private suspend fun validateNonce(payload: JsonObject, context: CredentialProofValidationContext) {
        val nonceValidation = context.nonceValidation ?: return
        val nonce = payload.optionalStringClaim(PROOF_NONCE_CLAIM)
            ?: throw invalidCredentialNonce("Credential proof nonce is required")
        val result = try {
            nonceValidation.service.validate(nonce, nonceValidation.binding)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw invalidCredentialNonce("Credential proof nonce verification failed", e)
        }
        if (result != CredentialNonceValidationResult.VALID) {
            throw invalidCredentialNonce("Credential proof nonce is invalid")
        }
    }

    private fun Proofs.presentTypes(): Set<String> = buildSet {
        if (!jwt.isNullOrEmpty()) add(ProofTypeId.JWT.value)
        if (!diVp.isNullOrEmpty()) add(ProofTypeId.DI_VP.value)
        if (!attestation.isNullOrEmpty()) add(ProofTypeId.ATTESTATION.value)
        additional.keys.forEach { add(it) }
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

    private fun JsonObject.extractAudience(): Set<String> {
        val element = this[JwtPayloadClaims.AUDIENCE]
            ?: throw invalidCredentialProof("Credential proof audience claim is required")
        return when (element) {
            is JsonArray -> element.map { audience ->
                (audience as? JsonPrimitive)?.contentOrNull
                    ?: throw invalidCredentialProof(
                        "Credential proof audience claim must be a non-empty string or string array",
                    )
            }.toSet()
            is JsonPrimitive -> element.contentOrNull?.let(::setOf).orEmpty()
            else -> emptySet()
        }.also { audiences ->
            requireCredentialProof(audiences.isNotEmpty()) {
                "Credential proof audience claim must be a non-empty string or string array"
            }
        }
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
        val supportedAsymmetricAlgorithms = KeyType.entries.map { it.jwsAlg }.toSet()
    }
}
