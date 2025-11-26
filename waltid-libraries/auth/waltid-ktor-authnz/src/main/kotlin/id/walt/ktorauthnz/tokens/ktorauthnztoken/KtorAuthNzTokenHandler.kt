package id.walt.ktorauthnz.tokens.ktorauthnztoken

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.tokens.TokenHandler
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class KtorAuthNzTokenHandler : TokenHandler {

    override val name = "token-authnz"

    var tokenStore: KtorAuthnzTokenStore = InMemoryKtorAuthNzTokenStore()

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
        return KtorAuthnzManager.sessionStore.resolveSessionById(getTokenSessionId(token)).accountId ?: error("No account id for session")
    }

    override suspend fun dropToken(token: String) =
        tokenStore.dropToken(token)
}
