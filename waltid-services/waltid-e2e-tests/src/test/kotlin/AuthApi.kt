import E2ETestWebService.test
import id.walt.webwallet.db.models.Account
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.KeycloakLogoutRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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

    suspend fun create(request: AccountRequest) = test("/wallet-api/auth/create - wallet-api create") {
        client.post("/wallet-api/auth/create") {
            setBody(request)
        }.expectSuccess()
    }

    suspend fun login(request: AccountRequest, output: ((JsonObject) -> Unit)? = null) =
        test("/wallet-api/auth/login - wallet-api login") {
            client.post("/wallet-api/auth/login") {
                setBody(request)
            }.expectSuccess().apply {
                body<JsonObject>().let { result ->
                    assertNotNull(result["token"])
                    result["token"]!!.jsonPrimitive.content.expectLooksLikeJwt()
                    output?.invoke(result)
                }
            }
        }

    suspend fun logout() = test("/wallet-api/auth/logout - wallet-api logout") {
        client.post("/wallet-api/auth/logout").expectSuccess()
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

    class Oidc(private val client: HttpClient) {
        suspend fun oidcToken() = test("/wallet-api/auth/oidc-token - wallet-api oidc token") {
            client.get("/wallet-api/auth/oidc-token").expectSuccess()
        }

        suspend fun oidcLogin() = test("/wallet-api/auth/oidc-login - wallet-api oidc login") {
            client.get("/wallet-api/auth/oidc-login").expectSuccess()
        }

        suspend fun oidcLogout() = test("/wallet-api/auth/logout-oidc - wallet-api oidc logout") {
            client.get("/wallet-api/auth/oidc-logout").expectSuccess()
        }
    }

    class KeyCloak(private val client: HttpClient) {
        suspend fun token(output: ((String) -> Unit)? = null) =
            test("/wallet-api/auth/keycloak/token - wallet-api keycloak token") {
                client.get("/wallet-api/auth/keycloak/token").expectSuccess().apply {
                    output?.invoke(bodyAsText())
                }
            }

        suspend fun create(request: AccountRequest) =
            test("/wallet-api/auth/keycloak/create - wallet-api keycloak create") {
                client.post("/wallet-api/auth/keycloak/create") {
                    setBody(request)
                }.expectSuccess()
            }

        suspend fun login(request: AccountRequest, output: ((JsonObject) -> Unit)? = null) =
            test("/wallet-api/auth/keycloak/login - wallet-api keycloak login") {
                client.post("/wallet-api/auth/keycloak/login") {
                    setBody(request)
                }.expectSuccess().apply {
                    body<JsonObject>().let { result ->
                        assertNotNull(result["token"])
                        result["token"]!!.jsonPrimitive.content.expectLooksLikeJwt()
                        output?.invoke(result)
                    }
                }
            }

        suspend fun logout(request: KeycloakLogoutRequest) =
            test("/wallet-api/auth/keycloak/logout - wallet-api keycloak logout") {
                client.post("/wallet-api/auth/keycloak/logout") {
                    setBody(request)
                }.expectSuccess()
            }
    }
}