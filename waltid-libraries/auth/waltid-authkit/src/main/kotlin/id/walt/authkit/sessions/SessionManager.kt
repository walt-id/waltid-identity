package id.walt.authkit.sessions

import id.walt.authkit.AuthKitManager
import id.walt.authkit.flows.AuthFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object SessionManager {

    suspend fun newSession(authFlow: AuthFlow): AuthSession {
        val sessionId = Uuid.random().toString()

        val newSession = AuthSession(
            id = sessionId,
            status = AuthSessionStatus.CONTINUE_NEXT_STEP,
            flows = setOf(authFlow)
        )

        AuthKitManager.sessionStore.store(newSession)

        return newSession
    }

    /**
     * Session is explicitly started
     */
    suspend fun openExplicitGlobalSession(authFlow: AuthFlow): AuthSession {
        return newSession(authFlow)
    }

    /**
     * Session is started through the first login method
     */
    suspend fun openImplicitGlobalSession(authFlow: AuthFlow): AuthSession {
        return newSession(authFlow)
    }

    //  XXXXX figure out flow based on first login method


    suspend fun updateSession(updatedAuthSession: AuthSession) {
        AuthKitManager.sessionStore.store(updatedAuthSession)
    }

    fun removeSession(authSession: AuthSession) {
        AuthKitManager.sessionStore.dropSession(authSession.id)
    }

}
