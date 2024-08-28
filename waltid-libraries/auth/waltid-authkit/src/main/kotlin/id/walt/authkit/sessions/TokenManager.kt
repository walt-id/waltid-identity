package id.walt.authkit.sessions

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object TokenManager {

    @OptIn(ExperimentalUuidApi::class)
    fun supplyNewToken(session: AuthSession): String {
        val newToken = Uuid.random().toString()

        TokenStore.tokens[newToken] = session

        return newToken
    }

}
