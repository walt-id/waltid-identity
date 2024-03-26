import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestJs {

    suspend fun test(keyType: KeyType) {
        val plaintext = JsonObject(
            mapOf("id" to JsonPrimitive("abc123-${keyType.name}-JS"))
        )

        println("Generating key: $keyType...")
        val key = JWKKey.generate(keyType)

        println("  Checking for private key...")
        assertTrue { key.hasPrivateKey }

        println("  Key id: " + key.getKeyId())

        println("  Exporting JWK...")
        val exportedJwk = key.exportJWK()
        println("  JWK: $exportedJwk")
        assertTrue { exportedJwk.startsWith("{") }

        println("  Checking for private key on a public key...")
        assertFalse { key.getPublicKey().hasPrivateKey }

        println("  Checking for changes...")
        assertEquals(exportedJwk, key.exportJWK())

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
}
