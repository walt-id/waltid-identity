import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import io.ktor.utils.io.core.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val keyTypeMap = mapOf<KeyType, String>(
  KeyType.Ed25519 to "OKP",
  KeyType.secp256k1 to "EC",
  KeyType.secp256r1 to "EC",
  KeyType.RSA to "RSA"
)

private fun getKeyTypeMap(kt: KeyType): String? {
  return keyTypeMap[kt]
}

private val testObj = JsonObject(mapOf("value1" to JsonPrimitive("123456789")))

@Serializable
class KeyTest {
  
  @Test
  fun localKeyGenerationTest() = runTest {
    
    KeyType.entries.forEach {
      println("Generate key for key type $it")
      val serialized = KeySerialization.serializeKey(LocalKey.generate(it))
      val decoded = Json.decodeFromString<JsonObject>(serialized)
      
      assertEquals("local", decoded["type"]!!.jsonPrimitive.content)
      
      val jwk = decoded["jwk"]!!.jsonPrimitive.content
      
      val kty = Json.parseToJsonElement(jwk).jsonObject["kty"].toString().removeSurrounding("\"")
      val d = Json.parseToJsonElement(jwk).jsonObject["d"].toString().removeSurrounding("\"")
      val crv = Json.parseToJsonElement(jwk).jsonObject["crv"].toString().removeSurrounding("\"")
      val kid = Json.parseToJsonElement(jwk).jsonObject["kid"].toString().removeSurrounding("\"")
      val x = Json.parseToJsonElement(jwk).jsonObject["x"].toString().removeSurrounding("\"")
      
      assertEquals(kty, getKeyTypeMap(it))
      assertNotNull(d)
      assertNotNull(crv)
      assertNotNull(kid)
      assertNotNull(x)
      
      // use the generated key to sign using JWS and RAW signature types
      signJws(serialized)
      signRaw(serialized)

      // export the key as JWK or JSON
      exportJwk(serialized)
      exportJson(serialized)
    }
  }
  
  private suspend fun signJws(serializedKey: String) {
    val testObjJson = Json.encodeToString(testObj)
    
    // sign using newly generated key
    val key = KeySerialization.deserializeKey(serializedKey).getOrThrow()
    val signature = key.signJws(testObjJson.toByteArray())
    
    // verify the signature using public key
    val verificationResult = key.getPublicKey().verifyJws(signature)
    
    println("Verify signed object using key ${key.getPublicKey()}")
    assertTrue(verificationResult.isSuccess)
    assertEquals(testObj, verificationResult.getOrThrow())
  }
  
  private suspend fun signRaw(serializedKey: String) {
    val testObjJson = Json.encodeToString(testObj)
    
    // sign using newly generated key
    val key = KeySerialization.deserializeKey(serializedKey).getOrThrow()
    val signature = key.signRaw(testObjJson.toByteArray())
    
    assertNotNull(signature)
  }
  
  private suspend fun exportJwk(serializedKey: String) {
    val decoded = Json.decodeFromString<JsonObject>(serializedKey)
    val jwk = decoded["jwk"]!!.jsonPrimitive.content
    
    val key = KeySerialization.deserializeKey(serializedKey).getOrThrow()
    val export = key.exportJWK()

    assertEquals(jwk, export)
  }
  
  private suspend fun exportJson(serializedKey: String) {
    val decoded = Json.decodeFromString<JsonObject>(serializedKey)
    val jwk = decoded["jwk"]!!.jsonPrimitive.content
    
    val key = KeySerialization.deserializeKey(serializedKey).getOrThrow()
    val export = key.exportJWKObject()
    assertEquals(jwk, export.toString())
  }
}