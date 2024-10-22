import AWSKeyTest.Config.payloadJWS
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.aws.AWSKey
import id.walt.crypto.keys.aws.AWSKeyMetadata
import io.ktor.utils.io.core.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/**
 * Enable this test by providing valid credentials for accessKeyId and secretAccessKey below.
 */
class AWSKeyTest {


    private object Config {
        val accessKeyId = ""
        val secretAccessKey = ""
        val region = "eu-central-1"

        val payloadJWS = JsonObject(
            mapOf(
                "sub" to JsonPrimitive("16bb17e0-e733-4622-9384-122bc2fc6290"),
                "iss" to JsonPrimitive("http://localhost:3000"),
                "aud" to JsonPrimitive("TOKEN"),
            )
        )
        val payload = "Hello, World!"

        val TESTABLE_KEY_TYPES = listOf(KeyType.RSA, KeyType.secp256r1, KeyType.secp256k1)


    }

    private fun isConfigAvailable() = runCatching {
        Config.accessKeyId.isNotEmpty() && Config.secretAccessKey.isNotEmpty()
    }.fold(onSuccess = { it }, onFailure = { false })


    lateinit var keys: List<AWSKey>


    @Test
    fun testAws() = runTest {
        isConfigAvailable().takeIf { it }?.let {
            println("Testing key creation of: ${Config.TESTABLE_KEY_TYPES}...")
            awsTestKeyCreation()

            println("Testing resolving to public key for: $keys...")
            awsTestPublicKeys()

            println("Testing sign & verify raw (payload: ${Config.payload})...")
            awsTestSignRaw()

            println("Testing sign & verify JWS (payload: ${Config.payloadJWS})...")
            awsTestSignJws()
        }
    }

    private suspend fun awsTestKeyCreation() {
        val awsMetadata = AWSKeyMetadata(
            region = Config.region,
            secretAccessKey = Config.secretAccessKey,
            accessKeyId = Config.accessKeyId
        )

        keys = Config.TESTABLE_KEY_TYPES.map {
            AWSKey.generate(it, awsMetadata).also { key ->
                println("Generated key: ${key.keyType} - ${key.getKeyId()}")
                assertNotNull(key, "Key generation failed for $it")
                assertNotNull(key.getKeyId(), "Key ID should not be null")
                assertTrue(key.getKeyId().isNotBlank(), "Key ID should not be blank")
                assertEquals(it, key.keyType, "Key type does not match expected value")
            }
        }
    }

    private suspend fun awsTestPublicKeys() {
        keys.forEach {
            println("Public key: ${it.getPublicKey().exportJWK()}")
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
            val signature = key.signJws(payloadJWS.toString().toByteArray())
            val verified = key.verifyJws(signature)
            assertNotNull(signature, "JWS signature should not be null")
            assertTrue(signature.isNotEmpty(), "JWS signature should not be empty")
            assertTrue(verified.isSuccess, "JWS signature verification failed")
            assertEquals(payloadJWS, verified.getOrThrow(), "JWS payload mismatch after verification")
        }
    }
}