@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package id.walt.openid4vci.preauthorized

import id.walt.openid4vci.Session
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.offers.TxCode
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
 * The token endpoint half still lives as a handler (`PreAuthorizedCodeTokenEndpoint`) because it
 * responds to an OAuth request. The issuing step is backend orchestration the caller triggers
 * explicitly. Splitting the responsibilities keeps handler registries focused on inbound traffic
 * while services cover issuer-facing workflows.
 */
interface PreAuthorizedCodeIssuer {
    suspend fun issue(request: PreAuthorizedCodeIssueRequest): PreAuthorizedCodeIssueResult
}

data class PreAuthorizedCodeIssueRequest(
    val clientId: String? = null,
    val txCode: TxCode? = null,
    val txCodeValue: String? = null,
    val scopes: Set<String> = emptySet(),
    val audience: Set<String> = emptySet(),
    val session: Session,
    val credentialNonce: String? = null,
    val credentialNonceExpiresAt: Instant? = null,
)

data class PreAuthorizedCodeIssueResult(
    val code: String,
    val expiresAt: Instant,
    val txCodeValue: String? = null,
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
        val subject = request.session.subject?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Session subject is required for pre-authorized code issuance")

        if (request.txCode == null && request.txCodeValue != null) {
            throw IllegalArgumentException("txCodeValue must be null when txCode is not configured")
        }
        validateTxCodeMetadata(request.txCode)

        val resolvedTxCodeValue = resolveTxCodeValue(request.txCode, request.txCodeValue)

        val now = kotlin.time.Clock.System.now()
        val expiresAt = now + codeLifetimeSeconds.seconds
        val sessionSnapshot = request.session.copy()
            .withSubject(subject)
            .withExpiresAt(TokenType.ACCESS_TOKEN, expiresAt)

        val hashedTxCode = resolvedTxCodeValue?.let { hashTxCode(it) }
        val (code, _) = generateAndSaveUnique { generatedCode ->
            DefaultPreAuthorizedCodeRecord(
                code = generatedCode,
                clientId = request.clientId,
                txCode = request.txCode,
                txCodeValue = hashedTxCode,
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
            txCodeValue = resolvedTxCodeValue,
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

private fun validateTxCodeMetadata(txCode: TxCode?) {
    if (txCode == null) return

    require(txCode.length == null || txCode.length > 0) {
        "txCode length must be positive when provided"
    }
    require(txCode.inputMode == null || txCode.inputMode == TX_CODE_INPUT_MODE_NUMERIC || txCode.inputMode == TX_CODE_INPUT_MODE_TEXT) {
        "txCode inputMode must be either \"$TX_CODE_INPUT_MODE_NUMERIC\" or \"$TX_CODE_INPUT_MODE_TEXT\" when provided"
    }
}

private fun resolveTxCodeValue(txCode: TxCode?, txCodeValue: String?): String? {
    if (txCode == null) return null
    val resolved = txCodeValue ?: generateTxCodeValue(txCode)
    validateTxCodeValue(txCode, resolved)
    return resolved
}

private fun validateTxCodeValue(txCode: TxCode, txCodeValue: String) {
    require(txCodeValue.isNotBlank()) {
        "txCodeValue must not be blank when txCode is configured"
    }
    txCode.length?.let { expectedLength ->
        require(txCodeValue.length == expectedLength) {
            "txCodeValue length must match txCode length"
        }
    }
    if (resolveTxCodeInputMode(txCode) == TX_CODE_INPUT_MODE_NUMERIC) {
        require(txCodeValue.all(Char::isDigit)) {
            "txCodeValue must contain only digits when txCode inputMode is numeric"
        }
    }
}

internal fun generateTxCodeValue(txCode: TxCode): String {
    val length = txCode.length ?: DEFAULT_GENERATED_TX_CODE_LENGTH
    val alphabet = if (resolveTxCodeInputMode(txCode) == TX_CODE_INPUT_MODE_TEXT) {
        TEXT_TX_CODE_ALPHABET
    } else {
        NUMERIC_TX_CODE_ALPHABET
    }
    return generateRandomString(alphabet, length)
}

private fun resolveTxCodeInputMode(txCode: TxCode): String =
    txCode.inputMode ?: TX_CODE_INPUT_MODE_NUMERIC

private fun generateRandomString(alphabet: String, length: Int): String =
    buildString(length) {
        repeat(length) {
            append(alphabet[secureRandomBytes(1)[0].toInt().and(0xFF) % alphabet.length])
        }
    }

internal fun hashTxCode(txCode: String): String =
    Base64.UrlSafe.encode(SHA256().digest(txCode.encodeToByteArray()))

internal fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom.nextBytes(it) }

private const val TX_CODE_INPUT_MODE_NUMERIC = "numeric"
private const val TX_CODE_INPUT_MODE_TEXT = "text"
private const val DEFAULT_GENERATED_TX_CODE_LENGTH = 6
private const val NUMERIC_TX_CODE_ALPHABET = "0123456789"
private const val TEXT_TX_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
