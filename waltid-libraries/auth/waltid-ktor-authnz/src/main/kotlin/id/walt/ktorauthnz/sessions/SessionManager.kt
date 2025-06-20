package id.walt.ktorauthnz.sessions

import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.flows.AuthFlow
import kotlinx.datetime.Clock

object SessionManager {

    suspend fun newSession(authFlow: AuthFlow): AuthSession {
        val sessionId = randomUUIDString()

        val newSession = AuthSession(
            id = sessionId,
            status = AuthSessionStatus.CONTINUE_NEXT_STEP,
            flows = setOf(authFlow),
            expiration = authFlow.expiration?.let { Clock.System.now() + authFlow.parsedDuration!! }
        )

        KtorAuthnzManager.sessionStore.store(newSession)

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
        KtorAuthnzManager.sessionStore.store(updatedAuthSession)
    }

    fun removeSession(authSession: AuthSession) {
        KtorAuthnzManager.sessionStore.dropSession(authSession.id)
    }

}
