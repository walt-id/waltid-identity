package id.walt.openid4vci.tokens.access

/**
 * Minimal contract for minting access tokens.
 * Implementations can emit JWTs or opaque tokens.
 *
 * to-do: move to a "strategy"
 */
interface AccessTokenIssuer {
    suspend fun issue(claims: Map<String, Any?>): String

    /**
     * Storage handle for the issued access token.
     *
     * JWT access-token implementations should return the compact JWS signature segment.
     * Opaque token implementations may return their opaque handle or override this with a
     * storage-safe derivative.
     */
    fun signature(token: String): String = token
}
