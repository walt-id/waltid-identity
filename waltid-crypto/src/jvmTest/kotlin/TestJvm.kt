import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class TestJvm {

    suspend fun test(keyType: KeyType) {
        val plaintext = JsonObject(
            mapOf("id" to JsonPrimitive("abc123-${keyType.name}-JVM"))
        )
        println("Plaintext: $plaintext")

        println("Generating key: $keyType...")
        val key = LocalKey.generate(keyType)

        println("  Checking for private key...")
        assertTrue { key.hasPrivateKey }

        println("  Checking for private key on a public key...")
        assertFalse { key.getPublicKey().hasPrivateKey }

        println("  Key id: " + key.getKeyId())

        println("  Exporting JWK...")
        val exportedJwk = key.exportJWK()
        println("  JWK: $exportedJwk")
        assertTrue { exportedJwk.startsWith("{") }

        println("  Signing...")
        val signed = key.signJws(Json.encodeToString(plaintext).encodeToByteArray())
        println("  Signed: $signed")

        println("  Verifying...")
        val check1 = key.verifyJws(signed)
        assertTrue(check1.isSuccess)
        assertEquals(plaintext, check1.getOrThrow())

        assertEquals(plaintext, check1.getOrThrow())
        println("  Private key: ${check1.getOrNull()}")

        val check2 = key.getPublicKey().verifyJws(signed)
        assertEquals(plaintext, check2.getOrThrow())
        println("  Public key: ${check2.getOrNull()}")
    }

    @Test
    fun apiTestAll() = runTest {
        KeyType.entries.forEach { test(it) }
    }

    @Test
    fun signatureSpeedTestAll() = runTest(timeout = 5.minutes) {
        KeyType.entries.forEach { keyType ->
            val key = LocalKey.generate(keyType)
            key.signJws("abc".encodeToByteArray())

            val jobs = ArrayList<Job>()

            val n = 10_000
            val dispatchMs = measureTimeMillis {
                repeat(n) {
                    jobs.add(GlobalScope.launch {
                        key.signJws(byteArrayOf(it.toByte()))
                    })
                }
            }

            val signMs = measureTimeMillis {
                jobs.forEach { it.join() }
            }

            println("$keyType: Dispatch $dispatchMs ms, Signing: $signMs ms (for $n signatures)")
        }
    }

}
