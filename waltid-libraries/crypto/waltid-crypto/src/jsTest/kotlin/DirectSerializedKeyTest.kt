import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class DirectSerializedKeyTest {
    @Test
    fun deserializesWithoutBlockingTheEventLoop() = runTest {
        val original = JWKKey.generate(KeyType.secp256r1)
        val restored = Json.decodeFromString<DirectSerializedKey>(
            Json.encodeToString(DirectSerializedKey(original))
        ).key
        val payload = "{\"subject\":\"alice\"}".encodeToByteArray()
        val signed = restored.signJws(payload, mapOf("typ" to JsonPrimitive("JWT")))

        assertEquals(KeyType.secp256r1, restored.keyType)
        assertEquals(
            "alice",
            restored.getPublicKey().verifyJws(signed).getOrThrow().jsonObject.getValue("subject").jsonPrimitive.content,
        )
    }
}
