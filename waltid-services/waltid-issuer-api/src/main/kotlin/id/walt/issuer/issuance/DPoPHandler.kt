package id.walt.issuer.issuance

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.oid4vc.util.JwtUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import java.security.MessageDigest
import kotlin.time.Clock

/**
 * DPoP (Demonstrating Proof of Possession) Handler
 * Implements RFC 9449: OAuth 2.0 Demonstrating Proof of Possession (DPoP)
 */
object DPoPHandler {
    private val log = KotlinLogging.logger { }

    // Supported DPoP signing algorithms
    val SUPPORTED_ALGORITHMS = setOf("ES256", "ES384", "ES512", "RS256", "RS384", "RS512")

    // Maximum age for DPoP proof (5 minutes)
    private const val MAX_PROOF_AGE_SECONDS = 300L

    // JTI cache to prevent replay attacks (simple in-memory implementation)
    private val usedJtis = mutableSetOf<String>()

    /**
     * Validates a DPoP proof JWT
     */
    suspend fun validateDPoPProof(
        dpopProof: String,
        httpMethod: String,
        httpUri: String,
        accessTokenHash: String? = null
    ): DPoPValidationResult {
        try {
            log.info { "=== VALIDATING DPoP PROOF ===" }
            log.info { "HTTP Method: $httpMethod, HTTP URI: $httpUri" }

            val header = JwtUtils.parseJWTHeader(dpopProof)
            val payload = JwtUtils.parseJWTPayload(dpopProof)

            // 1. Verify typ header is "dpop+jwt"
            val typ = header["typ"]?.jsonPrimitive?.content
            if (typ != "dpop+jwt") {
                return DPoPValidationResult.Error("Invalid DPoP proof: typ must be 'dpop+jwt'")
            }

            // 2. Verify alg is supported
            val alg = header["alg"]?.jsonPrimitive?.content
            if (alg == null || alg !in SUPPORTED_ALGORITHMS) {
                return DPoPValidationResult.Error("Invalid DPoP proof: unsupported algorithm")
            }

            // 3. Extract and verify jwk header
            val jwk = header["jwk"]?.jsonObject
                ?: return DPoPValidationResult.Error("Invalid DPoP proof: missing jwk header")

            if (jwk.containsKey("d")) {
                return DPoPValidationResult.Error("Invalid DPoP proof: jwk must not contain private key")
            }

            // 4. Verify jti
            val jti = payload["jti"]?.jsonPrimitive?.content
                ?: return DPoPValidationResult.Error("Invalid DPoP proof: missing jti")

            synchronized(usedJtis) {
                if (jti in usedJtis) {
                    return DPoPValidationResult.Error("Invalid DPoP proof: jti already used")
                }
                usedJtis.add(jti)
                if (usedJtis.size > 10000) usedJtis.clear()
            }

            // 5. Verify htm
            val htm = payload["htm"]?.jsonPrimitive?.content
            if (htm == null || htm.uppercase() != httpMethod.uppercase()) {
                return DPoPValidationResult.Error("Invalid DPoP proof: htm mismatch")
            }

            // 6. Verify htu
            val htu = payload["htu"]?.jsonPrimitive?.content
            if (htu == null || !uriMatches(htu, httpUri)) {
                return DPoPValidationResult.Error("Invalid DPoP proof: htu mismatch")
            }

            // 7. Verify iat
            val iat = payload["iat"]?.jsonPrimitive?.longOrNull
                ?: return DPoPValidationResult.Error("Invalid DPoP proof: missing iat")

            val currentTime = Clock.System.now().epochSeconds
            if (currentTime - iat > MAX_PROOF_AGE_SECONDS) {
                return DPoPValidationResult.Error("Invalid DPoP proof: proof expired")
            }

            // 8. Verify ath if provided
            if (accessTokenHash != null) {
                val ath = payload["ath"]?.jsonPrimitive?.content
                if (ath == null || ath != accessTokenHash) {
                    return DPoPValidationResult.Error("Invalid DPoP proof: ath mismatch")
                }
            }

            // 9. Verify signature
            val key = JWKKey.importJWK(jwk.toString()).getOrThrow()
            val signatureValid = key.verifyJws(dpopProof).isSuccess
            if (!signatureValid) {
                return DPoPValidationResult.Error("Invalid DPoP proof: signature verification failed")
            }

            // 10. Calculate JWK thumbprint
            val thumbprint = calculateJwkThumbprint(jwk)
            log.info { "DPoP validation successful. JWK thumbprint: $thumbprint" }

            return DPoPValidationResult.Success(thumbprint, jwk)
        } catch (e: Exception) {
            log.error(e) { "DPoP validation failed" }
            return DPoPValidationResult.Error("Invalid DPoP proof: ${e.message}")
        }
    }

    fun calculateJwkThumbprint(jwk: JsonObject): String {
        val kty = jwk["kty"]?.jsonPrimitive?.content
        val canonicalJwk = when (kty) {
            "EC" -> buildJsonObject {
                put("crv", jwk["crv"]!!)
                put("kty", jwk["kty"]!!)
                put("x", jwk["x"]!!)
                put("y", jwk["y"]!!)
            }
            "RSA" -> buildJsonObject {
                put("e", jwk["e"]!!)
                put("kty", jwk["kty"]!!)
                put("n", jwk["n"]!!)
            }
            else -> jwk
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalJwk.toString().toByteArray(Charsets.UTF_8))
        return digest.encodeToBase64Url()
    }

    fun calculateAccessTokenHash(accessToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(accessToken.toByteArray(Charsets.US_ASCII))
        return digest.encodeToBase64Url()
    }

    private fun uriMatches(htu: String, requestUri: String): Boolean {
        val htuBase = htu.substringBefore("?").substringBefore("#")
        val requestBase = requestUri.substringBefore("?").substringBefore("#")
        return htuBase == requestBase
    }

    sealed class DPoPValidationResult {
        data class Success(val thumbprint: String, val jwk: JsonObject) : DPoPValidationResult()
        data class Error(val message: String) : DPoPValidationResult()
    }
}
