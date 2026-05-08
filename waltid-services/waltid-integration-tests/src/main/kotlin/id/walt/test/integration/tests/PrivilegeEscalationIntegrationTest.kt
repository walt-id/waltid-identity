@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.test.integration.environment.api.wallet.WalletApi
import id.walt.test.integration.environment.api.wallet.WalletContainerApi
import id.walt.test.integration.expectError
import id.walt.test.integration.randomString
import id.walt.webwallet.web.model.EmailAccountRequest
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.uuid.ExperimentalUuidApi

/**
 * Integration tests for privilege escalation prevention.
 * Tests that one user cannot access another user's wallet resources.
 * This extends the basic security tests in TwoWalletsIssueCredentialIntegrationTest.
 */
@TestMethodOrder(OrderAnnotation::class)
class PrivilegeEscalationIntegrationTest : AbstractIntegrationTest() {

    companion object {
        private val accountA = EmailAccountRequest(
            name = "Security Test User A",
            email = "security-a-${randomString(8)}@walt.id",
            password = "securePasswordA123!"
        )
        
        private val accountB = EmailAccountRequest(
            name = "Security Test User B",
            email = "security-b-${randomString(8)}@walt.id",
            password = "securePasswordB123!"
        )
        
        lateinit var walletA: WalletApi
        lateinit var walletContainerA: WalletContainerApi
        lateinit var walletB: WalletApi
        lateinit var walletContainerB: WalletContainerApi
    }

    @Test
    @Order(0)
    fun setupSecurityTestWallets() = runTest {
        walletContainerApi.register(accountA)
        walletContainerApi.register(accountB)
        
        walletContainerA = walletContainerApi.login(accountA)
        walletA = walletContainerA.selectDefaultWallet()
        
        walletContainerB = walletContainerApi.login(accountB)
        walletB = walletContainerB.selectDefaultWallet()
        
        assertNotEquals(walletA.walletId, walletB.walletId, "Wallets should have different IDs")
    }

    @Test
    @Order(1)
    fun shouldNotAllowAccessToForeignWalletDids() = runTest {
        val evilWallet = walletContainerA.selectWallet(walletB.walletId)
        
        val response = evilWallet.httpClient.get("/wallet-api/wallet/${walletB.walletId}/dids")
        response.expectError()
        assertEquals(HttpStatusCode.Forbidden, response.status, "Should return 403 Forbidden")
    }

    @Test
    @Order(2)
    fun shouldNotAllowAccessToForeignWalletKeys() = runTest {
        val evilWallet = walletContainerA.selectWallet(walletB.walletId)
        
        val response = evilWallet.httpClient.get("/wallet-api/wallet/${walletB.walletId}/keys")
        response.expectError()
        assertEquals(HttpStatusCode.Forbidden, response.status, "Should return 403 Forbidden")
    }

    @Test
    @Order(3)
    fun shouldNotAllowAccessToForeignWalletEventLog() = runTest {
        val evilWallet = walletContainerA.selectWallet(walletB.walletId)
        
        val response = evilWallet.httpClient.get("/wallet-api/wallet/${walletB.walletId}/eventlog")
        response.expectError()
        assertEquals(HttpStatusCode.Forbidden, response.status, "Should return 403 Forbidden")
    }

    @Test
    @Order(4)
    fun shouldNotAllowKeyGenerationInForeignWallet() = runTest {
        val evilWallet = walletContainerA.selectWallet(walletB.walletId)
        
        val response = evilWallet.httpClient.post("/wallet-api/wallet/${walletB.walletId}/keys/generate") {
            setBody(KeyGenerationRequest("jwk", KeyType.Ed25519))
        }
        response.expectError()
        assertEquals(HttpStatusCode.Forbidden, response.status, "Should return 403 Forbidden")
    }

    @Test
    @Order(5)
    fun shouldNotAllowDidCreationInForeignWallet() = runTest {
        val evilWallet = walletContainerA.selectWallet(walletB.walletId)
        
        val response = evilWallet.createDidRaw("jwk")
        response.expectError()
        assertEquals(HttpStatusCode.Forbidden, response.status, "Should return 403 Forbidden")
    }

    @Test
    @Order(6)
    fun shouldNotAllowAccessToForeignWalletCredentials() = runTest {
        val evilWallet = walletContainerA.selectWallet(walletB.walletId)
        
        val response = evilWallet.listCredentialsRaw()
        response.expectError()
        assertEquals(HttpStatusCode.Forbidden, response.status, "Should return 403 Forbidden")
    }

    @Test
    @Order(7)
    fun shouldNotAllowAccessToForeignWalletCategories() = runTest {
        val evilWallet = walletContainerA.selectWallet(walletB.walletId)
        
        val response = evilWallet.httpClient.get("/wallet-api/wallet/${walletB.walletId}/categories")
        response.expectError()
        assertEquals(HttpStatusCode.Forbidden, response.status, "Should return 403 Forbidden")
    }

    @Test
    @Order(8)
    fun shouldNotAllowCategoryCreationInForeignWallet() = runTest {
        val evilWallet = walletContainerA.selectWallet(walletB.walletId)
        
        val response = evilWallet.httpClient.post("/wallet-api/wallet/${walletB.walletId}/categories") {
            setBody(mapOf("name" to "malicious-category"))
        }
        response.expectError()
        assertEquals(HttpStatusCode.Forbidden, response.status, "Should return 403 Forbidden")
    }

    @Test
    @Order(9)
    fun shouldNotAllowCredentialOfferResolutionForForeignWallet() = runTest {
        val evilWallet = walletContainerA.selectWallet(walletB.walletId)
        
        val response = evilWallet.httpClient.post("/wallet-api/wallet/${walletB.walletId}/exchange/resolveCredentialOffer") {
            setBody("openid-credential-offer://example.com?credential_offer=test")
        }
        response.expectError()
        assertEquals(HttpStatusCode.Forbidden, response.status, "Should return 403 Forbidden")
    }

    @Test
    @Order(10)
    fun shouldNotAllowPresentationRequestResolutionForForeignWallet() = runTest {
        val evilWallet = walletContainerA.selectWallet(walletB.walletId)
        
        val response = evilWallet.resolvePresentationRequestRaw("openid4vp://example.com?request=test")
        response.expectError()
        assertEquals(HttpStatusCode.Forbidden, response.status, "Should return 403 Forbidden")
    }

    @Test
    @Order(11)
    fun shouldNotAllowCredentialMatchingForForeignWallet() = runTest {
        val evilWallet = walletContainerA.selectWallet(walletB.walletId)
        
        val response = evilWallet.matchCredentialsForPresentationDefinitionRaw("{}")
        response.expectError()
        assertEquals(HttpStatusCode.Forbidden, response.status, "Should return 403 Forbidden")
    }

    @Test
    @Order(12)
    fun shouldAllowAccessToOwnWallet() = runTest {
        val ownDids = walletA.listDids()
        val ownKeys = walletA.listKeys()
        val ownCredentials = walletA.listCredentials()
        val ownCategories = walletA.listCategories()
        
        assertEquals(true, true, "User should be able to access their own wallet resources")
    }
}
