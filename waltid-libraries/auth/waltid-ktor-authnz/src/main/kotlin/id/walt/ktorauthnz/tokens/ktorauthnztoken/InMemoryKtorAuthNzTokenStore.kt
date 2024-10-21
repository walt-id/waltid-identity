package id.walt.ktorauthnz.tokens.ktorauthnztoken

class InMemoryKtorAuthNzTokenStore : KtorAuthnzTokenStore {

    val tokens = HashMap<String, String>()

    override fun mapToken(token: String, sessionId: String) {
        tokens[token] = sessionId
    }

    override fun getTokenSessionId(token: String): String {
        return tokens[token] ?: error("Unknown token: $token")
    }

    override fun validateToken(token: String): Boolean {
        return tokens[token] != null
    }

    override fun dropToken(token: String) {
        tokens.remove(token)
    }

}


