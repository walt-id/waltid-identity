import AzureKeyTest.Config.payloadJWS
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.azure.AzureAuth
import id.walt.crypto.keys.azure.AzureKeyMetadata
import id.walt.crypto.keys.azure.AzureKeyRestApi
import io.ktor.utils.io.core.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class AzureKeyTest {

    private object Config {
        val clientId = ""
        val clientSecret = ""
        val tenantId = ""
        val keyVaultUrl = ""

        val payloadJWS = JsonObject(
            mapOf(
                "sub" to JsonPrimitive("16bb17e0-e733-4622-9384-122bc2fc6290"),
                "iss" to JsonPrimitive("http://localhost:3000"),
                "aud" to JsonPrimitive("TOKEN"),
            )
        )
        val payload = "Hello, World!"

        val TESTABLE_KEY_TYPES = listOf(KeyType.RSA, KeyType.secp256r1)


    }

    private fun isConfigAvailable() = runCatching {
        Config.clientId.isNotEmpty() && Config.clientSecret.isNotEmpty()
    }.fold(onSuccess = { it }, onFailure = { false })


    lateinit var keys: List<AzureKeyRestApi>


    @Test
    fun testAzure() = runTest {
        isConfigAvailable().takeIf { it }?.let {
            println("Testing key creation of: ${Config.TESTABLE_KEY_TYPES}...")
            azureTestKeyCreation()

            println("Testing resolving to public key for: $keys...")
            azureTestPublicKeys()

            println("Testing sign & verify raw (payload: ${Config.payload})...")
            azureTestSignRaw()

            println("Testing sign & verify JWS (payload: ${Config.payloadJWS})...")
            azureTestSignJws()


            println("Testing key deletion...")
            azureTestDeleteKey()
        }
    }

    private suspend fun azureTestKeyCreation() {
        val azureMetadata = AzureKeyMetadata(
            auth = AzureAuth(
                clientId = Config.clientId,
                clientSecret = Config.clientSecret,
                tenantId = Config.tenantId,
                keyVaultUrl = Config.keyVaultUrl
            )
        )

        keys = Config.TESTABLE_KEY_TYPES.map {
            AzureKeyRestApi.generate(it, azureMetadata).also { key ->
                println("Generated key: ${key.keyType} - ${key.getKeyId()}")
                assertNotNull(key, "Key generation failed for $it")
                assertNotNull(key.getKeyId(), "Key ID should not be null")
                assertTrue(key.getKeyId().isNotBlank(), "Key ID should not be blank")
                assertEquals(it, key.keyType, "Key type does not match expected value")
            }
        }
    }

    private suspend fun azureTestPublicKeys() {
        keys.forEach {
            println("Public key: ${it.getPublicKey().exportJWK()}")
        }
    }

    private suspend fun azureTestSignRaw() {
        keys.forEach { key ->
            println("RAW sign/verification test with key type: ${key.keyType}")
            val signature = key.signRaw(Config.payload.toByteArray())
            val verified = key.verifyRaw(signature, Config.payload.toByteArray())
            assertNotNull(signature, "Signature should not be null")
            assertTrue(signature.isNotEmpty(), "Signature should not be empty")
            assertTrue(verified.isSuccess, "Raw signature verification failed")
        }
    }

    private suspend fun azureTestSignJws() {
        keys.forEach { key ->
            println("JWS sign/verification test with key type: ${key.keyType}")
            val signature = key.signJws(payloadJWS.toString().toByteArray())
            val verified = key.verifyJws(signature)
            assertNotNull(signature, "JWS signature should not be null")
            assertTrue(signature.isNotEmpty(), "JWS signature should not be empty")
            assertTrue(verified.isSuccess, "JWS signature verification failed")
            assertEquals(payloadJWS, verified.getOrThrow(), "JWS payload mismatch after verification")
        }
    }

    private suspend fun azureTestDeleteKey() {
        keys.forEach { key ->
            println("Deleting key: ${key.getKeyId()}")
            key.deleteKey()
        }
    }

}