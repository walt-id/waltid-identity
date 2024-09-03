package id.walt.authkit.sessions

class InMemoryTokenStore: TokenStore {

    val tokens = HashMap<String, String>()

    override fun mapToken(token: String, sessionId: String) {
        tokens[token] = sessionId
    }

    override fun dropToken(token: String) {
        tokens.remove(token)
    }

}
