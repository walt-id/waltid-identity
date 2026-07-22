package id.walt.verifier2.verification2

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.did.dids.resolver.Crypto2DidKeyResolver
import id.walt.did.dids.DidService
import id.walt.crypto2.serialization.BinaryData
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.encodeToString
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
 *    - DID subject: resolve the selected verification method and verify the signature
 *
 * Reference: SIOPv2 §7, OID4VP 1.0 §"Combining this specification with SIOPv2"
 */
object SelfIssuedIdTokenValidator {

    private val log = KotlinLogging.logger { }

    private const val JWK_THUMBPRINT_PREFIX = "urn:ietf:params:oauth:jwk-thumbprint"
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

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
        clock: Clock = Clock.System,
        didKeyResolver: Crypto2DidKeyResolver = Crypto2DidKeyResolver { did ->
            DidService.resolveToCrypto2Keys(did).getOrThrow()
        },
        allowedAlgorithms: Set<JwsAlgorithm> = JwsAlgorithm.entries.toSet(),
    ) {
        val decoded = runCatching { CompactJws.decodeUnverified(idToken) }.getOrElse {
            throw IllegalArgumentException("id_token is not a valid JWS: ${it.message}")
        }
        val payload = runCatching {
            Json.parseToJsonElement(decoded.payload.decodeToString(throwOnInvalidSequence = true)) as? JsonObject
                ?: throw IllegalArgumentException("id_token payload must be a JSON object")
        }.getOrElse { throw IllegalArgumentException("id_token has an invalid JSON payload: ${it.message}") }
        require(decoded.algorithm in allowedAlgorithms) {
            "id_token algorithm is not allowed: ${decoded.algorithm.identifier}"
        }

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
        val now = clock.now()
        val exp = Instant.fromEpochSeconds(expSeconds)
        require(now <= exp.plus(clockSkewSeconds.seconds)) {
            "id_token is expired: exp=$expSeconds, now=${now.epochSeconds}"
        }

        // 5. Signature verification - determine Subject Syntax Type from `sub`
        when {
            sub.startsWith("did:") -> {
                val keys = didKeyResolver.resolveToKeys(sub)
                val kid = decoded.protectedHeader["kid"]?.jsonPrimitive?.contentOrNull
                val verificationKey = when {
                    kid != null -> keys.singleOrNull { it.id.value == kid }
                        ?: throw IllegalArgumentException("id_token kid does not identify a DID verification method: $kid")
                    keys.size == 1 -> keys.single()
                    else -> throw IllegalArgumentException("id_token with multiple DID keys must include kid")
                }
                require(kid == null || kid == sub || kid.startsWith("$sub#")) {
                    "id_token kid is not controlled by the subject DID"
                }
                try {
                    CompactJws.verify(idToken, verificationKey, decoded.algorithm)
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    throw IllegalArgumentException("id_token DID signature verification failed: ${cause.message}", cause)
                }
                log.trace { "Self-Issued ID Token DID signature verified: sub=$sub, kid=$kid" }
            }

            sub.startsWith(JWK_THUMBPRINT_PREFIX) -> {
                // JWK Thumbprint Subject Syntax Type - verify with sub_jwk
                val subJwkElement = payload["sub_jwk"] as? JsonObject
                    ?: throw IllegalArgumentException(
                        "id_token with JWK Thumbprint subject must include 'sub_jwk' claim (SIOPv2 §6)"
                    )

                val stored = try {
                    EncodedKey.Jwk(
                        BinaryData(Json.encodeToString(subJwkElement).encodeToByteArray()),
                        privateMaterial = false,
                    ).toStoredSoftwareKey(KeyId("self-issued-sub-jwk"), setOf(KeyUsage.VERIFY))
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    throw IllegalArgumentException("id_token 'sub_jwk' is not a valid JWK: ${cause.message}", cause)
                }
                val encodedJwk = stored.material as EncodedKey.Jwk
                require(!encodedJwk.privateMaterial) { "id_token 'sub_jwk' must not contain private key material" }
                val subJwkKey = runtime.restore(stored)

                // Verify that sub equals the thumbprint of sub_jwk
                val expectedSub = "$JWK_THUMBPRINT_PREFIX:sha-256:${Jwk.sha256Thumbprint(encodedJwk)}"
                require(sub == expectedSub) {
                    "id_token 'sub' ($sub) does not match JWK thumbprint of 'sub_jwk' ($expectedSub)"
                }

                // Verify JWS signature using sub_jwk
                try {
                    CompactJws.verify(idToken, subJwkKey, decoded.algorithm)
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    throw IllegalArgumentException("id_token signature verification failed: ${cause.message}", cause)
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
