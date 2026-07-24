package id.walt.openid4vci.proofs

import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import id.walt.openid4vci.tokens.jwt.JwtSigningKeyResolver
import id.walt.openid4vci.tokens.jwt.JwtTokenSigner
import id.walt.openid4vci.tokens.jwt.JwtTokenVerifier
import id.walt.openid4vci.tokens.jwt.JwtVerificationKeyResolver
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.kotlincrypto.random.CryptoRand
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Stateless JWT-backed OpenID4VCI nonce service.
 *
 * A valid nonce remains reusable until it expires. Deployments that require one-time nonce
 * redemption can layer a replay store on the JWT ID without changing this protocol default.
 */
class JwtCredentialNonceService(
    signingKeyResolver: JwtSigningKeyResolver,
    verificationKeyResolver: JwtVerificationKeyResolver,
    private val nonceLifetime: Duration = DEFAULT_NONCE_LIFETIME_SECONDS.seconds,
    private val now: () -> Instant = { Clock.System.now() },
) : CredentialNonceService {
    private val signer = JwtTokenSigner(signingKeyResolver)
    private val verifier = JwtTokenVerifier(verificationKeyResolver)

    init {
        require(nonceLifetime > Duration.ZERO) { "Credential nonce lifetime must be positive" }
    }

    override suspend fun issue(binding: CredentialNonceBinding): IssuedCredentialNonce {
        val issuedAt = now()
        val expiresAt = issuedAt + nonceLifetime
        val nonce = signer.sign(
            claims = mapOf(
                JwtPayloadClaims.ISSUER to binding.credentialIssuer,
                JwtPayloadClaims.AUDIENCE to listOf(binding.credentialEndpoint),
                JwtPayloadClaims.ISSUED_AT to issuedAt.epochSeconds,
                JwtPayloadClaims.EXPIRATION to expiresAt.epochSeconds,
                JwtPayloadClaims.JWT_ID to randomNonceId(),
                SOURCE_ENDPOINT_CLAIM to binding.nonceEndpoint,
            ),
        )
        return IssuedCredentialNonce(
            nonce = nonce,
            expiresInSeconds = nonceLifetime.inWholeSeconds,
        )
    }

    override suspend fun validate(
        nonce: String,
        binding: CredentialNonceBinding,
    ): CredentialNonceValidationResult = try {
        val payload = verifier.verify(nonce, "Credential nonce")
        validatePayload(payload, binding)
        CredentialNonceValidationResult.VALID
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        CredentialNonceValidationResult.INVALID
    }

    private fun validatePayload(payload: JsonObject, binding: CredentialNonceBinding) {
        require(payload.requiredStringClaim(JwtPayloadClaims.ISSUER) == binding.credentialIssuer) {
            "Credential nonce issuer does not match"
        }
        require(binding.credentialEndpoint in payload.audience()) {
            "Credential nonce audience does not match"
        }
        require(payload.requiredStringClaim(SOURCE_ENDPOINT_CLAIM) == binding.nonceEndpoint) {
            "Credential nonce source endpoint does not match"
        }
        require(payload.requiredLongClaim(JwtPayloadClaims.EXPIRATION) > now().epochSeconds) {
            "Credential nonce is expired"
        }
        require(payload.requiredStringClaim(JwtPayloadClaims.JWT_ID).hasRequiredEntropy()) {
            "Credential nonce ID is invalid"
        }
    }

    private fun randomNonceId(): String {
        // JWT ID (jti): fresh 256-bit random data makes every signed nonce unique and
        // unpredictable without server-side storage; it is not a replay-store key by default.
        return CryptoRand.nextBytes(ByteArray(NONCE_ID_BYTES)).encodeToBase64Url()
    }

    private fun JsonObject.requiredStringClaim(name: String): String =
        (this[name] as? JsonPrimitive)
            ?.takeIf { it.isString && it.content.isNotBlank() }
            ?.content
            ?: throw IllegalArgumentException("Credential nonce claim $name must be a non-empty string")

    private fun JsonObject.requiredLongClaim(name: String): Long =
        this[name]?.jsonPrimitive
            ?.takeUnless { it.isString }
            ?.longOrNull
            ?: throw IllegalArgumentException("Credential nonce claim $name must be an integer")

    private fun JsonObject.audience(): Set<String> {
        val audience = this[JwtPayloadClaims.AUDIENCE]
            ?: throw IllegalArgumentException("Credential nonce audience claim is required")
        return when (audience) {
            is JsonArray -> audience.mapNotNull { it.stringContentOrNull() }.toSet()
            is JsonPrimitive -> setOfNotNull(audience.stringContentOrNull())
            else -> emptySet()
        }.filter { it.isNotBlank() }.toSet().also {
            require(it.isNotEmpty()) { "Credential nonce audience claim must not be empty" }
        }
    }

    private fun kotlinx.serialization.json.JsonElement.stringContentOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun String.hasRequiredEntropy(): Boolean =
        runCatching { decodeFromBase64Url().size >= NONCE_ID_BYTES }.getOrDefault(false)

    private companion object {
        const val DEFAULT_NONCE_LIFETIME_SECONDS = 300L
        const val NONCE_ID_BYTES = 32
        const val SOURCE_ENDPOINT_CLAIM = "source_endpoint"
    }
}
