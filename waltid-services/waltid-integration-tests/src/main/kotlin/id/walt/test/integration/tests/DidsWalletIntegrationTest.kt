@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.local.DidWebResolver
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
        lateinit var defaultDidString: String
        val createdDids = mutableListOf<String>()

        @JvmStatic
        @BeforeAll
        fun loadWalletAndDefaultDid(): Unit = runBlocking {
            defaultDidString = defaultWalletApi.getDefaultDid().did
        }

        @JvmStatic
        @AfterAll
        fun deleteCreatedDidsAndResetDefaultDid(): Unit = runBlocking {
            defaultWalletApi.setDefaultDid(defaultDidString)
            createdDids.forEach {
                defaultWalletApi.deleteDidRaw(it)
            }
        }
    }

    @Order(0)
    @Test
    fun shouldCreateKeyDidWithJwkJcsPubFalse() = runTest {
        //todo: test for optional registration defaults
        val createdDidString =
            defaultWalletApi.createDid(method = "key", options = mapOf("useJwkJcsPub" to false))
        createdDids.add(createdDidString)
        val loadedDid = defaultWalletApi.getDid(createdDidString)
        assertEquals(createdDidString, loadedDid["id"]?.jsonPrimitive?.content)
        val verificationMethod = loadedDid["verificationMethod"]?.jsonArray?.get(0)?.jsonObject
        assertNotNull(verificationMethod?.jsonObject["publicKeyJwk"])
    }

    @Order(0)
    @Test
    fun shouldCreateJwkKey() = runTest {
        val createdDidString = defaultWalletApi.createDid(method = "jwk")
        createdDids.add(createdDidString)
        val loadedDid = defaultWalletApi.getDid(createdDidString)
        assertEquals(createdDidString, loadedDid["id"]?.jsonPrimitive?.content)
        val verificationMethod = loadedDid["verificationMethod"]?.jsonArray?.get(0)?.jsonObject
        assertNotNull(verificationMethod?.jsonObject["publicKeyJwk"])
    }

    private enum class ColonEncoding(val value: String) {
        PLAIN(":"),
        URL("%3A"),
        PATH("/")
    }

    private fun createDomainString(encoding: ColonEncoding = ColonEncoding.PLAIN): String =
        "${environment.e2e.host}${encoding.value}${environment.e2e.port}"

    private fun createPathString(encoding: ColonEncoding = ColonEncoding.PLAIN): String =
        ":wallet-api:registry:mydid".replace(":", encoding.value)

    @Order(0)
    @Test
    fun shouldCreateWebKey() = runTest {
        val options = mapOf(
            "domain" to createDomainString(),
            "path" to createPathString(ColonEncoding.PATH)
        )
        val createdDidString = defaultWalletApi.createDid(
            method = "web",
            options = options
        )

        createdDids.add(createdDidString)
        val loadedDid = defaultWalletApi.getDid(createdDidString)
        DidWebResolver.enableHttps(false)
        val did = DidService.resolve(createdDidString).getOrNull()
        assertNotNull(did)
        assertEquals(createdDidString, loadedDid["id"]?.jsonPrimitive?.content)
        val verificationMethod = loadedDid["verificationMethod"]?.jsonArray?.get(0)?.jsonObject
        assertNotNull(verificationMethod?.jsonObject).also {
            assertNotNull(it["publicKeyJwk"]?.jsonObject)
            assertEquals("JsonWebKey2020", it["type"]?.jsonPrimitive?.content)
            val expectedController = "did:web:${createDomainString(ColonEncoding.URL)}${createPathString()}"
            assertEquals(expectedController, it["controller"]?.jsonPrimitive?.content)
        }
    }

    @Disabled("This is not working any more. It seems https://did-registrar.cheqd.net/ has changed API")
    @Test
    fun shouldCreateCheqdDid() = runTest {
        val createdDidString = defaultWalletApi.createDid(
            method = "cheqd", options = mapOf("network" to "testnet")
        )
        createdDids.add(createdDidString)
        val loadedDid = defaultWalletApi.getDid(createdDidString)
        assertEquals(createdDidString, loadedDid["id"]?.jsonPrimitive?.content)
        //TODO: more assertions

    }

    @Disabled("Error: DID method not supported for auto-configuration: ebsi")
    @Test
    fun shouldCreateEbsiDid() = runTest {
        val createdDidString = defaultWalletApi.createDid(
            method = "ebsi", options = mapOf("version" to 2, "bearerToken" to "token")
        )
        createdDids.add(createdDidString)
        val loadedDid = defaultWalletApi.getDid(createdDidString)
        assertEquals(createdDidString, loadedDid["id"]?.jsonPrimitive?.content)
        // TODO: more assertions
    }

    @Test
    fun shouldUpdateDefaultDid() = runTest {
        val createdDidString = defaultWalletApi.createDid(
            method = "web",
            options = mapOf("domain" to "test.walt.id", "path" to "/dids/a001")
        )
        createdDids.add(createdDidString)
        defaultWalletApi.setDefaultDid(createdDidString)
        val newDefaultDid = defaultWalletApi.getDefaultDid()
        assertEquals(createdDidString, newDefaultDid.did)
    }

    @Test
    fun shouldDeleteDid() = runTest {
        val createdDidString = defaultWalletApi.createDid(method = "jwk")
        createdDids.add(createdDidString)
        val loadedDid = defaultWalletApi.getDid(createdDidString)
        assertEquals(createdDidString, loadedDid["id"]?.jsonPrimitive?.content)
        defaultWalletApi.deleteDid(createdDidString)
        assertFalse(defaultWalletApi.listDids().any {
            it.did == createdDidString
        }, "Did should be deleted")
    }
}
