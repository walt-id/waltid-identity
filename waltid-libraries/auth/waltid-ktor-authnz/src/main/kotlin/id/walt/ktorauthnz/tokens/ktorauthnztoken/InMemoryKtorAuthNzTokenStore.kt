package id.walt.ktorauthnz.tokens.ktorauthnztoken

import java.util.concurrent.ConcurrentHashMap

class InMemoryKtorAuthNzTokenStore : KtorAuthnzTokenStore {

    override val name = "in_memory"

    val tokens = ConcurrentHashMap<String, String>()

    override suspend fun mapToken(token: String, sessionId: String) {
        tokens[token] = sessionId
    }

    override suspend fun getTokenSessionId(token: String): String {
        return tokens[token] ?: error("Unknown token: $token")
    }

    override suspend fun validateToken(token: String): Boolean {
        return tokens[token] != null
    }

    override suspend fun dropToken(token: String) {
        tokens.remove(token)
    }

}


