package id.walt.ktorauthnz

import id.walt.ktorauthnz.accounts.EditableAccountStore
import id.walt.ktorauthnz.attempts.AttemptLimits
import id.walt.ktorauthnz.ephemeral.ExpiringStore
import id.walt.ktorauthnz.events.AuthnzEventListener
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.methods.config.PasskeySettings
import id.walt.ktorauthnz.security.PasswordHashingConfiguration
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.sessions.SessionStore
import id.walt.ktorauthnz.tokens.RefreshTokenSettings
import id.walt.ktorauthnz.tokens.TokenHandler
import id.walt.ktorauthnz.tokens.ktorauthnztoken.KtorAuthNzTokenHandler

object KtorAuthnzManager {

    var passwordHashingConfig = PasswordHashingConfiguration()
    lateinit var accountStore: EditableAccountStore
    var sessionStore: SessionStore = InMemorySessionStore()

    var tokenHandler: TokenHandler = KtorAuthNzTokenHandler()

    /** Short-lived data: attempt counters, one-time challenges, pending enrolments, reset and refresh tokens. */
    var expiringStore: ExpiringStore = InMemoryExpiringStore()

    /** Limits on failed authentication attempts, per session and per account identifier. */
    var attemptLimits = AttemptLimits()

    /** Issue refresh tokens with each login token; off when null. */
    var refreshTokens: RefreshTokenSettings? = null

    /** The WebAuthn relying party, for passkeys; passkeys are off when null. */
    var passkeys: PasskeySettings? = null

    /** Sending of one-time email codes, for the `email-code` method; off when null. */
    var emailCodes: id.walt.ktorauthnz.methods.config.EmailCodeSettings? = null

    /** Receivers of authentication events (audit, alerts, metrics). */
    val eventListeners: MutableList<AuthnzEventListener> = java.util.concurrent.CopyOnWriteArrayList()

    internal val isAccountStoreConfigured: Boolean
        get() = ::accountStore.isInitialized
}
