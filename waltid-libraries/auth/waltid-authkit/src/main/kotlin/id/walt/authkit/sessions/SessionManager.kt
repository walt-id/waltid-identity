package id.walt.authkit.sessions

import id.walt.authkit.flows.AuthFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object SessionManager {

    // flow is explicitly provided when starting session
    suspend fun openExplicitSession(authFlow: AuthFlow): AuthSession {
        val sessionId = Uuid.random().toString()

        val newSession = AuthSession(
            id = sessionId,
            status = AuthSessionStatus.CONTINUE_NEXT_STEP,
            flow = authFlow
        )

        SessionStore.store(newSession)

        return newSession
    }

    // figure out flow based on first login method
    suspend fun openImplicitSession(): AuthSession {
        val sessionId = Uuid.random().toString()

        val newSession = AuthSession(sessionId)

        SessionStore.store(newSession)

        return newSession
    }

    suspend fun updateSession(updatedAuthSession: AuthSession) {
        SessionStore.store(updatedAuthSession)
    }

}
