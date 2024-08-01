import TestUtils.loadSerializedLocal
import com.nimbusds.jose.JWSObject
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.keys.KeyManager
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeySignTests {

    private val payload = JsonObject(
        mapOf(
            "sub" to JsonPrimitive("16bb17e0-e733-4622-9384-122bc2fc6290"),
            "iss" to JsonPrimitive("http://localhost:3000"),
            "aud" to JsonPrimitive("TOKEN"),
        )
    )

    @ParameterizedTest
    @ValueSource(strings = ["ed25519.private.json", "secp256k1.private.json", "secp256r1.private.json", "rsa.private.json"])
    fun `given key and payload, when signing jws then the result is a valid jws signature`(
        keyFile: String
    ) = runTest {
        // given
        val key = KeyManager.resolveSerializedKey(loadSerializedLocal(keyFile))
        // when
        val signature = key.signJws(payload.toString().encodeToByteArray(), mapOf("h1" to buildJsonObject {
           put("h1.1", "bla".toJsonElement())
        }))
        val verificationResult = key.getPublicKey().verifyJws(signature)
        val header = Json.parseToJsonElement(JWSObject.parse(signature).header.toString())
        assertContains(header.jsonObject.keys, "h1")
        assertContains(header.jsonObject["h1"]!!.jsonObject.keys, "h1.1")
        assertEquals(header.jsonObject["h1"]!!.jsonObject["h1.1"]!!.jsonPrimitive.content, "bla")
        // then
        assertTrue(verificationResult.isSuccess)
        assertEquals(payload, verificationResult.getOrThrow())
    }

    @ParameterizedTest
    @ValueSource(strings = ["ed25519.private.json", "secp256k1.private.json", "secp256r1.private.json", "rsa.private.json"])
    @Disabled // not implemented
    fun `given key and payload, when signing raw then the result is a valid signature`(keyFile: String) = runTest {
        // given
        val key = KeyManager.resolveSerializedKey(loadSerializedLocal(keyFile))
        // when
        val signature = key.signRaw(payload.toString().encodeToByteArray())
        val verificationResult = key.getPublicKey().verifyRaw(signature as ByteArray)
        // then
        assertTrue(verificationResult.isSuccess)
        assertEquals(payload.toString().encodeToByteArray(), verificationResult.getOrThrow())
    }
}
