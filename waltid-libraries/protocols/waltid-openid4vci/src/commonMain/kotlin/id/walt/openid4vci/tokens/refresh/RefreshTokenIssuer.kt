package id.walt.openid4vci.tokens.refresh

import kotlin.time.Clock
import kotlin.time.Instant

data class RefreshTokenGenerationRequest(
    val issuer: String,
    val subject: String,
    val clientId: String,
    val scopes: Set<String>,
    val expiresAt: Instant,
    val sessionId: String?,
    val issuedAt: Instant = Clock.System.now(),
)

interface RefreshTokenIssuer {
    suspend fun issue(request: RefreshTokenGenerationRequest): String
    fun signature(token: String): String
}