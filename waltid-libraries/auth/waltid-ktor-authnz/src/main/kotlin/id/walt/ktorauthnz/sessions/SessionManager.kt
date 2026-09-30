package id.walt.ktorauthnz.sessions

import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.exceptions.AuthSessionNotFoundException
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.utils.ExternalMappingList
import kotlinx.coroutines.flow.asFlow
import kotlin.time.Clock


object SessionManager {

    /**
     * Opens a session for [authFlow]. With [persist] false it is only stored once something is written to it (a step
     * succeeds, or a multi-step method records its state) - so failed first attempts leave nothing behind.
     */
    suspend fun newSession(authFlow: AuthFlow, persist: Boolean = true, tenant: String? = null): AuthSession {
        val now = Clock.System.now()
        val newSession = AuthSession(
            id = randomUUIDString(),
            status = AuthSessionStatus.CONTINUE_NEXT_FLOW,
            flows = setOf(authFlow),
            expiration = authFlow.parsedDuration?.let { now + it },
            createdAt = now,
            tenant = tenant,
        )

        if (persist) KtorAuthnzManager.sessionStore.storeSession(newSession)

        return newSession
    }

    /** The stored session, or [AuthSessionNotFoundException] if there is none (never created, expired, logged out). */
    suspend fun getSessionById(sessionId: String): AuthSession =
        KtorAuthnzManager.sessionStore.findSessionById(sessionId) ?: throw AuthSessionNotFoundException(sessionId)

    /** Session is explicitly started, e.g. by a `start` route, before its first step. */
    suspend fun openExplicitGlobalSession(authFlow: AuthFlow, tenant: String? = null): AuthSession =
        newSession(authFlow, persist = true, tenant = tenant)

    /** Session is started through the first login method; stored only once that succeeds. */
    suspend fun openImplicitGlobalSession(authFlow: AuthFlow, tenant: String? = null): AuthSession =
        newSession(authFlow, persist = false, tenant = tenant)


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
