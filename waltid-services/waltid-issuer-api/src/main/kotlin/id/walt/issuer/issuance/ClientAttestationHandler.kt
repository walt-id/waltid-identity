package id.walt.issuer.issuance

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.oid4vc.util.JwtUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Handler for OAuth 2.0 Attestation-Based Client Authentication.
 * Implements draft-ietf-oauth-attestation-based-client-auth for EUDI wallet compatibility.
 */
object ClientAttestationHandler {
    private val logger = KotlinLogging.logger {}

    val SUPPORTED_ALGORITHMS = setOf(
        "ES256", "ES384", "ES512",
        "RS256", "RS384", "RS512",
        "PS256", "PS384", "PS512"
    )

    private val usedJtis = ConcurrentHashMap<String, Long>()
    private val JTI_EXPIRY_MS = 5.minutes.inWholeMilliseconds
    private val trustedAttestationIssuers = mutableSetOf<String>()

    sealed class AttestationValidationResult {
        data class Success(
            val clientId: String,
            val attestationIssuer: String,
            val cnfKey: JsonObject
        ) : AttestationValidationResult()

        data class Error(
            val errorCode: String,
            val message: String
        ) : AttestationValidationResult()
    }

    fun addTrustedIssuer(issuer: String) {
        trustedAttestationIssuers.add(issuer)
        logger.info { "Added trusted attestation issuer: $issuer" }
    }

    fun isPermissiveMode(): Boolean = trustedAttestationIssuers.isEmpty()

    suspend fun validateClientAttestation(
        attestationJwt: String,
        attestationPopJwt: String,
        expectedAudience: String,
        expectedClientId: String? = null
    ): AttestationValidationResult {
        logger.info { "=== CLIENT ATTESTATION VALIDATION ===" }
        logger.info { "Expected audience: $expectedAudience" }

        try {
            val attestationHeader = JwtUtils.parseJWTHeader(attestationJwt)
            val attestationPayload = JwtUtils.parseJWTPayload(attestationJwt)

            val attestationAlg = attestationHeader["alg"]?.jsonPrimitive?.contentOrNull
            if (attestationAlg == null || attestationAlg == "none") {
                return AttestationValidationResult.Error("invalid_client", "Attestation JWT must use asymmetric algorithm")
            }

            val attestationIss = attestationPayload["iss"]?.jsonPrimitive?.contentOrNull
                ?: return AttestationValidationResult.Error("invalid_client", "Attestation JWT missing 'iss' claim")

            val attestationSub = attestationPayload["sub"]?.jsonPrimitive?.contentOrNull
                ?: return AttestationValidationResult.Error("invalid_client", "Attestation JWT missing 'sub' claim")

            val attestationExp = attestationPayload["exp"]?.jsonPrimitive?.longOrNull
            if (attestationExp != null && attestationExp < Clock.System.now().epochSeconds) {
                return AttestationValidationResult.Error("invalid_client", "Attestation JWT has expired")
            }

            val cnf = attestationPayload["cnf"]?.jsonObject
                ?: return AttestationValidationResult.Error("invalid_client", "Attestation JWT missing 'cnf' claim")

            val cnfJwk = cnf["jwk"]?.jsonObject
                ?: return AttestationValidationResult.Error("invalid_client", "Attestation JWT 'cnf' claim missing 'jwk'")

            if (trustedAttestationIssuers.isNotEmpty() && !trustedAttestationIssuers.contains(attestationIss)) {
                return AttestationValidationResult.Error("invalid_client", "Attestation issuer not trusted")
            }

            logger.info { "Attestation issuer: $attestationIss, client_id: $attestationSub" }

            val popHeader = JwtUtils.parseJWTHeader(attestationPopJwt)
            val popPayload = JwtUtils.parseJWTPayload(attestationPopJwt)

            val popAlg = popHeader["alg"]?.jsonPrimitive?.contentOrNull
            if (popAlg == null || popAlg == "none") {
                return AttestationValidationResult.Error("invalid_client", "PoP JWT must use asymmetric algorithm")
            }

            val popIss = popPayload["iss"]?.jsonPrimitive?.contentOrNull
            if (popIss != attestationSub) {
                return AttestationValidationResult.Error("invalid_client", "PoP 'iss' must match attestation 'sub'")
            }

            val popJti = popPayload["jti"]?.jsonPrimitive?.contentOrNull
                ?: return AttestationValidationResult.Error("invalid_client", "PoP JWT missing 'jti' claim")

            if (isJtiUsed(popJti)) {
                return AttestationValidationResult.Error("invalid_client", "Replay attack detected")
            }

            val popIat = popPayload["iat"]?.jsonPrimitive?.longOrNull
                ?: return AttestationValidationResult.Error("invalid_client", "PoP JWT missing 'iat' claim")

            val currentTime = Clock.System.now().epochSeconds
            if (currentTime - popIat > 300L) {
                return AttestationValidationResult.Error("invalid_client", "PoP JWT is too old")
            }

            // Verify PoP signature using key from attestation's cnf claim
            try {
                val cnfKey = JWKKey.importJWK(cnfJwk.toString()).getOrThrow()
                val verificationResult = cnfKey.verifyJws(attestationPopJwt)
                if (!verificationResult.isSuccess) {
                    return AttestationValidationResult.Error("invalid_client", "PoP signature verification failed")
                }
                logger.info { "PoP signature verified successfully" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to verify PoP signature" }
                return AttestationValidationResult.Error("invalid_client", "PoP verification error: ${e.message}")
            }

            markJtiUsed(popJti)
            logger.info { "Client attestation validated for: $attestationSub" }

            return AttestationValidationResult.Success(
                clientId = attestationSub,
                attestationIssuer = attestationIss,
                cnfKey = cnfJwk
            )
        } catch (e: Exception) {
            logger.error(e) { "Client attestation validation error" }
            return AttestationValidationResult.Error("invalid_client", "Validation failed: ${e.message}")
        }
    }

    private fun isJtiUsed(jti: String): Boolean {
        cleanupExpiredJtis()
        return usedJtis.containsKey(jti)
    }

    private fun markJtiUsed(jti: String) {
        usedJtis[jti] = System.currentTimeMillis()
    }

    private fun cleanupExpiredJtis() {
        val cutoff = System.currentTimeMillis() - JTI_EXPIRY_MS
        usedJtis.entries.removeIf { it.value < cutoff }
    }
}
