package id.walt.openid4vci.tokens.refresh

import kotlin.time.Instant

data class RefreshTokenClaims(
    val id: String,
    val issuer: String,
    val subject: String,
    val type: String,
    val issuedFor: String,
    val audience: Set<String>,
    val scopes: Set<String>,
    val sessionId: String?,
    val issuedAt: Instant,
    val expiresAt: Instant,
)

interface RefreshTokenVerifier {
    suspend fun verify(token: String, expectedIssuer: String?, expectedClientId: String): RefreshTokenClaims
}
