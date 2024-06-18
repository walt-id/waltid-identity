import TestUtils.loadSerializedLocal
import id.walt.crypto.keys.KeySerialization
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
        val key = KeySerialization.deserializeKey(loadSerializedLocal(keyFile)).getOrThrow()
        // when
        val signature = key.signJws(payload.toString().encodeToByteArray())
        val verificationResult = key.getPublicKey().verifyJws(signature)
        // then
        assertTrue(verificationResult.isSuccess)
        assertEquals(payload, verificationResult.getOrThrow())
    }

    @ParameterizedTest
    @ValueSource(strings = ["ed25519.private.json", "secp256k1.private.json", "secp256r1.private.json", "rsa.private.json"])
    @Disabled // not implemented
    fun `given key and payload, when signing raw then the result is a valid signature`(keyFile: String) = runTest {
        // given
        val key = KeySerialization.deserializeKey(loadSerializedLocal(keyFile)).getOrThrow()
        // when
        val signature = key.signRaw(payload.toString().encodeToByteArray())
        val verificationResult = key.getPublicKey().verifyRaw(signature as ByteArray)
        // then
        assertTrue(verificationResult.isSuccess)
        assertEquals(payload.toString().encodeToByteArray(), verificationResult.getOrThrow())
    }
}