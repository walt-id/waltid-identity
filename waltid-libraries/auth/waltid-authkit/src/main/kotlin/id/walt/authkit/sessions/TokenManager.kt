package id.walt.authkit.sessions

import id.walt.authkit.AuthKitManager
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object TokenManager {

    @OptIn(ExperimentalUuidApi::class)
    fun supplyNewToken(session: AuthSession): String {
        val newToken = Uuid.random().toString()

        AuthKitManager.tokenStore.mapToken(newToken, session.id)

        return newToken
    }

}
