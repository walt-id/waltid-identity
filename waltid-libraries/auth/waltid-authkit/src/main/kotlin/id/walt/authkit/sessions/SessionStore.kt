package id.walt.authkit.sessions

import kotlin.uuid.Uuid

@OptIn(ExperimentalStdlibApi::class)
object SessionStore {

    val log = logger("SessionStore")
    val wip_sessions = HashMap<String, AuthSession>()

    fun store(session: AuthSession) {
        wip_sessions[session.id] = session
    }
}
