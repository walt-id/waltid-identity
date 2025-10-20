package id.walt.ktorauthnz.tokens.ktorauthnztoken

interface KtorAuthnzTokenStore {

    val name: String

    /**
     * Return session id
     */
    suspend fun mapToken(token: String, sessionId: String)

    suspend fun getTokenSessionId(token: String): String

    suspend fun validateToken(token: String): Boolean

    suspend fun dropToken(token: String)


}
