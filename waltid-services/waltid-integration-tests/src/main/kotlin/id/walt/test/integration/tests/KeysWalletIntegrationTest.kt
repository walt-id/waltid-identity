@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.test.integration.assertKeyComponents
import id.walt.test.integration.environment.api.wallet.WalletApi
import id.walt.test.integration.tryGetData
import id.walt.webwallet.config.RegistrationDefaultsConfig
import id.walt.webwallet.db.models.AccountWalletListing.WalletListing
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

@TestMethodOrder(OrderAnnotation::class)
class KeysWalletIntegrationTest : AbstractIntegrationTest() {

    companion object {

        lateinit var walletApi: WalletApi
        lateinit var wallet: WalletListing

        val keyGenRequest = KeyGenerationRequest("jwk", KeyType.Ed25519)
        var generatedKeyId: String? = null

        @JvmStatic
        @BeforeAll
        fun loadWallet(): Unit = runBlocking {
            walletApi = getDefaultAccountWalletApi()
            wallet = walletApi.listAccountWallets().wallets.first()
        }
    }

    @Test
    @Order(1)
    fun walletShouldContainKeyWithDefaultConfiguration() = runTest {
        val defaultKeyConfig = ConfigManager.getConfig<RegistrationDefaultsConfig>().defaultKeyConfig
        // requires registration-defaults to not be disabled in _features.conf val defaultKeyConfig = ConfigManager.getConfig<RegistrationDefaultsConfig>().defaultKeyConfig
        val keys = walletApi.listKeys(wallet.id)
        assertNotNull(keys).also {
            assertTrue(
                it.any { key -> KeyType.valueOf(key.algorithm) == defaultKeyConfig.keyType },
                "Default key type not ${defaultKeyConfig.keyType}"
            )
        }
    }

    @Test
    @Order(2)
    fun walletShouldGenerateKey() = runTest {
        generatedKeyId = walletApi.generateKey(wallet.id, keyGenRequest)
        assertFalse(generatedKeyId.isNullOrEmpty())
    }

    @Test
    @Order(3)
    fun walletShouldLoadKey() = runTest {
        assertFalse(generatedKeyId.isNullOrEmpty(), "No key generated - test order ??")
        val key = walletApi.loadKey(wallet.id, generatedKeyId!!)
        assertKeyComponents(key, generatedKeyId!!, keyGenRequest.keyType, true)
    }

    @Test
    @Order(4)
    fun walletShouldLoadKeyMeta() = runTest {
        assertFalse(generatedKeyId.isNullOrEmpty(), "No key generated - test order ??")
        val keyMeta = walletApi.loadKeyMeta(wallet.id, generatedKeyId!!)
        when (keyGenRequest.backend) {
            "jwt" -> assert(keyMeta.tryGetData("type")!!.jsonPrimitive.content.endsWith("JwkKeyMeta")) { "Missing _type_ component!" }
            "tse" -> TODO()
            "oci" -> TODO()
            "oci-rest-api" -> TODO()
            else -> Unit
        }

    }

    @Test
    @Order(5)
    fun walletShouldExportKeyJwk() = runTest {
        val isPrivate = true
        assertFalse(generatedKeyId.isNullOrEmpty(), "No key generated - test order ??")
        val key = walletApi.exportKey(wallet.id, generatedKeyId!!, "JWK", isPrivate)
        assertKeyComponents(
            key,
            generatedKeyId!!,
            keyGenRequest.keyType,
            isPrivate
        )
    }

    @Test
    @Order(6)
    fun walletShouldDeleteKey() = runTest {
        assertFalse(generatedKeyId.isNullOrEmpty(), "No key generated - test order ??")
        walletApi.deleteKey(wallet.id, generatedKeyId!!)
        val response = walletApi.deleteKeyRaw(wallet.id, generatedKeyId!!)
        // TODO: Not found would be better code than bad request
        // TODO: Maybe consider making endpoint idempotent and return OK if key doesn't exist
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }


    @Test
    @Order(7)
    fun shouldImportJwkKey() = runTest {
        val rsaJwkImport = loadResource("keys/rsa.json")
        val importedKeyId = walletApi.importKey(wallet.id, rsaJwkImport)
        val importedKey = walletApi.loadKey(wallet.id, importedKeyId)
        assertNotNull(importedKey).also {
            assertEquals("RSA", it.jsonObject["kty"]?.jsonPrimitive?.content)
            assertEquals(importedKeyId, it.jsonObject["kid"]?.jsonPrimitive?.content)
        }
    }
}