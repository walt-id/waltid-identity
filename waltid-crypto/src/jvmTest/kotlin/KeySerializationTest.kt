import id.walt.core.crypto.keys.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class KeySerializationTest {

    @Test
    fun test() = runTest {
        val localKey = LocalKeyGenerator.generate(KeyType.Ed25519)
        val localKeySerialized = KeySerialization.serializeKey(localKey)

        val jsons = listOf(
            localKeySerialized to LocalKey::class,
            """{ "type": "tse", "_tseId": "tseid", "_rawPublicKey": [], "keyType": "Ed25519" }""" to TSEKey::class
        )

        jsons.forEach {
            check(it)
        }
    }

    private val testObj = JsonObject(mapOf("value1" to JsonPrimitive("123456789")))

    @Test
    fun testDeserializedVerify() = runTest {
        val testObjJson = Json.encodeToString(testObj)

        val key = LocalKeyGenerator.generate(KeyType.Ed25519)
        val key2 = KeySerialization.deserializeKey(KeySerialization.serializeKey(key)).getOrThrow()

        val jws = key.signJws(testObjJson.toByteArray())

        val res = key2.verifyJws(jws)
        println(res)
        assertEquals(testObj, res.getOrThrow())
    }


    @Test
    fun testDeserializedSign() = runTest {
        val testObjJson = Json.encodeToString(testObj)

        val keyToUseForVerifying = LocalKeyGenerator.generate(KeyType.Ed25519)
        val keyToUseForSigning = KeySerialization.deserializeKey(KeySerialization.serializeKey(keyToUseForVerifying)).getOrThrow()

        val jws = keyToUseForSigning.signJws(testObjJson.toByteArray())

        val res = keyToUseForVerifying.verifyJws(jws)
        println(res)
        assertEquals(testObj, res.getOrThrow())
    }

    private fun check(value: Pair<String, KClass<out Key>>) {
        println("Parsing: ${value.first}")
        val key = KeySerialization.deserializeKey(value.first).getOrThrow()

        println("Got key: $key")
        println("Of type: " + key::class.simpleName)

        assertEquals(value.second.simpleName, key::class.simpleName)
        assertNotEquals("Key", key::class.simpleName)

        println()
    }
}
