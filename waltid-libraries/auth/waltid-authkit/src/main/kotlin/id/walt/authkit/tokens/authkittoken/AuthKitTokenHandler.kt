package id.walt.authkit.tokens.authkittoken

import id.walt.authkit.AuthKitManager
import id.walt.authkit.sessions.AuthSession
import id.walt.authkit.tokens.TokenHandler
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AuthKitTokenHandler : TokenHandler {

    var tokenStore = InMemoryAuthKitTokenStore()

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun generateToken(session: AuthSession): String {
        val newToken = Uuid.random().toString()

        tokenStore.mapToken(newToken, session.id)

        return newToken
    }

    override suspend fun validateToken(token: String): Boolean =
        tokenStore.validateToken(token)

    override suspend fun getTokenSessionId(token: String): String =
        tokenStore.getTokenSessionId(token)

    override suspend fun getTokenAccountId(token: String): String {
        return AuthKitManager.sessionStore.resolveSessionId(getTokenSessionId(token)).accountId ?: error("No account id for session")
    }

    override suspend fun dropToken(token: String) =
        tokenStore.dropToken(token)
}
