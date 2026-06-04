import TSEKeyTest.Config.TESTABLE_KEY_TYPES
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.tse.TSEKey
import id.walt.crypto.keys.tse.TSEKeyMetadata
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TSEKeyTest {

    private object Config {
        const val BASE_SERVER = "http://127.0.0.1:8200"
        const val BASE_URL = "$BASE_SERVER/v1/transit"
        const val TOKEN = "dev-only-token"
        val NAMESPACE = null

        val payload = JsonObject(
            mapOf(
                "sub" to JsonPrimitive("16bb17e0-e733-4622-9384-122bc2fc6290"),
                "iss" to JsonPrimitive("http://localhost:3000"),
                "aud" to JsonPrimitive("TOKEN"),
            )
        )

        //val TESTABLE_KEY_TYPES = KeyType.entries.filterNot { it in listOf(KeyType.secp256k1) } // TODO get working: this line
        val TESTABLE_KEY_TYPES = listOf(KeyType.Ed25519)
    }

    private val http = HttpClient()
    private suspend fun isVaultAvailable() = runCatching {
        http.get(Config.BASE_SERVER).status == HttpStatusCode.OK
    }.fold(onSuccess = { it }, onFailure = { false })

    lateinit var keys: List<TSEKey>

    @Test
    fun testTse() = runTest {
        isVaultAvailable().takeIf { it }?.let {
            println("Testing key creation of: $TESTABLE_KEY_TYPES...")
            tse1TestKeyCreation()

            println("Testing resolving to public key for: $keys...")
            tse2TestPublicKeys()

            println("Testing sign & verify raw (payload: ${Config.payload})...")
            tse3TestSignRaw()

            println("Testing sign & verify JWS (payload: ${Config.payload})...")
            tse4TestSignJws()

            println("Testing key deletion of: $keys...")
            tse99TestKeyDeletion()
        }
    }

    private suspend fun tse1TestKeyCreation() {
        val tseMetadata = TSEKeyMetadata(Config.BASE_URL, Config.TOKEN, Config.NAMESPACE)
        keys = TESTABLE_KEY_TYPES.map { TSEKey.generate(it, tseMetadata) }
    }

    private suspend fun tse2TestPublicKeys() {
        keys.forEach { key ->
            val publicKey = key.getPublicKey()
            assertTrue { !publicKey.hasPrivateKey }
        }
        // TODO: assert keyId, thumbprint, export
    }

    private suspend fun tse3TestSignRaw() {
        keys.forEach { key ->
            val signed = key.signRaw(Config.payload.toString().encodeToByteArray()) as String
            val verificationResult = key.verifyRaw(signed.decodeBase64Bytes(), Config.payload.toString().encodeToByteArray())
            assertTrue(verificationResult.isSuccess)
            assertEquals(Config.payload.toString(), verificationResult.getOrThrow().decodeToString())
        }
    }

    private suspend fun tse4TestSignJws() {
        keys.forEach { key ->
            val signed = key.signJws(Config.payload.toString().encodeToByteArray())
            val verificationResult = key.verifyJws(signed)
            assertTrue(verificationResult.isSuccess)
            assertEquals(Config.payload, verificationResult.getOrThrow())
        }
    }

    private suspend fun tse99TestKeyDeletion() {
        keys.forEach { it.delete() }
    }


}
