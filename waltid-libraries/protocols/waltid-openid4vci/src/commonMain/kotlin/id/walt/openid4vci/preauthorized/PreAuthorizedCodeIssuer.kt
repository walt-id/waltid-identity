package id.walt.openid4vci.preauthorized

import id.walt.openid4vci.Session
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRecord
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.ExperimentalTime

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
    fun issue(request: PreAuthorizedCodeIssueRequest): PreAuthorizedCodeIssueResult
}

data class PreAuthorizedCodeIssueRequest @OptIn(ExperimentalTime::class) constructor(
    val issuerId: String,
    val clientId: String? = null,
    val userPinRequired: Boolean = false,
    val userPin: String? = null,
    val scopes: Set<String> = emptySet(),
    val audience: Set<String> = emptySet(),
    val session: Session,
    val credentialNonce: String? = null,
    val credentialNonceExpiresAt: Instant? = null,
)

data class PreAuthorizedCodeIssueResult @OptIn(ExperimentalTime::class) constructor(
    val code: String,
    val expiresAt: Instant,
    val credentialNonce: String? = null,
    val credentialNonceExpiresAt: Instant? = null,
)

class DefaultPreAuthorizedCodeIssuer(
    private val repository: PreAuthorizedCodeRepository,
    private val codeLifetimeSeconds: Long = 300,
) : PreAuthorizedCodeIssuer {

    @OptIn(ExperimentalTime::class)
    override fun issue(request: PreAuthorizedCodeIssueRequest): PreAuthorizedCodeIssueResult {
        val code = generateCode()
        val now = kotlin.time.Clock.System.now()
        val expiresAt = now + codeLifetimeSeconds.seconds
        val sessionSnapshot = request.session.cloneSession().apply {
            setExpiresAt(TokenType.AUTHORIZATION_CODE, expiresAt)
        }

        repository.save(
            PreAuthorizedCodeRecord(
                code = code,
                clientId = request.clientId,
                userPinRequired = request.userPinRequired,
                userPin = request.userPin,
                grantedScopes = request.scopes,
                grantedAudience = request.audience,
                session = sessionSnapshot,
                expiresAt = expiresAt,
                credentialNonce = request.credentialNonce,
                credentialNonceExpiresAt = request.credentialNonceExpiresAt,
            ),
            request.issuerId,
        )

        return PreAuthorizedCodeIssueResult(
            code = code,
            expiresAt = expiresAt,
            credentialNonce = request.credentialNonce,
            credentialNonceExpiresAt = request.credentialNonceExpiresAt,
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun generateCode(): String = Base64.UrlSafe.encode(Random.nextBytes(32))
}
