@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment.api.wallet

import id.walt.commons.testing.E2ETest
import id.walt.webwallet.db.models.Account
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import id.walt.webwallet.web.model.X5CAccountRequest
import io.klogging.Klogging
import io.ktor.client.*
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class WalletContainerApi(
    val clientFactory: (String?) -> HttpClient,
    val e2e: E2ETest,
    val token: String? = null,
    val accountId: Uuid? = null,
) : Klogging {
    val httpClient = clientFactory(token)
    val authApi = AuthApi(e2e, httpClient)

    fun withToken(token: String, accountId: Uuid): WalletContainerApi = WalletContainerApi(
        clientFactory,
        e2e,
        token,
        accountId
    )

    suspend fun loginEmailAccountUserRaw(request: EmailAccountRequest) = authApi.loginEmailAccountUserRaw(request)

    suspend fun registerX5cUserRaw(request: X5CAccountRequest) = authApi.registerX5cUserRaw(request)
    suspend fun loginX5cUserRaw(request: X5CAccountRequest) = authApi.loginX5cUserRaw(request)

    suspend fun register(request: AccountRequest): WalletContainerApi {
        authApi.register(request)
        return this
    }

    suspend fun login(request: AccountRequest): WalletContainerApi {
        return when (request) {
            is X5CAccountRequest -> authApi.loginX5cUser(request)
            is EmailAccountRequest -> authApi.loginEmailAccountUser(request)
            else -> throw IllegalArgumentException("Unsupported request type: ${request::class.simpleName}")
        }.let { userData ->
            val token = userData["token"]!!.jsonPrimitive.content
            val accountId = Uuid.parse(userData["id"]!!.jsonPrimitive.content)
            WalletContainerApi(clientFactory, e2e, token, accountId)
        }
    }

    suspend fun userInfoRaw() = authApi.userInfoRaw()

    suspend fun userInfo(): Account = authApi.userInfo()

    suspend fun userSessionRaw() = authApi.userSessionRaw()

    suspend fun userSession() = authApi.userSession()

    suspend fun listAccountWalletsRaw() = authApi.listAccountWalletsRaw()

    suspend fun listAccountWallets() = authApi.listAccountWallets()

    fun selectWallet(walletId: Uuid): WalletApi {
        requireNotNull(token) { "Must login before wallet can be selected" }
        return WalletApi(
            walletId,
            clientFactory,
            e2e,
            token
        )
    }

    suspend fun selectDefaultWallet(): WalletApi =
        listAccountWallets().wallets.first().let { wallet ->
            selectWallet(wallet.id)
        }
}

