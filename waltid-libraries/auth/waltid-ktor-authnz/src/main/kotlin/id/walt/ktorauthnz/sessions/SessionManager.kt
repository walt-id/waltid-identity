@file:OptIn(ExperimentalTime::class)

package id.walt.ktorauthnz.sessions

import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.utils.ExternalMappingList
import kotlinx.coroutines.flow.asFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object SessionManager {

    suspend fun newSession(authFlow: AuthFlow): AuthSession {
        val sessionId = randomUUIDString()

        val newSession = AuthSession(
            id = sessionId,
            status = AuthSessionStatus.CONTINUE_NEXT_FLOW,
            flows = setOf(authFlow),
            expiration = authFlow.expiration?.let { Clock.System.now() + authFlow.parsedDuration!! }
        )

        KtorAuthnzManager.sessionStore.storeSession(newSession)

        return newSession
    }

    suspend fun getSessionById(sessionId: String): AuthSession =
        KtorAuthnzManager.sessionStore.resolveSessionById(sessionId)

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
        KtorAuthnzManager.sessionStore.storeSession(updatedAuthSession)
    }

    suspend fun dropAllExternalMappings(authSession: AuthSession) {
        ExternalMappingList.ALL_EXTERNAL_MAPPINGS.asFlow().collect { namespace ->
            runCatching { KtorAuthnzManager.sessionStore.dropExternalIdMappingByInternal(namespace, authSession.id) }
        }
    }

    suspend fun invalidateSession(authSession: AuthSession) {
        KtorAuthnzManager.sessionStore.dropSession(authSession.id)
        dropAllExternalMappings(authSession)
    }

    suspend fun invalidateAllSessionsForAccount(accountId: String) {
        KtorAuthnzManager.sessionStore.invalidateAllSessionsForAccount(accountId)
    }

    suspend fun getSessionIdByExternalId(namespace: String, externalId: String): String? {
        return KtorAuthnzManager.sessionStore.resolveExternalIdMapping(namespace = namespace, externalId = externalId)
    }

}
