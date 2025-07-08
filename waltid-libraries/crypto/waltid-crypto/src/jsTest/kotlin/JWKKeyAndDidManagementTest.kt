import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JWKKeyAndDidManagementTest {

    private val keyTypeMap = mapOf(
        KeyType.Ed25519 to "OKP",
        KeyType.secp256k1 to "EC",
        KeyType.secp256r1 to "EC",
        KeyType.RSA to "RSA"
    )

    private fun getKeyTypeMap(kt: KeyType): String? {
        return keyTypeMap[kt]
    }

    private val testObj = JsonObject(mapOf("value1" to JsonPrimitive("123456789")))

    @Test
    @Ignore
    //TODO: This tests does not work - timeout error
    fun jwkKeyGenerationTest() = runTest {
        KeyType.values().forEach {
            println("Generate key for key type $it")
            val generatedKey = JWKKey.generate(it)

            println("Serializing key: $generatedKey")
            val serialized = KeySerialization.serializeKey(generatedKey)

            println("Decoding serialized key: $serialized")
            val decoded = Json.parseToJsonElement(serialized).jsonObject
            println("Decoded $serialized as $decoded")

            println("Checking if type=local")
            assertEquals("jwk", decoded["type"]!!.jsonPrimitive.content)

            println("Parsing JWK...")
            val jwk = decoded["jwk"]!!.jsonObject
            println("JWK is: $jwk")

            println("Getting kty, d, crv, kid, x...")
            val kty = jwk["kty"].toString().removeSurrounding("\"")
            val d = jwk["d"].toString().removeSurrounding("\"")
            val crv = jwk["crv"].toString().removeSurrounding("\"")
            val kid = jwk["kid"].toString().removeSurrounding("\"")
            val x = jwk["x"].toString().removeSurrounding("\"")

            println("Checking kty, d, crv, kid, x...")
            assertEquals(kty, getKeyTypeMap(it))
            assertNotNull(d)
            assertNotNull(crv)
            assertNotNull(kid)
            assertNotNull(x)

            println("use the generated key to sign using JWS and RAW signature types")

            println("Signing JWS...")
            signJws(serialized)

            println("Signing raw...")

            try {
                signRaw(serialized)
            } catch (e: NotImplementedError) {
                println("Raw signatures not implemented on this platform!")
            }

            println("export the key as JWK or JSON")
            exportJwk(serialized)
            exportJson(serialized)
        }
    }

    private suspend fun signJws(serializedKey: String) {
        val testObjJson = Json.encodeToString(testObj)

        // sign using newly generated key
        val key = KeyManager.resolveSerializedKey(serializedKey)
        val signature = key.signJws(testObjJson.encodeToByteArray())

        // verify the signature using public key
        val verificationResult = key.getPublicKey().verifyJws(signature)

        println("Verify signed object using key ${key.getPublicKey()}")
        assertTrue(verificationResult.isSuccess)
        assertEquals(testObj, verificationResult.getOrThrow())
    }

    private suspend fun signRaw(serializedKey: String) {
        val testObjJson = Json.encodeToString(testObj)

        // sign using newly generated key
        val key = KeyManager.resolveSerializedKey(serializedKey)
        val signature = key.signRaw(testObjJson.encodeToByteArray())

        assertNotNull(signature)
    }

    private suspend fun exportJwk(serializedKey: String) {
        val decoded = Json.decodeFromString<JsonObject>(serializedKey)
        val jwk = decoded["jwk"]!!.jsonObject

        val key = KeyManager.resolveSerializedKey(serializedKey)
        val export = key.exportJWK()

        assertEquals(Json.encodeToString(jwk), export)
    }

    private suspend fun exportJson(serializedKey: String) {
        val decoded = Json.decodeFromString<JsonObject>(serializedKey)
        val jwk = decoded["jwk"]!!.jsonObject

        val key = KeyManager.resolveSerializedKey(serializedKey)
        val export = key.exportJWKObject()
        assertEquals(jwk, export)
    }
}
