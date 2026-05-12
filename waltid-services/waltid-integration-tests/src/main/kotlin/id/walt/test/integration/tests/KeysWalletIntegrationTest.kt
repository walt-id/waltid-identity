@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import assertKeyComponents
import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.test.integration.tryGetData
import id.walt.webwallet.config.RegistrationDefaultsConfig
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        val keyGenRequest = KeyGenerationRequest("jwk", KeyType.Ed25519)
        var generatedKeyId: String? = null
        var signingKeyId: String? = null
    }

    @Test
    @Order(1)
    fun walletShouldContainKeyWithDefaultConfiguration() = runTest {
        val defaultKeyConfig = ConfigManager.getConfig<RegistrationDefaultsConfig>().defaultKeyConfig
        // requires registration-defaults to not be disabled in _features.conf val defaultKeyConfig = ConfigManager.getConfig<RegistrationDefaultsConfig>().defaultKeyConfig
        val keys = defaultWalletApi.listKeys()
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
        generatedKeyId = defaultWalletApi.generateKey(keyGenRequest)
        assertFalse(generatedKeyId.isNullOrEmpty())
    }

    @Test
    @Order(3)
    fun walletShouldLoadKey() = runTest {
        assertFalse(generatedKeyId.isNullOrEmpty(), "No key generated - test order ??")
        val key = defaultWalletApi.loadKey(generatedKeyId!!)
        assertKeyComponents(key, generatedKeyId!!, keyGenRequest.keyType, true)
    }

    @Test
    @Order(4)
    fun walletShouldLoadKeyMeta() = runTest {
        assertFalse(generatedKeyId.isNullOrEmpty(), "No key generated - test order ??")
        val keyMeta = defaultWalletApi.loadKeyMeta(generatedKeyId!!)
        when (keyGenRequest.backend) {
            "jwt" -> assertTrue(keyMeta.tryGetData("type")!!.jsonPrimitive.content.endsWith("JwkKeyMeta"), "Missing _type_ component!")
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
        val key = defaultWalletApi.exportKey(generatedKeyId!!, "JWK", isPrivate)
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
        defaultWalletApi.deleteKey(generatedKeyId!!)
        val response = defaultWalletApi.deleteKeyRaw(generatedKeyId!!)
        // TODO: Not found would be better code than bad request
        // TODO: Maybe consider making endpoint idempotent and return OK if key doesn't exist
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }


    @Test
    @Order(7)
    fun shouldImportJwkKey() = runTest {
        val rsaJwkImport = loadResource("keys/rsa.json")
        val importedKeyId = defaultWalletApi.importKey(rsaJwkImport)
        val importedKey = defaultWalletApi.loadKey(importedKeyId)
        assertNotNull(importedKey).also {
            assertEquals("RSA", it.jsonObject["kty"]?.jsonPrimitive?.content)
            assertEquals(importedKeyId, it.jsonObject["kid"]?.jsonPrimitive?.content)
        }
    }

    @Test
    @Order(8)
    fun walletShouldSignWithKey() = runTest {
        signingKeyId = defaultWalletApi.generateKey(KeyGenerationRequest("jwk", KeyType.Ed25519))
        assertFalse(signingKeyId.isNullOrEmpty(), "Key generation failed")
        
        val messageToSign = JsonPrimitive("Hello, World!")
        val signature = defaultWalletApi.signWithKey(signingKeyId!!, messageToSign)
        
        assertNotNull(signature, "Signature should not be null")
        assertTrue(signature.isNotEmpty(), "Signature should not be empty")
    }

    @Test
    @Order(9)
    fun walletShouldSignJsonObjectWithKey() = runTest {
        assertFalse(signingKeyId.isNullOrEmpty(), "No signing key available - test order ??")
        
        val jsonMessage = kotlinx.serialization.json.buildJsonObject {
            put("data", JsonPrimitive("test data"))
            put("timestamp", JsonPrimitive(System.currentTimeMillis()))
        }
        val signature = defaultWalletApi.signWithKey(signingKeyId!!, jsonMessage)
        
        assertNotNull(signature, "Signature should not be null")
        assertTrue(signature.isNotEmpty(), "Signature should not be empty")
    }
}
