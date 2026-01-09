package id.walt.openid4vci

import kotlin.time.Instant

/**
 * Session is a per-request container that carries subject context and token expirations
 * between the authorize and token endpoints. The authorize handler clones the session
 * into the persisted AuthorizationCodeRecord; the token handler restores it so the access request
 * inherits subject/expiry info before minting tokens.
 */
interface Session {
    fun setExpiresAt(tokenType: TokenType, expiresAt: Instant)
    fun getExpiresAt(tokenType: TokenType): Instant?
    fun setSubject(subject: String)
    fun getSubject(): String?
    fun cloneSession(): Session
}

enum class TokenType {
    ACCESS_TOKEN,
    REFRESH_TOKEN,
    AUTHORIZATION_CODE,
}

class DefaultSession(
    private val expiresAt: MutableMap<TokenType, Instant> = mutableMapOf(),
    private var subject: String? = null,
) : Session {

    override fun setExpiresAt(tokenType: TokenType, expiresAt: Instant) {
        this.expiresAt[tokenType] = expiresAt
    }

    override fun getExpiresAt(tokenType: TokenType): Instant? = expiresAt[tokenType]

    override fun setSubject(subject: String) {
        this.subject = subject
    }

    override fun getSubject(): String? = subject

    override fun cloneSession(): Session = DefaultSession(
        expiresAt = expiresAt.toMutableMap(),
        subject = subject,
    )
}
