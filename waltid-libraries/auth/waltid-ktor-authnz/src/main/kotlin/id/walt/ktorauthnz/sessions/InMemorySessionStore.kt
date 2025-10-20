package id.walt.ktorauthnz.sessions

import io.klogging.logger

class InMemorySessionStore : SessionStore {

    override val name = "in_memory"

    private val log = logger("InMemorySessionStore")
    val wip_sessions = HashMap<String, AuthSession>()

    override suspend fun resolveSessionId(sessionId: String): AuthSession = wip_sessions[sessionId] ?: error("Unknown session id: $sessionId")

    override suspend fun dropSession(id: String) {
        wip_sessions.remove(id)
    }

    override suspend fun store(session: AuthSession) {
        log.debug("saving session $session")
        wip_sessions[session.id] = session
    }
}
