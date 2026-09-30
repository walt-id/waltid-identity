package id.walt.ktorauthnz

import id.walt.ktorauthnz.accounts.EditableAccountStore
import id.walt.ktorauthnz.attempts.AttemptLimits
import id.walt.ktorauthnz.ephemeral.ExpiringStore
import id.walt.ktorauthnz.events.AuthnzEventListener
import id.walt.ktorauthnz.events.AuthnzEvents
import id.walt.ktorauthnz.security.PasswordHashingConfiguration
import id.walt.ktorauthnz.sessions.SessionStore
import id.walt.ktorauthnz.sessions.SessionTokenCookieHandler
import id.walt.ktorauthnz.tokens.RefreshTokenSettings
import id.walt.ktorauthnz.tokens.TokenHandler
import io.ktor.server.application.*

/**
 * Configuration of [KtorAuthnz]. Unset values keep what [KtorAuthnzManager] has (its defaults: in-memory sessions,
 * opaque tokens, in-memory expiring store); only the account store is required.
 */
class KtorAuthnzConfig {
    /** Accounts, their identifiers and stored authentication data. Required. */
    var accountStore: EditableAccountStore? = null
    var sessionStore: SessionStore? = null
    var tokenHandler: TokenHandler? = null
    var expiringStore: ExpiringStore? = null
    var attemptLimits: AttemptLimits? = null
    var passwordHashing: PasswordHashingConfiguration? = null

    /** Issue refresh tokens with each login token; see [RefreshTokenSettings]. */
    var refreshTokens: RefreshTokenSettings? = null

    internal val cookie = CookieConfig()
    internal val listeners = mutableListOf<AuthnzEventListener>()

    /** Receives every authentication event, e.g. to write an audit log. */
    fun onEvent(listener: AuthnzEventListener) {
        listeners += listener
    }

    /** The session token cookie set on successful login and read on requests. */
    fun cookie(configure: CookieConfig.() -> Unit) = cookie.configure()

    class CookieConfig {
        var name: String? = null
        var domain: String? = null
        var secure: Boolean? = null
    }
}

/**
 * Configures ktor-authnz for the application, in one place:
 *
 * ```kotlin
 * install(KtorAuthnz) {
 *     accountStore = MyAccountStore
 *     tokenHandler = JwtTokenHandler.crypto2(signingKey, algorithm = JwsAlgorithm.ES256)
 *     cookie { domain = ".example.com" }
 * }
 * install(Authentication) { ktorAuthnz("ktor-authnz") { } }
 * ```
 *
 * Fails at startup when no account store is configured, rather than at the first login. The configuration is
 * process-wide (it is kept in [KtorAuthnzManager]), so one JVM runs one ktor-authnz configuration.
 */
val KtorAuthnz = createApplicationPlugin("KtorAuthnz", ::KtorAuthnzConfig) {
    val config = pluginConfig

    config.accountStore?.let { KtorAuthnzManager.accountStore = it }
    check(KtorAuthnzManager.isAccountStoreConfigured) {
        "ktor-authnz needs an account store: set `accountStore` when installing KtorAuthnz"
    }
    config.sessionStore?.let { KtorAuthnzManager.sessionStore = it }
    config.tokenHandler?.let { KtorAuthnzManager.tokenHandler = it }
    config.expiringStore?.let { KtorAuthnzManager.expiringStore = it }
    config.attemptLimits?.let { KtorAuthnzManager.attemptLimits = it }
    config.passwordHashing?.let { KtorAuthnzManager.passwordHashingConfig = it }
    config.refreshTokens?.let { KtorAuthnzManager.refreshTokens = it }

    config.cookie.name?.let { SessionTokenCookieHandler.cookieName = it }
    config.cookie.domain?.let { SessionTokenCookieHandler.domain = it }
    config.cookie.secure?.let { SessionTokenCookieHandler.secure = it }
    config.listeners.forEach { AuthnzEvents.listen(it) }
}
