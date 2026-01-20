package id.walt.openid4vci

import kotlin.time.Instant

/**
 * Session is a per-request container that carries subject context and token expirations
 * between the authorize and token endpoints. The authorize handler clones the session
 * into the persisted AuthorizationCodeRecord; the token handler restores it so the access request
 * inherits subject/expiry info before minting tokens.
 */
interface Session {
    val subject: String?
    val expiresAt: Map<TokenType, Instant>

    fun withExpiresAt(tokenType: TokenType, instant: Instant): Session
    fun withSubject(subject: String?): Session
    fun copy(): Session
}

enum class TokenType {
    ACCESS_TOKEN,
    REFRESH_TOKEN,
    AUTHORIZATION_CODE,
}

data class DefaultSession(
    override val expiresAt: MutableMap<TokenType, Instant> = mutableMapOf(),
    override val subject: String? = null,
) : Session {
    override fun withExpiresAt(tokenType: TokenType, instant: Instant): Session =
        copy(expiresAt = expiresAt.toMutableMap().apply { this[tokenType] = instant })

    override fun withSubject(subject: String?): Session = copy(subject = subject)

    override fun copy(): Session = this.copy(
        expiresAt = expiresAt.toMutableMap(),
    )
}
