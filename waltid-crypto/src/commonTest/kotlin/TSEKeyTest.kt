import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.TSEKey
import id.walt.crypto.keys.TSEKeyMetadata
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.condition.EnabledIf
import kotlin.test.Test

class TSEKeyTest {
    private val payload = JsonObject(
        mapOf(
            "sub" to JsonPrimitive("16bb17e0-e733-4622-9384-122bc2fc6290"),
            "iss" to JsonPrimitive("http://localhost:3000"),
            "aud" to JsonPrimitive("TOKEN"),
        )
    )

    fun getPublicKeyRepresentation() = runTest {

    }

    fun getPublicKey() = runTest {

    }

    fun getKeyType() = runTest {

    }

    fun getHasPrivateKey() = runTest {

    }

    fun signRaw() = runTest {

    }

    fun signJws() = runTest {

    }

    fun getKeyId() = runTest {

    }

    fun verifyJws() = runTest {

    }

    fun exportJWK() = runTest {

    }

    fun exportJWKObject() = runTest {

    }

    fun exportPEM() = runTest {

    }



    @Test
    @EnabledIf("hostCondition")
    @Disabled
    fun testAll() = runTest {
        keys.forEach {
            exampleKeySignRaw(it)
        }
    }

    private suspend fun exampleKeySignRaw(key: TSEKey) {
        try {
            println("TSEKey: $key")
            val plaintext = "This is a plaintext for ${key.keyType.name}... 123".encodeToByteArray()
            println("Plaintext: ${plaintext.decodeToString()}")

            val signed = key.signRaw(plaintext) as String
            println("Signed: $signed")

            val verified = key.verifyRaw(signed.decodeBase64Bytes(), plaintext)
            println("Verified signature success: ${verified.isSuccess}")
            println("Verified plaintext: ${verified.getOrNull()!!.decodeToString()}")
        } finally {
            println("Deleting $key...")
            key.delete()
        }
    }

    companion object {
        private lateinit var keys: List<TSEKey>
        @JvmStatic
        @BeforeAll
        fun initKeys() = runTest {
            hostCondition().takeIf { it }?.run {
                val tseMetadata = TSEKeyMetadata("http://127.0.0.1:8200/v1/transit", "dev-only-token")
                keys = enumValues<KeyType>().map { TSEKey.generate(KeyType.Ed25519, tseMetadata) }
            }
        }

        @JvmStatic
        @AfterAll
        fun cleanup() = runTest {
            hostCondition().takeIf { it }?.run { keys.forEach { it.delete() } }
        }

        private fun hostCondition() = runCatching {
            runBlocking { HttpClient().get("http://127.0.0.1:8200") }.status == HttpStatusCode.OK
        }.fold(onSuccess = { true }, onFailure = { false })
    }
}