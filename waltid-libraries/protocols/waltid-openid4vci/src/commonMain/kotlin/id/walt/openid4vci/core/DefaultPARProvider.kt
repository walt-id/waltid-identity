package id.walt.openid4vci.core

import id.walt.crypto.utils.UuidUtils
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.repository.par.PAREntry
import id.walt.openid4vci.repository.par.PARRepository
import id.walt.openid4vci.requests.par.PushedAuthorizationRequest
import id.walt.openid4vci.responses.par.PushedAuthorizationResponse
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Default PAR provider implementation using a PARRepository.
 *
 * Implements RFC 9126 PAR lifecycle:
 * - Store pushed authorization requests
 * - Generate request_uri
 * - Resolve and consume request_uri during authorization
 */
class DefaultPARProvider(
    private val parRepository: PARRepository,
    private val parExpirySeconds: Int = 90, // RFC 9126 recommendation
) : PARProvider {

    init {
        require(parExpirySeconds > 0) { "PAR expiry must be positive" }
    }

    override suspend fun processPushedAuthorizationRequest(
        request: PushedAuthorizationRequest,
        clientAuthentication: Map<String, String>,
    ): PushedAuthorizationResponse {
        // RFC 9126 §2.1: Validate the authorization request
        validatePARRequest(request)

        // Generate unique request ID
        val requestId = UuidUtils.randomUUIDString()
        val now = Clock.System.now()
        val expiresAt = now + parExpirySeconds.seconds

        // Store PAR entry
        val entry = PAREntry(
            requestId = requestId,
            request = request,
            createdAt = now,
            expiresAt = expiresAt,
            consumed = false,
            clientMetadata = clientAuthentication,
        )
        parRepository.store(entry)

        // Return PAR response
        return PushedAuthorizationResponse.create(
            requestId = requestId,
            expiresIn = parExpirySeconds
        )
    }

    override suspend fun resolveRequestUri(
        requestUri: String,
        clientId: String,
    ): Map<String, List<String>>? {
        // RFC 9126 §2.3: Extract request ID from request_uri
        val requestId = PushedAuthorizationResponse.extractRequestId(requestUri)
            ?: return null

        // Retrieve and validate PAR entry
        val now = Clock.System.now()
        val entry = parRepository.findByRequestId(requestId, now)
            ?: return null

        // RFC 9126 §2.3: Validate client_id matches
        if (entry.request.clientId != clientId) {
            throw IllegalArgumentException(
                "client_id mismatch between PAR and authorization request (OAuth error: ${OAuthErrorCodes.INVALID_REQUEST})"
            )
        }

        // RFC 9126 §2.3: Single-use enforcement - mark as consumed
        parRepository.markConsumed(requestId)
            ?: return null // Already consumed or deleted

        // Return the full authorization parameters
        return entry.request.toAuthorizationParameters()
    }

    /**
     * Basic PAR request validation (RFC 9126 §2.1)
     *
     * Implementations may extend this with:
     * - PKCE enforcement (code_challenge validation)
     * - Client authentication (client assertion, mTLS)
     * - Authorization details validation
     */
    protected open fun validatePARRequest(request: PushedAuthorizationRequest) {
        // RFC 9126 §2.1: client_id is required (validated in model init)

        // RFC 6749 §3.1.1: redirect_uri validation (if present)
        request.redirectUri?.let { redirectUri ->
            require(redirectUri.isNotBlank()) { "redirect_uri must not be blank" }
            require(redirectUri.startsWith("http://") || redirectUri.startsWith("https://")) {
                "redirect_uri must use http or https scheme"
            }
        }

        // RFC 7636 §4.3: PKCE validation (if present)
        request.codeChallenge?.let { challenge ->
            require(challenge.isNotBlank()) { "code_challenge must not be blank" }
            val method = request.codeChallengeMethod ?: "plain"
            require(method in setOf("plain", "S256")) {
                "code_challenge_method must be 'plain' or 'S256'"
            }
        }
    }
}
