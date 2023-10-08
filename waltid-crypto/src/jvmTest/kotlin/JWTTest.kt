import id.walt.core.crypto.keys.KeyType
import id.walt.core.crypto.keys.LocalKey
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

class JWTTest {

    @Test
    fun testJwt() = runTest {
        val localKey by lazy { runBlocking { LocalKey.generate(KeyType.Ed25519) } }

        val payload = JsonObject(
            mapOf(
                "sub" to JsonPrimitive("16bb17e0-e733-4622-9384-122bc2fc6290"),
                "iss" to JsonPrimitive("http://localhost:3000"),
                "aud" to JsonPrimitive("TOKEN"),
            )
        )

        println("Signing JWS: $payload")
        val signed = localKey.signJws(payload.toString().toByteArray())
        println("Signed: $signed")

        println("Verifying signed: $signed")
        localKey.verifyJws(signed).also { println("Verified: $it") }
    }

}
