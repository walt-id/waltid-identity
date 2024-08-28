package id.walt.authkit.sessions

import io.klogging.logger

object SessionStore {

    val log = logger("SessionStore")
    val wip_sessions = HashMap<String, AuthSession>()

    fun resolveSessionId(sessionId: String): AuthSession = wip_sessions[sessionId] ?: error("Unknown session id: $sessionId")

    suspend fun store(session: AuthSession) {
        log.debug("saving session $session")
        wip_sessions[session.id] = session
    }
}
