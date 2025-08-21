@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment.api.wallet

import id.walt.commons.testing.E2ETest
import id.walt.test.integration.tests.AbstractIntegrationTest.Companion.walletContainerApi
import id.walt.webwallet.db.models.Account
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import id.walt.webwallet.web.model.X5CAccountRequest
import io.ktor.client.*
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class WalletContainerApi(
    val defaultEmailAccount: EmailAccountRequest,
    val clientFactory: (String?) -> HttpClient,
    val e2e: E2ETest,
    val token: String? = null
) {
    val httpClient = clientFactory(token)
    val authApi = AuthApi(e2e, httpClient)

    fun withToken(token: String): WalletContainerApi = WalletContainerApi(
        defaultEmailAccount,
        clientFactory,
        e2e,
        token
    )

    suspend fun registerX5cUserRaw(request: X5CAccountRequest) = authApi.registerX5cUserRaw(request)
    suspend fun loginX5cUserRaw(request: X5CAccountRequest) = authApi.loginX5cUserRaw(request)

    suspend fun loginWithDefaultUserRaw() = authApi.loginEmailAccountUserRaw(defaultEmailAccount)

    suspend fun loginWithDefaultUser() = login(defaultEmailAccount)

    suspend fun login(request: AccountRequest): WalletContainerApi {
        return when (request) {
            is X5CAccountRequest -> authApi.loginX5cUser(request)
            is EmailAccountRequest -> authApi.loginEmailAccountUser(request)
            else -> throw IllegalArgumentException("Unsupported request type: ${request::class.simpleName}")
        }.let { userData ->
            val token = userData["token"]!!.jsonPrimitive.content
            WalletContainerApi(defaultEmailAccount, clientFactory, e2e, token)
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
            walletContainerApi.selectWallet(wallet.id)
        }
}
