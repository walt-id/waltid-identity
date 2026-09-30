package id.walt.ktorauthnz.events

import id.walt.ktorauthnz.KtorAuthnzManager
import io.klogging.logger

/** Something that happened in authentication, for audit logs, alerts or metrics. */
sealed interface AuthnzEvent {
    /** A step of a login flow succeeded; [completed] when it was the last one and a token was issued. */
    data class LoginStepSucceeded(val sessionId: String, val method: String, val accountId: String?, val completed: Boolean) : AuthnzEvent

    /** A login step failed, e.g. a wrong password; [identifier] as entered, if the method names one. */
    data class LoginStepFailed(val sessionId: String?, val method: String?, val identifier: String?, val reason: String) : AuthnzEvent

    /** A login step was refused because the session or identifier used up its attempts. */
    data class AttemptsExceeded(val sessionId: String?, val method: String?, val identifier: String?) : AuthnzEvent

    data class LoggedOut(val sessionId: String, val accountId: String?) : AuthnzEvent

    /** All sessions of an account were ended, e.g. after a password change. */
    data class SessionsRevoked(val accountId: String) : AuthnzEvent

    /** An account enrolled or removed a method, e.g. TOTP or a passkey. */
    data class MethodEnrolled(val accountId: String, val method: String) : AuthnzEvent
    data class MethodRemoved(val accountId: String, val method: String) : AuthnzEvent

    data class PasswordChanged(val accountId: String, val method: String) : AuthnzEvent
    data class PasswordResetRequested(val method: String, val identifier: String) : AuthnzEvent

    /** A refresh token was exchanged for a new login token. */
    data class TokenRefreshed(val sessionId: String, val accountId: String?) : AuthnzEvent
}

/** Receives [AuthnzEvent]s; register with [AuthnzEvents.listen] or the `KtorAuthnz` plugin's `onEvent`. */
fun interface AuthnzEventListener {
    suspend fun onEvent(event: AuthnzEvent)
}

object AuthnzEvents {
    private val log = logger("AuthnzEvents")

    fun listen(listener: AuthnzEventListener) {
        KtorAuthnzManager.eventListeners += listener
    }

    /** Delivers [event] to every listener; a failing listener is logged and does not affect authentication. */
    suspend fun emit(event: AuthnzEvent) {
        KtorAuthnzManager.eventListeners.forEach { listener ->
            runCatching { listener.onEvent(event) }.onFailure { log.warn(it) { "Event listener failed on $event" } }
        }
    }
}
