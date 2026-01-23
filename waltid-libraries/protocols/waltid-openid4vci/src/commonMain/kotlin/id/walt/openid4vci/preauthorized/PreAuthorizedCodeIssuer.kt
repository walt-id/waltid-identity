@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package id.walt.openid4vci.preauthorized

import id.walt.openid4vci.Session
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import id.walt.openid4vci.repository.preauthorized.DefaultPreAuthorizedCodeRecord
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import korlibs.crypto.SecureRandom
import org.kotlincrypto.hash.sha2.SHA256

/**
 * Service API for issuing OpenID4VCI pre-authorized codes.
 *
 * This is intentionally not a handler?: issuance happens before any OAuth endpoint is invoked, so
 * callers trigger it out-of-band when preparing a credential offer. We keep handlers for inbound
 * HTTP flows.
 * This service covers the issuer’s own step, storing the grant so the token handler can redeem it later.
 *
 * The implementation must generate a one-time code, persists it via [PreAuthorizedCodeRepository], and
 * returns the data the wallet needs to redeem the grant at the token endpoint.
 *
 * The token endpoint half still lives as a handler (`PreAuthorizedCodeTokenHandler`) because it
 * responds to an OAuth request. The issuing step is backend orchestration the caller triggers
 * explicitly. Splitting the responsibilities keeps handler registries focused on inbound traffic
 * while services cover issuer-facing workflows.
 */
interface PreAuthorizedCodeIssuer {
    suspend fun issue(request: PreAuthorizedCodeIssueRequest): PreAuthorizedCodeIssueResult
}

data class PreAuthorizedCodeIssueRequest(
    val clientId: String? = null,
    val userPinRequired: Boolean = false,
    val userPin: String? = null,
    val scopes: Set<String> = emptySet(),
    val audience: Set<String> = emptySet(),
    val session: Session,
    val credentialNonce: String? = null,
    val credentialNonceExpiresAt: Instant? = null,
)

data class PreAuthorizedCodeIssueResult(
    val code: String,
    val expiresAt: Instant,
    val credentialNonce: String? = null,
    val credentialNonceExpiresAt: Instant? = null,
)

class DefaultPreAuthorizedCodeIssuer(
    private val repository: PreAuthorizedCodeRepository,
    private val codeLifetimeSeconds: Long = 300,
    private val maxGenerateAttempts: Int = 3,
) : PreAuthorizedCodeIssuer {

    init {
        require(codeLifetimeSeconds > 0) { "codeLifetimeSeconds must be positive" }
    }

    override suspend fun issue(request: PreAuthorizedCodeIssueRequest): PreAuthorizedCodeIssueResult {
        val subject = request.session.getSubject()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Session subject is required for pre-authorized code issuance")

        if (request.userPinRequired && request.userPin.isNullOrBlank()) {
            throw IllegalArgumentException("userPin is required when userPinRequired=true")
        }

        val now = kotlin.time.Clock.System.now()
        val expiresAt = now + codeLifetimeSeconds.seconds
        val sessionSnapshot = request.session.cloneSession().apply {
            setSubject(subject)
            setExpiresAt(TokenType.ACCESS_TOKEN, expiresAt)
        }

        val hashedPin = request.userPin?.let { hashPin(it) }
        val (code, _) = generateAndSaveUnique { generatedCode ->
            DefaultPreAuthorizedCodeRecord(
                code = generatedCode,
                clientId = request.clientId,
                userPinRequired = request.userPinRequired,
                userPin = hashedPin,
                grantedScopes = request.scopes,
                grantedAudience = request.audience,
                session = sessionSnapshot,
                expiresAt = expiresAt,
                credentialNonce = request.credentialNonce,
                credentialNonceExpiresAt = request.credentialNonceExpiresAt,
            )
        }

        return PreAuthorizedCodeIssueResult(
            code = code,
            expiresAt = expiresAt,
            credentialNonce = request.credentialNonce,
            credentialNonceExpiresAt = request.credentialNonceExpiresAt,
        )
    }

    private suspend fun generateAndSaveUnique(
        buildRecord: (String) -> DefaultPreAuthorizedCodeRecord,
    ): Pair<String, DefaultPreAuthorizedCodeRecord> {
        repeat(maxGenerateAttempts) { attempt ->
            val code = Base64.UrlSafe.encode(secureRandomBytes(33))
            try {
                val record = buildRecord(code)
                repository.save(record)
                return code to record
            } catch (e: DuplicateCodeException) {
                if (attempt == maxGenerateAttempts - 1) throw e
            }
        }
        throw IllegalStateException(
            "Failed to generate a unique pre-authorized code after $maxGenerateAttempts attempts"
        )
    }
}

internal fun hashPin(pin: String): String =
    Base64.UrlSafe.encode(SHA256().digest(pin.encodeToByteArray()))

internal fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom.nextBytes(it) }
