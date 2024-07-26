import E2ETestWebService.test
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

    suspend fun userInfo(expectedStatus: HttpStatusCode): Account? =
        client.get("/wallet-api/auth/user-info").run {
            assert(status == expectedStatus) { "Expected status: $expectedStatus, but had $status" }
            if (status.isSuccess()) runCatching { body<Account>() }.getOrNull() else null
        }

    suspend fun create(request: AccountRequest) = test(name = "/wallet-api/auth/create - wallet-api create") {
        client.post(urlString = "/wallet-api/auth/create") {
            setBody(
                body = request
            )
        }.expectSuccess()
    }

    suspend fun login(request: AccountRequest) = client.post("/wallet-api/auth/login") {
        setBody(body = request)
    }.expectSuccess().run {
        body<JsonObject>().let { result ->
            val token = result["token"]
            assertNotNull(token)
            val tokenContent = result["token"]!!.jsonPrimitive.content
            tokenContent.expectLooksLikeJwt()
            tokenContent
        }
    }

    suspend fun logout() =
        client.post("/wallet-api/auth/logout").expectSuccess()

    suspend fun userSession() =
        client.get("/wallet-api/auth/session").expectSuccess()

    suspend fun userWallets(expectedAccountId: UUID) =
        client.get("/wallet-api/wallet/accounts/wallets").expectSuccess().run {
            val listing = body<AccountWalletListing>()
            assert(expectedAccountId == listing.account) { "Wallet listing is for wrong account!" }
            assert(listing.wallets.isNotEmpty()) { "No wallets available!" }
            listing.wallets
        }

    /*class Oidc(private val client: HttpClient) {
        suspend fun oidcToken() = client.get("/wallet-api/auth/oidc-token").expectSuccess()

        suspend fun oidcLogin() = client.get("/wallet-api/auth/oidc-login").expectSuccess()

        suspend fun oidcLogout() = client.get("/wallet-api/auth/oidc-logout").expectSuccess()
    }*/

    /*class Keycloak(private val client: HttpClient) {
        suspend fun token(output: ((String) -> Unit)? = null) =
                client.get("/wallet-api/auth/keycloak/token").expectSuccess().apply {
                    output?.invoke(bodyAsText())
                }

        suspend fun create(request: AccountRequest) = create(
            client = client,
            name = "/wallet-api/auth/keycloak/create - wallet-api keycloak create",
            url = "/wallet-api/auth/keycloak/create",
            request = request
        )

        suspend fun login(request: AccountRequest) = login(
            client = client,
            name = "/wallet-api/auth/keycloak/login - wallet-api keycloak login",
            url = "/wallet-api/auth/keycloak/login",
            request = request,
        )

        suspend fun logout(request: KeycloakLogoutRequest) = client.post("/wallet-api/auth/keycloak/logout") {
                    setBody(request)
             }.expectSuccess()
    }*/
}
