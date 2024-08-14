package id.walt.authkit.sessions

import kotlin.uuid.Uuid

@OptIn(ExperimentalStdlibApi::class)
object SessionManager {

    fun openSession() {
        val sessionId = Uuid.random()

        val newSession = AuthSession(sessionId)

        SessionStore.store(newSession)
    }

}
