@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.test.integration.expectSuccess
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi

class EmailAccountUserWalletIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun shouldLoginWithDefaultCredentials() = runTest {
        val api = environment.getWalletApi()
        val infoResponse = api.userInfoRaw()
        assertEquals(
            HttpStatusCode.Unauthorized, infoResponse.status,
            "Not logged in - retrieval of user info should not be possible."
        )

        val sessionResponse = api.userSessionRaw()
        assertEquals(
            HttpStatusCode.Unauthorized, sessionResponse.status,
            "Not logged in - retrieval of user session should not be possible."
        )

        val loginResponse = api.loginWithDefaultUserRaw()
        loginResponse.expectSuccess()
        val loginResponseBody = loginResponse.body<JsonObject>()
        assertNotNull(loginResponseBody).also {
            assertNotNull(it["id"]?.jsonPrimitive?.content)
            assertNotNull(it["token"]?.jsonPrimitive?.content)
            assertEquals(environment.defaultEmailAccount.email, it["username"]?.jsonPrimitive?.content)
        }
        val token = loginResponseBody["token"]!!.jsonPrimitive.content
        val authenticatedApi = api.withToken(token)
        assertNotNull(authenticatedApi.userInfo()).also {
            assertNotNull(it.id)
            assertEquals(environment.defaultEmailAccount.email, it.email)
            assertEquals(environment.defaultEmailAccount.name, it.name)
        }

        val session = authenticatedApi.userSession()
        assertNotNull(session).also {
            assertEquals(token, session["token"]?.jsonObject?.get("accessToken")?.jsonPrimitive?.content)
        }
    }

    @Test
    fun shouldListWallets() = runTest {
        val wallet = environment.getWalletApi().loginWithDefaultUser()
        val account = wallet.userInfo()
        val wallets = wallet.listAccountWallets()
        assertNotNull(wallets).also {
            assertEquals(account.id, it.account)
            assertNotNull(it.wallets).also { walletList ->
                assertFalse(walletList.isEmpty(), "Wallet list should not be empty")
                walletList.forEach { wallet ->
                    assertNotNull(wallet.id)
                    assertNotNull(wallet.name)
                    assertNotNull(wallet.createdOn)
                    assertNotNull(wallet.addedOn)
                    assertNotNull(wallet.permission)
                }
            }
        }
    }
}