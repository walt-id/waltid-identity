import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.azure.AzureKey
import id.walt.crypto.keys.azure.AzureKeyMetadataSDK
import id.walt.crypto.keys.azure.AzureSDKAuth
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AzureSDKKeyTest {

    private object Config {
        val payloadJWS = JsonObject(
            mapOf(
                "sub" to JsonPrimitive("16bb17e0-e733-4622-9384-122bc2fc6290"),
                "iss" to JsonPrimitive("http://localhost:3000"),
                "aud" to JsonPrimitive("TOKEN")
            )
        )

        val payload = "Hello, Azure Key Vault!"

        val TESTABLE_KEY_TYPES = listOf(KeyType.RSA, KeyType.secp256r1)

        val vaultUrl = "https://your-key-vault-name.vault.azure.net/"
    }

    private lateinit var keys: List<AzureKey>

    @Test
    fun testAzure() = runTest {
        azureTestKeyCreation()
        azureTestPublicKeys()
        azureTestSignRaw()
        azureTestSignJws()
        azureTestDeleteKey()
    }

    private suspend fun azureTestKeyCreation() {
        keys = Config.TESTABLE_KEY_TYPES.map {
            AzureKey.generateKey(
                it,
                AzureKeyMetadataSDK(
                    auth = AzureSDKAuth(
                        keyVaultUrl = Config.vaultUrl
                    )
                )
            ).also {
                println("Generated key: ${it.keyType} - ${it.id}")
                assertNotNull(it, "Key generation failed for $it")
                assertNotNull(it.id, "Key ID should not be null")
                assertTrue(it.id.isNotBlank(), "Key ID should not be blank")
            }
        }
        println("Generated ${keys.size} keys")
        assertTrue(keys.isNotEmpty(), "No keys were created")
    }

    private suspend fun azureTestPublicKeys() {
        keys.forEach { key ->
            val publicKey = key.getPublicKey()
            println("Public key for ${key.keyType}: ${publicKey.exportJWK()}")
            assertNotNull(publicKey, "Public key should not be null")
        }
    }

    private suspend fun azureTestSignRaw() {
        keys.forEach { key ->
            println("RAW sign/verification test with key type: ${key.keyType}")
            val data = Config.payload.toByteArray()
            val signature = key.signRaw(data)
            val verified = key.verifyRaw(signature, data)
            assertNotNull(signature, "Signature should not be null")
            assertTrue(signature.isNotEmpty(), "Signature should not be empty")
            assertTrue(verified.isSuccess, "Raw signature verification failed: ${verified.exceptionOrNull()?.message}")
        }
    }

    private suspend fun azureTestSignJws() {
        keys.forEach { key ->
            println("JWS sign/verification test with key type: ${key.keyType}")
            val data = Config.payloadJWS.toString().toByteArray()
            val jws = key.signJws(data)
            val verified = key.verifyJws(jws)

            assertNotNull(jws, "JWS signature should not be null")
            assertTrue(jws.isNotEmpty(), "JWS signature should not be empty")
            assertTrue(verified.isSuccess, "JWS signature verification failed: ${verified.exceptionOrNull()?.message}")
            assertEquals(Config.payloadJWS, verified.getOrThrow(), "JWS payload mismatch after verification")
        }
    }

    private suspend fun azureTestDeleteKey() {
        keys.forEach { key ->
            println("Deleting key: ${key.id}")
            val deleted = key.deleteKey()
            println("Delete result: $deleted")
            assertTrue(deleted, "Key deletion failed")
        }
    }
}