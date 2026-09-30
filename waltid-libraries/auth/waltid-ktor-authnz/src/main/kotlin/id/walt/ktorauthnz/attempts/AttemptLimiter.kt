package id.walt.ktorauthnz.attempts

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.exceptions.AuthException
import id.walt.ktorauthnz.exceptions.TooManyAttemptsException
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import io.klogging.logger
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.util.*

/**
 * Counts failed authentication attempts and refuses further ones past the [AttemptLimits].
 *
 * Methods name what an attempt is about - [attemptOnSession] once the session is known, [attemptOnIdentifier] once the
 * account identifier is known, both before checking the credential. A failure is an [AuthException] leaving the method
 * route; [AuthAttemptTracking] records it. A successful step clears the identifier's count.
 */
object AttemptLimiter {

    private val log = logger("AttemptLimiter")

    private val sessionKey = AttributeKey<String>("ktor-authnz-attempt-session")
    private val identifierKey = AttributeKey<String>("ktor-authnz-attempt-identifier")

    private val limits get() = KtorAuthnzManager.attemptLimits
    private val store get() = KtorAuthnzManager.expiringStore

    private fun sessionCounter(sessionId: String) = "attempts:session:$sessionId"
    private fun identifierCounter(identifier: String) = "attempts:identifier:$identifier"

    private suspend fun count(key: String): Long = store.get(key)?.toLongOrNull() ?: 0

    /** This call works on [sessionId]: refuses it if the session used up its attempts. */
    suspend fun ApplicationCall.attemptOnSession(sessionId: String) {
        attributes.put(sessionKey, sessionId)
        val max = limits.maxFailuresPerSession
        if (max > 0 && count(sessionCounter(sessionId)) >= max) {
            throw TooManyAttemptsException("Too many failed attempts in this authentication session; start a new one")
        }
    }

    /**
     * This call authenticates [identifier] with [method]: refuses it while the identifier is locked. The identifier is
     * compared case-insensitively, so that `Alice` and `alice` share one count.
     */
    suspend fun ApplicationCall.attemptOnIdentifier(method: String, identifier: String) {
        val key = "$method:${identifier.lowercase()}"
        attributes.put(identifierKey, key)
        val max = limits.maxFailuresPerIdentifier
        if (max > 0 && count(identifierCounter(key)) >= max) {
            throw TooManyAttemptsException("Too many failed attempts for this account; try again later")
        }
    }

    private val recordedKey = AttributeKey<Unit>("ktor-authnz-attempt-recorded")

    internal suspend fun recordFailure(call: ApplicationCall) {
        // Tracking installed on nested routes sees the same failure more than once.
        if (call.attributes.contains(recordedKey)) return
        call.attributes.put(recordedKey, Unit)
        call.attributes.getOrNull(identifierKey)?.let { key ->
            if (limits.maxFailuresPerIdentifier > 0) store.increment(identifierCounter(key), limits.identifierWindow)
        }
        call.attributes.getOrNull(sessionKey)?.let { sessionId ->
            val max = limits.maxFailuresPerSession
            if (max > 0 && store.increment(sessionCounter(sessionId), limits.sessionWindow) >= max) {
                markSessionFailed(sessionId)
            }
        }
    }

    internal suspend fun recordSuccess(call: ApplicationCall) {
        call.attributes.getOrNull(identifierKey)?.let { store.remove(identifierCounter(it)) }
    }

    private suspend fun markSessionFailed(sessionId: String) {
        runCatching {
            val sessions = KtorAuthnzManager.sessionStore
            val session = sessions.resolveSessionById(sessionId)
            session.status = AuthSessionStatus.FAILURE
            session.flows = null
            sessions.storeSession(session)
        }.onFailure { log.debug { "Could not mark session $sessionId as failed: ${it.message}" } }
    }
}

/** Records failed attempts of the authentication method routes it is installed on. */
val AuthAttemptTracking = createRouteScopedPlugin("KtorAuthnzAttemptTracking") {
    on(CallFailed) { call, cause ->
        if (cause is AuthException && cause !is TooManyAttemptsException) {
            AttemptLimiter.recordFailure(call)
        }
    }
}
