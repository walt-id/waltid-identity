@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment.api.wallet

import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.webwallet.db.models.Account
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import id.walt.webwallet.web.model.X5CAccountRequest
import io.ktor.client.*
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class WalletApi(
    val defaultEmailAccount: EmailAccountRequest,
    val clientFactory: (String?) -> HttpClient,
    val e2e: E2ETest,
    token: String? = null
) {
    val httpClient = clientFactory(token)
    val authApi = AuthApi(e2e, httpClient)
    val keysApi = KeysApi(e2e, httpClient)

    fun withToken(token: String): WalletApi = WalletApi(defaultEmailAccount, clientFactory, e2e, token)

    suspend fun registerX5cUserRaw(request: X5CAccountRequest) = authApi.registerX5cUserRaw(request)
    suspend fun loginX5cUserRaw(request: X5CAccountRequest) = authApi.loginX5cUserRaw(request)

    suspend fun loginWithDefaultUserRaw() = authApi.loginEmailAccountUserRaw(defaultEmailAccount)

    suspend fun loginWithDefaultUser() = login(defaultEmailAccount)

    suspend fun login(request: AccountRequest): WalletApi {
        return when (request) {
            is X5CAccountRequest -> authApi.loginX5cUser(request)
            is EmailAccountRequest -> authApi.loginEmailAccountUser(request)
            else -> throw IllegalArgumentException("Unsupported request type: ${request::class.simpleName}")
        }.let { userData ->
            val token = userData["token"]!!.jsonPrimitive.content
            WalletApi(defaultEmailAccount, clientFactory, e2e, token)
        }
    }

    suspend fun userInfoRaw() = authApi.userInfoRaw()

    suspend fun userInfo(): Account = authApi.userInfo()

    suspend fun userSessionRaw() = authApi.userSessionRaw()

    suspend fun userSession() = authApi.userSession()

    suspend fun listAccountWalletsRaw() = authApi.listAccountWalletsRaw()

    suspend fun listAccountWallets() = authApi.listAccountWallets()

    //=========================================================================
    // Keys API
    //=========================================================================
    suspend fun listKeys(walletId: Uuid) = keysApi.list(walletId)
    suspend fun generateKey(walletId: Uuid, request: KeyGenerationRequest) = keysApi.generate(walletId, request)
    suspend fun loadKey(walletId: Uuid, keyId: String) = keysApi.load(walletId, keyId)
    suspend fun loadKeyMeta(walletId: Uuid, keyId: String) = keysApi.loadMeta(walletId, keyId)
    suspend fun exportKey(walletId: Uuid, keyId: String, format: String, isPrivate: Boolean) =
        keysApi.exportKey(walletId, keyId, format, isPrivate)

    suspend fun deleteKeyRaw(walletId: Uuid, keyId: String) = keysApi.deleteKeyRaw(walletId, keyId)
    suspend fun deleteKey(walletId: Uuid, keyId: String) = keysApi.deleteKey(walletId, keyId)
    suspend fun importKey(walletId: Uuid, keyId: String): String = keysApi.importKey(walletId, keyId)
}