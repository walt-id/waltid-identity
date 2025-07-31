@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.test.integration.environment.api.wallet.WalletApi
import id.walt.webwallet.db.models.AccountWalletListing.WalletListing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi

@TestMethodOrder(OrderAnnotation::class)
class DidsWalletIntegrationTest : AbstractIntegrationTest() {

    companion object {

        lateinit var walletApi: WalletApi
        lateinit var wallet: WalletListing

        lateinit var defaultDidString: String

        val createdDids = mutableListOf<String>()

        @JvmStatic
        @BeforeAll
        fun loadWalletAndDefaultDid(): Unit = runBlocking {
            walletApi = getDefaultAccountWalletApi()
            wallet = walletApi.listAccountWallets().wallets.first()
            defaultDidString = walletApi.getDefaultDid(wallet.id).did
        }

        @JvmStatic
        @AfterAll
        fun deleteCreatedDidsAndResetDefaultDid(): Unit = runBlocking {
            walletApi.setDefaultDid(wallet.id, defaultDidString)
            createdDids.forEach {
                walletApi.deleteDidRaw(wallet.id, it)
            }
        }
    }

    @Order(0)
    @Test
    fun shouldCreateKeyDidWithJwkJcsPubFalse() = runTest {
        //todo: test for optional registration defaults
        val createdDidString = walletApi.createDid(wallet.id, method = "key", options = mapOf("useJwkJcsPub" to false))
        createdDids.add(createdDidString)
        val loadedDid = walletApi.getDid(wallet.id, createdDidString)
        assertEquals(createdDidString, loadedDid["id"]?.jsonPrimitive?.content)
        val verificationMethod = loadedDid["verificationMethod"]?.jsonArray?.get(0)?.jsonObject
        assertNotNull(verificationMethod?.jsonObject["publicKeyJwk"])
    }

    @Order(0)
    @Test
    fun shouldCreateJwkKey() = runTest {
        val createdDidString = walletApi.createDid(wallet.id, method = "jwk")
        createdDids.add(createdDidString)
        val loadedDid = walletApi.getDid(wallet.id, createdDidString)
        assertEquals(createdDidString, loadedDid["id"]?.jsonPrimitive?.content)
        val verificationMethod = loadedDid["verificationMethod"]?.jsonArray?.get(0)?.jsonObject
        assertNotNull(verificationMethod?.jsonObject["publicKeyJwk"])
    }

    @Order(0)
    @Test
    fun shouldCreateWebKey() = runTest {
        val createdDidString = walletApi.createDid(
            wallet.id,
            method = "web",
            options = mapOf("domain" to "domain", "path" to "path")
        )
        createdDids.add(createdDidString)
        val loadedDid = walletApi.getDid(wallet.id, createdDidString)
        assertEquals(createdDidString, loadedDid["id"]?.jsonPrimitive?.content)
        val verificationMethod = loadedDid["verificationMethod"]?.jsonArray?.get(0)?.jsonObject
        assertNotNull(verificationMethod?.jsonObject).also {
            assertNotNull(it["publicKeyJwk"]?.jsonObject)
            assertEquals("JsonWebKey2020", it["type"]?.jsonPrimitive?.content)
            assertEquals("did:web:domain:path", it["controller"]?.jsonPrimitive?.content)
        }
    }

    @Disabled("This is not working any more. It seems https://did-registrar.cheqd.net/ has changed API")
    @Test
    fun shouldCreateCheqdDid() = runTest {
        val createdDidString = walletApi.createDid(
            wallet.id,
            method = "cheqd", options = mapOf("network" to "testnet")
        )
        createdDids.add(createdDidString)
        val loadedDid = walletApi.getDid(wallet.id, createdDidString)
        assertEquals(createdDidString, loadedDid["id"]?.jsonPrimitive?.content)
        //TODO: more assertions

    }

    @Disabled("Error: DID method not supported for auto-configuration: ebsi")
    @Test
    fun shouldCreateEbsiDid() = runTest {
        val createdDidString = walletApi.createDid(
            wallet.id,
            method = "ebsi", options = mapOf("version" to 2, "bearerToken" to "token")
        )
        createdDids.add(createdDidString)
        val loadedDid = walletApi.getDid(wallet.id, createdDidString)
        assertEquals(createdDidString, loadedDid["id"]?.jsonPrimitive?.content)
        // TODO: more assertions
    }

    @Test
    fun shouldUpdateDefaultDid() = runTest {
        val createdDidString = walletApi.createDid(
            wallet.id,
            method = "web",
            options = mapOf("domain" to "test.walt.id", "path" to "/dids/a001")
        )
        createdDids.add(createdDidString)
        walletApi.setDefaultDid(wallet.id, createdDidString)
        val newDefaultDid = walletApi.getDefaultDid(wallet.id)
        assertEquals(createdDidString, newDefaultDid.did)
    }

    @Test
    fun shouldDeleteDid() = runTest {
        val createdDidString = walletApi.createDid(wallet.id, method = "jwk")
        createdDids.add(createdDidString)
        val loadedDid = walletApi.getDid(wallet.id, createdDidString)
        assertEquals(createdDidString, loadedDid["id"]?.jsonPrimitive?.content)
        walletApi.deleteDid(wallet.id, createdDidString)
        assertFalse(walletApi.listDids(wallet.id).any {
            it.did == createdDidString
        }, "Did should be deleted")
    }
}