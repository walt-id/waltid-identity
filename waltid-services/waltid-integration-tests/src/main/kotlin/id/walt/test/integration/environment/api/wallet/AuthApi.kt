package id.walt.test.integration.environment.api.wallet

import id.walt.commons.testing.E2ETest
import id.walt.test.integration.expectLooksLikeJwt
import id.walt.test.integration.expectSuccess
import id.walt.webwallet.db.models.Account
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.KeycloakLogoutRequest
import id.walt.webwallet.web.model.X5CAccountRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AuthApi(
    private val e2e: E2ETest,
    private val client: HttpClient
) {

    suspend fun registerX5cUserRaw(request: X5CAccountRequest): HttpResponse =
        client.post("/wallet-api/auth/x5c/register") {
            setBody<AccountRequest>(request)
        }

    suspend fun loginX5cUserRaw(request: X5CAccountRequest): HttpResponse =
        client.post("/wallet-api/auth/x5c/login") {
            setBody<AccountRequest>(request)
        }

    suspend fun loginX5cUser(request: X5CAccountRequest): JsonObject =
        checkSuccessfulLogin(loginX5cUserRaw(request))

    suspend fun loginEmailAccountUserRaw(
        request: AccountRequest,
    ): HttpResponse =
        client.post("/wallet-api/auth/login") {
            setBody(request)
        }

    suspend fun loginEmailAccountUser(request: AccountRequest): JsonObject =
        checkSuccessfulLogin(loginEmailAccountUserRaw(request))

    suspend fun userInfoRaw(): HttpResponse {
        return client.get("/wallet-api/auth/user-info")
    }

    suspend fun userInfo(): Account {
        val response = client.get("/wallet-api/auth/user-info")
        response.expectSuccess()
        return response.body<Account>()
    }


    suspend fun userSessionRaw(): HttpResponse {
        return client.get("/wallet-api/auth/session")
    }

    suspend fun userSession() = userSessionRaw().let {
        it.expectSuccess()
        it.body<JsonObject>()
    }

    suspend fun listAccountWalletsRaw(): HttpResponse {
        return client.get("/wallet-api/wallet/accounts/wallets")
    }


    suspend fun listAccountWallets() = listAccountWalletsRaw().let {
        it.expectSuccess()
        val listing = it.body<AccountWalletListing>()
        assertFalse(listing.wallets.isEmpty(), "No wallets available!")
        listing
    }

    @Deprecated(
        message = "This API is for old integration tests",
        replaceWith = ReplaceWith("userInfoRaw()")
    )
    suspend fun userInfo(expectedStatus: HttpStatusCode, output: ((Account) -> Unit)? = null) =
        e2e.test("/wallet-api/auth/user-info - wallet-api user-info") {
            client.get("/wallet-api/auth/user-info").apply {
                assert(status == expectedStatus) { "Expected status: $expectedStatus, but had $status" }
                output?.invoke(body<Account>())
            }
        }

    suspend fun register(request: AccountRequest) = baseRegister(
        e2e = e2e,
        client = client,
        name = "/wallet-api/auth/register - wallet-api register",
        url = "/wallet-api/auth/register",
        request = request
    )

    suspend fun logout() = e2e.test("/wallet-api/auth/logout - wallet-api logout") {
        client.post("/wallet-api/auth/logout").expectSuccess()
    }

    @Deprecated(
        message = "This API is for old integration tests",
        replaceWith = ReplaceWith("listAccountWalletsRaw()")
    )
    @OptIn(ExperimentalUuidApi::class)
    suspend fun userWallets(expectedAccountId: Uuid, output: ((AccountWalletListing) -> Unit)? = null) =
        e2e.test("/wallet-api/wallet/accounts/wallets - get wallets") {
            client.get("/wallet-api/wallet/accounts/wallets").expectSuccess().apply {
                val listing = body<AccountWalletListing>()
                assert(expectedAccountId == listing.account) { "Wallet listing is for wrong account!" }
                assert(listing.wallets.isNotEmpty()) { "No wallets available!" }
                output?.invoke(listing)
            }
        }

    class Oidc(private val e2e: E2ETest, private val client: HttpClient) {
        suspend fun oidcToken() = e2e.test("/wallet-api/auth/oidc-token - wallet-api oidc token") {
            client.get("/wallet-api/auth/oidc-token").expectSuccess()
        }

        suspend fun oidcLogin() = e2e.test("/wallet-api/auth/oidc-login - wallet-api oidc login") {
            client.get("/wallet-api/auth/oidc-login").expectSuccess()
        }

        suspend fun oidcLogout() = e2e.test("/wallet-api/auth/logout-oidc - wallet-api oidc logout") {
            client.get("/wallet-api/auth/oidc-logout").expectSuccess()
        }
    }

    class Keycloak(private val e2e: E2ETest, private val client: HttpClient) {
        suspend fun token(output: ((String) -> Unit)? = null) =
            e2e.test("/wallet-api/auth/keycloak/token - wallet-api keycloak token") {
                client.get("/wallet-api/auth/keycloak/token").expectSuccess().apply {
                    output?.invoke(bodyAsText())
                }
            }

        suspend fun create(request: AccountRequest) = baseRegister(
            e2e = e2e,
            client = client,
            name = "/wallet-api/auth/keycloak/create - wallet-api keycloak create",
            url = "/wallet-api/auth/keycloak/create",
            request = request
        )

        suspend fun login(request: AccountRequest) = baseLogin(
            client = client,
            url = "/wallet-api/auth/keycloak/login",
            request = request
            //name = "/wallet-api/auth/keycloak/login - wallet-api keycloak login",
        )

        suspend fun logout(request: KeycloakLogoutRequest) =
            e2e.test("/wallet-api/auth/keycloak/logout - wallet-api keycloak logout") {
                client.post("/wallet-api/auth/keycloak/logout") {
                    setBody(request)
                }.expectSuccess()
            }
    }

    companion object {
        suspend fun baseRegister(
            e2e: E2ETest,
            client: HttpClient,
            name: String,
            url: String,
            request: AccountRequest,
        ) = e2e.test(name) {
            client.post(url) {
                setBody(request)
            }.expectSuccess()
        }

        suspend fun baseLogin(
            client: HttpClient,
            url: String,
            request: AccountRequest,
        ): JsonObject =
            client.post(url) {
                setBody(request)
            }.expectSuccess()
                .body<JsonObject>().let { result ->
                    assertNotNull(result["token"])
                    result["token"]!!.jsonPrimitive.content.expectLooksLikeJwt()
                    result
                }
    }
}

private suspend fun checkSuccessfulLogin(response: HttpResponse): JsonObject {
    response.expectSuccess()
    return response.body<JsonObject>()
        .also {
            assertNotNull(it["token"]?.jsonPrimitive?.content)
                .also { token ->
                    token.expectLooksLikeJwt()
                }
        }

}

