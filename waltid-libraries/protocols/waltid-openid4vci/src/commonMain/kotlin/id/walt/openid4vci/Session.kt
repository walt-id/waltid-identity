package id.walt.openid4vci

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Session is a per-request container that carries subject context and token expirations
 * between the authorize and token endpoints. The authorize handler clones the session
 * into the persisted AuthorizationCodeRecord; the token handler restores it so the access request
 * inherits subject/expiry info before minting tokens.
 */
@Serializable
sealed interface Session {
    @SerialName("subject")
    val subject: String?
    @SerialName("expires_at")
    val expiresAt: Map<TokenType, Instant>
    @SerialName("custom_attributes")
    val customAttributes: Map<String, String>

    fun withExpiresAt(tokenType: TokenType, instant: Instant): Session
    fun withSubject(subject: String?): Session
    fun withCustomAttribute(name: String, value: String): Session
    fun copy(): Session
}

enum class TokenType {
    ACCESS_TOKEN,
    REFRESH_TOKEN,
    AUTHORIZATION_CODE,
}

@Serializable
data class DefaultSession(
    @SerialName("expires_at")
    override val expiresAt: Map<TokenType, Instant> = emptyMap(),
    @SerialName("subject")
    override val subject: String? = null,
    @SerialName("custom_attributes")
    override val customAttributes: Map<String, String> = emptyMap(),
) : Session {
    override fun withExpiresAt(tokenType: TokenType, instant: Instant): Session =
        copy(expiresAt = expiresAt.toMutableMap().apply { this[tokenType] = instant })

    override fun withSubject(subject: String?): Session = copy(subject = subject)

    override fun withCustomAttribute(name: String, value: String): Session =
        copy(customAttributes = customAttributes.toMutableMap().apply { this[name] = value })

    override fun copy(): Session = this.copy(
        expiresAt = expiresAt.toMutableMap(),
        customAttributes = customAttributes.toMutableMap(),
    )
}
