package id.walt.openid4vci.tokens

/**
 * Minimal contract for minting access tokens.
 * Implementations can emit JWTs or opaque tokens.
 *
 * to-do: move to a "strategy"
 */
interface AccessTokenService {
    suspend fun createAccessToken(claims: Map<String, Any?>): String
}