package id.walt.openid4vci.core

import id.walt.openid4vci.requests.par.PushedAuthorizationRequest
import id.walt.openid4vci.responses.par.PushedAuthorizationResponse

/**
 * PAR (Pushed Authorization Request) provider interface.
 *
 * Extends OAuth2Provider with PAR endpoint support per RFC 9126.
 * Implementations must handle:
 * - PAR ingestion (POST /par)
 * - request_uri resolution (during /authorize)
 * - PAR validation and lifecycle
 */
interface PARProvider {
    /**
     * Process a pushed authorization request (RFC 9126 §2.1)
     *
     * @param request the PAR containing all authorization parameters
     * @param clientAuthentication optional client authentication metadata (client attestation, mTLS, etc.)
     * @return PAR response with request_uri and expiry
     * @throws OAuthError if validation fails
     */
    suspend fun processPushedAuthorizationRequest(
        request: PushedAuthorizationRequest,
        clientAuthentication: Map<String, String> = emptyMap(),
    ): PushedAuthorizationResponse

    /**
     * Resolve a request_uri to its original authorization parameters (RFC 9126 §2.3)
     *
     * Called during /authorize when request_uri parameter is present.
     * Must validate:
     * - request_uri is valid and not expired
     * - request_uri has not been consumed (single-use)
     * - client_id matches the PAR
     *
     * @param requestUri the request_uri to resolve
     * @param clientId the client_id from the /authorize request
     * @return resolved authorization parameters, or null if invalid/expired/consumed
     */
    suspend fun resolveRequestUri(
        requestUri: String,
        clientId: String,
    ): Map<String, List<String>>?
}
