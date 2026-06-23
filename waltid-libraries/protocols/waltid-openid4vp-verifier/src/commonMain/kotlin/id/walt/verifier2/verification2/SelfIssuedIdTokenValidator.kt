package id.walt.verifier2.verification2

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Validates a Self-Issued ID Token per SIOPv2 §7 "Self-Issued ID Token Validation".
 *
 * Performs the following checks:
 * 1. `iss` == `sub` (self-issued check)
 * 2. `aud` == verifier's client_id
 * 3. `nonce` matches the request nonce
 * 4. `exp` is not in the past
 * 5. Signature verification:
 *    - JWK Thumbprint subject: verify with `sub_jwk` from payload; validate `sub` == thumbprint
 *    - DID subject: verify with resolved DID key (not yet implemented — logs warning)
 *
 * Reference: SIOPv2 §7, OID4VP 1.0 §"Combining this specification with SIOPv2"
 */
object SelfIssuedIdTokenValidator {

    private val log = KotlinLogging.logger { }

    private const val JWK_THUMBPRINT_PREFIX = "urn:ietf:params:oauth:jwk-thumbprint"

    /**
     * Validates the Self-Issued ID Token.
     *
     * @throws IllegalArgumentException if any validation check fails
     */
    suspend fun validate(
        idToken: String,
        expectedNonce: String,
        expectedAudience: String,
        clockSkewSeconds: Long = 60L,
    ) {
        val decoded = runCatching { idToken.decodeJws() }.getOrElse {
            throw IllegalArgumentException("id_token is not a valid JWS: ${it.message}")
        }

        val payload = decoded.payload

        val iss = payload["iss"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("id_token missing 'iss' claim")
        val sub = payload["sub"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("id_token missing 'sub' claim")
        val aud = payload["aud"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("id_token missing 'aud' claim")
        val nonce = payload["nonce"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("id_token missing 'nonce' claim")
        val expSeconds = payload["exp"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: throw IllegalArgumentException("id_token missing 'exp' claim")

        // 1. Self-issued check: iss MUST equal sub
        require(iss == sub) {
            "id_token 'iss' ($iss) must equal 'sub' ($sub) for a Self-Issued ID Token (SIOPv2 §7)"
        }

        // 2. Audience check
        require(aud == expectedAudience) {
            "id_token 'aud' ($aud) does not match expected audience ($expectedAudience)"
        }

        // 3. Nonce check
        require(nonce == expectedNonce) {
            "id_token 'nonce' does not match the request nonce"
        }

        // 4. Expiry check (with clock skew)
        val now = Clock.System.now()
        val exp = Instant.fromEpochSeconds(expSeconds)
        require(now <= exp.plus(clockSkewSeconds.seconds)) {
            "id_token is expired: exp=$expSeconds, now=${now.epochSeconds}"
        }

        // 5. Signature verification — determine Subject Syntax Type from `sub`
        when {
            sub.startsWith("did:") -> {
                // DID Subject Syntax Type — full DID resolution and verification
                // Delegate to the existing DidKeyResolver / DID verification infrastructure
                log.warn {
                    "id_token uses DID subject syntax type ($sub) — " +
                        "DID-based Self-Issued ID Token verification is not yet fully implemented. " +
                        "Skipping signature verification."
                }
                // TODO: resolve DID, extract verificationMethod by kid, verify JWS signature
            }

            sub.startsWith(JWK_THUMBPRINT_PREFIX) -> {
                // JWK Thumbprint Subject Syntax Type — verify with sub_jwk
                val subJwkElement = payload["sub_jwk"]?.jsonObject
                    ?: throw IllegalArgumentException(
                        "id_token with JWK Thumbprint subject must include 'sub_jwk' claim (SIOPv2 §6)"
                    )

                val subJwkKey = runCatching {
                    JWKKey.importJWK(subJwkElement.toString()).getOrThrow()
                }.getOrElse {
                    throw IllegalArgumentException("id_token 'sub_jwk' is not a valid JWK: ${it.message}")
                }

                // Verify that sub equals the thumbprint of sub_jwk
                val expectedSub = "$JWK_THUMBPRINT_PREFIX:sha-256:${subJwkKey.getThumbprint()}"
                require(sub == expectedSub) {
                    "id_token 'sub' ($sub) does not match JWK thumbprint of 'sub_jwk' ($expectedSub)"
                }

                // Verify JWS signature using sub_jwk
                runCatching { subJwkKey.verifyJws(idToken).getOrThrow() }.getOrElse {
                    throw IllegalArgumentException("id_token signature verification failed: ${it.message}")
                }

                log.trace { "Self-Issued ID Token JWK Thumbprint signature verified: sub=$sub" }
            }

            else -> {
                throw IllegalArgumentException(
                    "id_token 'sub' ($sub) uses an unrecognized Subject Syntax Type. " +
                        "Expected 'did:...' or '$JWK_THUMBPRINT_PREFIX:...' (SIOPv2 §4.1)"
                )
            }
        }
    }
}
