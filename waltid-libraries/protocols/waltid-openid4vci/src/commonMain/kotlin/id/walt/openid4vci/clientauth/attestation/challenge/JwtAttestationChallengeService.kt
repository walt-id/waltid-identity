package id.walt.openid4vci.clientauth.attestation.challenge

import id.walt.openid4vci.tokens.jwt.Crypto2JwtSigningKeyResolver
import id.walt.openid4vci.tokens.jwt.Crypto2JwtVerificationKeyResolver
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import id.walt.openid4vci.tokens.jwt.JwtSigningKeyResolver
import id.walt.openid4vci.tokens.jwt.JwtTokenSigner
import id.walt.openid4vci.tokens.jwt.JwtTokenVerifier
import id.walt.openid4vci.tokens.jwt.JwtVerificationKeyResolver
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.kotlincrypto.random.CryptoRand
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Stateless JWT-backed client attestation challenge service.
 *
 * A valid challenge remains reusable at PAR and TOKEN until it expires. The signed purpose
 * and issuer claims prevent substitution of credential nonces or other internal tokens.
 */
class JwtAttestationChallengeService private constructor(
    private val signer: JwtTokenSigner,
    private val verifier: JwtTokenVerifier,
    private val challengeLifetime: Duration = DEFAULT_CHALLENGE_LIFETIME.seconds,
    private val now: () -> Instant = { Clock.System.now() },
) : AttestationChallengeService {

    @Deprecated("Use the crypto2 factory")
    constructor(
        signingKeyResolver: JwtSigningKeyResolver,
        verificationKeyResolver: JwtVerificationKeyResolver,
        challengeLifetime: Duration = DEFAULT_CHALLENGE_LIFETIME.seconds,
        now: () -> Instant = { Clock.System.now() },
    ) : this(
        signer = JwtTokenSigner(signingKeyResolver),
        verifier = JwtTokenVerifier(verificationKeyResolver),
        challengeLifetime = challengeLifetime,
        now = now,
    )

    init {
        require(challengeLifetime > Duration.ZERO) { "Attestation challenge lifetime must be positive" }
    }

    override suspend fun issue(authorizationServerIssuer: String): String {
        require(authorizationServerIssuer.isNotBlank()) { "Authorization server issuer must not be blank" }
        val issuedAt = now()
        val expiresAt = issuedAt + challengeLifetime
        val challenge = signer.sign(
            claims = mapOf(
                JwtPayloadClaims.ISSUER to authorizationServerIssuer,
                PURPOSE_CLAIM to PURPOSE,
                JwtPayloadClaims.ISSUED_AT to issuedAt.epochSeconds,
                JwtPayloadClaims.EXPIRATION to expiresAt.epochSeconds,
                JwtPayloadClaims.JWT_ID to randomChallengeId(),
            ),
        )
        return challenge
    }

    override suspend fun validate(
        challenge: String,
        authorizationServerIssuer: String,
    ): Boolean = try {
        val payload = verifier.verify(challenge, "Attestation challenge")
        require(payload.requiredStringClaim(JwtPayloadClaims.ISSUER) == authorizationServerIssuer)
        require(payload.requiredStringClaim(PURPOSE_CLAIM) == PURPOSE)
        require(payload.requiredLongClaim(JwtPayloadClaims.EXPIRATION) > now().epochSeconds)
        require(payload.requiredStringClaim(JwtPayloadClaims.JWT_ID).hasRequiredEntropy())
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun randomChallengeId(): String {
        // JWT ID (jti): fresh 256-bit random data makes every signed challenge unique and
        // unpredictable without server-side storage; it is not a replay-store key by default.
        return base64Url.encode(CryptoRand.nextBytes(ByteArray(CHALLENGE_ID_BYTES)))
    }

    private fun JsonObject.requiredStringClaim(name: String): String =
        (this[name] as? JsonPrimitive)
            ?.takeIf { it.isString && it.content.isNotBlank() }
            ?.content
            ?: throw IllegalArgumentException("Attestation challenge claim $name must be a non-empty string")

    private fun JsonObject.requiredLongClaim(name: String): Long =
        this[name]?.jsonPrimitive
            ?.takeUnless { it.isString }
            ?.longOrNull
            ?: throw IllegalArgumentException("Attestation challenge claim $name must be an integer")

    private fun String.hasRequiredEntropy(): Boolean =
        runCatching { base64Url.decode(this).size >= CHALLENGE_ID_BYTES }.getOrDefault(false)

    companion object {
        private const val DEFAULT_CHALLENGE_LIFETIME = 300L
        private const val CHALLENGE_ID_BYTES = 32
        // Internal marker, not a spec-defined claim: prevents c_nonce or other tokens signed with
        // the same key from being accepted as attestation challenges.
        private const val PURPOSE_CLAIM = "purpose"
        private const val PURPOSE = "oauth-client-attestation-challenge"
        private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

        /**
         * Crypto2-backed challenge service. The challenge is an internally issued and internally consumed JWT, so the same
         * key serves both directions; the resolvers stay separate to allow key rotation to add a verification-only
         * predecessor without reissuing under it.
         */
        fun crypto2(
            signingKeyResolver: Crypto2JwtSigningKeyResolver,
            verificationKeyResolver: Crypto2JwtVerificationKeyResolver,
            challengeLifetime: Duration = DEFAULT_CHALLENGE_LIFETIME.seconds,
            now: () -> Instant = { Clock.System.now() },
        ): JwtAttestationChallengeService = JwtAttestationChallengeService(
            signer = JwtTokenSigner(signingKeyResolver),
            verifier = JwtTokenVerifier(verificationKeyResolver),
            challengeLifetime = challengeLifetime,
            now = now,
        )
    }
}
