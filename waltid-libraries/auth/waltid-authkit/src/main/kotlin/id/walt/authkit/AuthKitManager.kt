package id.walt.authkit

import id.walt.authkit.sessions.InMemorySessionStore
import id.walt.authkit.sessions.InMemoryTokenStore

object AuthKitManager {

    val sessionStore = InMemorySessionStore()
    val tokenStore = InMemoryTokenStore()

}
