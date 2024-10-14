import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.aws.AWSKey
import id.walt.crypto.keys.aws.AWSKeyMetadata
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

class AWSKeyTest {


    private object Config {
        const val secretAccessKey = ""
        const val accessKeyId = ""
        val region = "eu-central-1"

        val payload1 = JsonObject(
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

            println("Testing sign & verify JWS (payload: ${Config.payload})...")
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
            AWSKey.generate(it, awsMetadata).also {
                println("Generated key: ${it.keyType} - ${it.getKeyId()}")
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
            val signature = key.signRaw(Config.payload.toString().toByteArray())
            val verified = key.verifyRaw(signature, Config.payload.toString().toByteArray())
            println("Signature: $signature - Verified: ${verified.isSuccess}")
        }
    }

    private suspend fun awsTestSignJws() {
        keys.forEach { key ->
            val signature = key.signJws(Config.payload1.toString().toByteArray())
            val verified = key.verifyJws(signature)
            println("Signature: $signature - Verified: ${verified.isSuccess} with keytype: ${key.keyType}")
        }
    }
}