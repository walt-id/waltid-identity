package id.waltid.openid4vci.wallet.proof

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.prooftypes.Proofs
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.core.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private val log = KotlinLogging.logger {}

/**
 * Builds JWT proofs of possession for OpenID4VCI credential requests.
 * Implements §7.2.1 of OpenID4VCI 1.0 specification (JWT Proof Type).
 */
class JwtProofBuilder : ProofOfPossessionBuilder {

    override val proofType: String = "jwt"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    /**
     * Builds a JWT proof of possession
     * 
     * The JWT contains:
     * - typ: "openid4vci-proof+jwt" (header)
     * - kid or jwk (header, depending on binding method)
     * - iss: client_id or key identifier
     * - aud: credential issuer URL
     * - iat: current timestamp
     * - nonce: c_nonce from issuer
     * 
     * @param key The cryptographic key to use for signing
     * @param audience The credential issuer URL
     * @param nonce The c_nonce from the issuer
     * @param keyId Optional key identifier (DID) for kid header
     * @param includeJwk Whether to include the public key as JWK in the header
     * @return Proofs object containing the JWT proof
     */
    suspend fun buildJwtProof(
        key: Key,
        audience: String,
        nonce: String,
        keyId: String? = null,
        includeJwk: Boolean = false,
    ): Proofs {
        ProofBuilderUtils.validateProofParameters(audience, nonce)

        log.debug { "Building JWT proof for audience: $audience" }

        // Build JWT payload
        val payload = buildJsonObject {
            put("aud", audience)
            put("iat", ProofBuilderUtils.currentTimestampSeconds())
            put("nonce", nonce)
        }

        // Build JWT header with typ
        val header = buildJsonObject {
            put("typ", "openid4vci-proof+jwt")
            put("alg", key.keyType.jwsAlg)

            when {
                keyId != null -> {
                    // DID-based binding: use kid
                    put("kid", keyId)
                    log.debug { "Using DID-based binding with kid: $keyId" }
                }

                includeJwk -> {
                    // JWK-based binding: include public key
                    try {
                        val publicKeyJwk = key.getPublicKey().exportJWK()
                        put("jwk", Json.parseToJsonElement(publicKeyJwk))
                        log.debug { "Using JWK-based binding with embedded public key" }
                    } catch (e: Exception) {
                        log.error(e) { "Failed to export public key as JWK" }
                        throw Exception("Failed to export public key as JWK", e)
                    }
                }

                else -> {
                    // Default: use key's thumbprint as kid
                    val thumbprint = key.getThumbprint()
                    put("kid", thumbprint)
                    log.debug { "Using key thumbprint as kid: $thumbprint" }
                }
            }
        }

        // Sign the JWT
        val jwt = try {
            key.signJws(payload.toString().toByteArray(), header.toJsonElement().jsonObject)
        } catch (e: Exception) {
            log.error(e) { "Failed to sign JWT proof" }
            throw Exception("Failed to sign JWT proof", e)
        }

        log.debug { "Successfully built JWT proof" }

        // Return as Proofs object
        return Proofs(
            jwt = listOf(jwt)

        )
    }

    override suspend fun buildProof(
        key: Key,
        audience: String,
        nonce: String,
    ): Proofs {
        // Default: try to use DID if available, otherwise use JWK
        val keyId = key.getKeyId()
        val useDid = keyId?.startsWith("did:") == true

        return if (useDid) {
            buildJwtProof(key, audience, nonce, keyId = keyId, includeJwk = false)
        } else {
            buildJwtProof(key, audience, nonce, keyId = null, includeJwk = true)
        }
    }

    /**
     * Determines the appropriate binding method based on key and configuration
     */
    fun determineBindingMethod(key: Key, keyId: String?): CryptographicBindingMethod {
        return when {
            keyId?.startsWith("did:") == true -> CryptographicBindingMethod.fromValue("did")
            else -> CryptographicBindingMethod.Jwk
        }
    }
}
