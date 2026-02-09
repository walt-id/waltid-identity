import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.aws.AWSKey
import id.walt.crypto.keys.aws.AWSKeyMetadataSDK
import id.walt.crypto.keys.aws.AwsSDKAuth
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AwsSDKKeyTest {

    private object Config {
        val payloadJWS = JsonObject(
            mapOf(
                "sub" to JsonPrimitive("16bb17e0-e733-4622-9384-122bc2fc6290"),
                "iss" to JsonPrimitive("http://localhost:3000"),
                "aud" to JsonPrimitive("TOKEN")
            )
        )
        // 4352 chars > 4096 bytes of maximum payload size for RSA keys in AWS KMS
        val payload = buildString { repeat(256) { append("Hello, World!!!!!") } }
        val TESTABLE_KEY_TYPES = listOf(KeyType.RSA, KeyType.secp256r1, KeyType.secp256k1)
    }

    private lateinit var keys: List<AWSKey>

    @Test
    fun testAws() = runTest {
        awsTestKeyCreation()
        awsTestPublicKeys()
        awsTestSignRaw()
        awsTestSignJws()
        awsTestDeleteKey()
    }

    private suspend fun awsTestKeyCreation() {
        keys = Config.TESTABLE_KEY_TYPES.map {
            AWSKey.generateKey(
                it,
                AWSKeyMetadataSDK(

                    auth = AwsSDKAuth(
                        region = "eu-central-1",
                    )
                )
            ).also {
                println("Generated key: ${it.keyType} - ${it.getKeyId()}")
                assertNotNull(it, "Key generation failed for $it")
                assertNotNull(it.getKeyId(), "Key ID should not be null")
                assertTrue(it.getKeyId().isNotBlank(), "Key ID should not be blank")
            }
        }
        println("Generated ${keys.size} keys")
        assertTrue(keys.isNotEmpty(), "No keys were created")
    }

    private suspend fun awsTestPublicKeys() {
        keys.forEach { key ->
            val publicKey = key.getPublicKey()
            println("Public key: ${publicKey.exportJWK()}")
            assertNotNull(publicKey, "Public key should not be null")
        }
    }

    private suspend fun awsTestSignRaw() {
        keys.forEach { key ->
            println("RAW sign/verification test with key type: ${key.keyType}")
            val signature = key.signRaw(Config.payload.toByteArray())
            val verified = key.verifyRaw(signature, Config.payload.toByteArray())
            assertNotNull(signature, "Signature should not be null")
            assertTrue(signature.isNotEmpty(), "Signature should not be empty")
            assertTrue(verified.isSuccess, "Raw signature verification failed")
        }
    }

    private suspend fun awsTestSignJws() {
        keys.forEach { key ->
            println("JWS sign/verification test with key type: ${key.keyType}")
            val signature = key.signJws(Config.payloadJWS.toString().toByteArray())
            val verified = key.verifyJws(signature)
            assertNotNull(signature, "JWS signature should not be null")
            assertTrue(signature.isNotEmpty(), "JWS signature should not be empty")
            assertTrue(verified.isSuccess, "JWS signature verification failed")
            assertEquals(Config.payloadJWS, verified.getOrThrow(), "JWS payload mismatch after verification")
        }
    }

    private suspend fun awsTestDeleteKey() {
        keys.forEach { key ->
            println("Deleting key: ${key.getKeyId()}")
            val delete = key.deleteKey()
            println("Delete result: $delete")
            assertTrue(delete, "Key deletion failed")

        }

    }
}
