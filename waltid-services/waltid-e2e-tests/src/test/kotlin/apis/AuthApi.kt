package apis

import E2ETestWebService.test
import expectLooksLikeJwt
import expectSuccess
import id.walt.webwallet.db.models.Account
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.web.model.AccountRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.uuid.UUID
import kotlin.test.assertNotNull

class AuthApi(private val client: HttpClient) {
    suspend fun userInfo(expectedStatus: HttpStatusCode, output: ((Account) -> Unit)? = null) =
        test("/wallet-api/auth/user-info - wallet-api user-info") {
            client.get("/wallet-api/auth/user-info").apply {
                assert(status == expectedStatus) { "Expected status: $expectedStatus, but had $status" }
                output?.invoke(body<Account>())
            }
        }

    suspend fun login(request: AccountRequest, output: ((token: String) -> Unit)? = null) =
        test("/wallet-api/auth/login - wallet-api login") {
            client.post("/wallet-api/auth/login") {
                setBody(request)
            }.expectSuccess().apply {
                body<JsonObject>().let { result ->
                    assertNotNull(result["token"])
                    val token = result["token"]!!.jsonPrimitive.content.expectLooksLikeJwt()
                    output?.invoke(token)
                }
            }
        }

    suspend fun userSession() = test("/wallet-api/auth/session - logged in after login") {
        client.get("/wallet-api/auth/session").expectSuccess()
    }

    suspend fun userWallets(expectedAccountId: UUID, output: ((AccountWalletListing) -> Unit)? = null) =
        test("/wallet-api/wallet/accounts/wallets - get wallets") {
            client.get("/wallet-api/wallet/accounts/wallets").expectSuccess().apply {
                val listing = body<AccountWalletListing>()
                assert(expectedAccountId == listing.account) { "Wallet listing is for wrong account!" }
                assert(listing.wallets.isNotEmpty()) { "No wallets available!" }
                output?.invoke(listing)
            }
        }
}