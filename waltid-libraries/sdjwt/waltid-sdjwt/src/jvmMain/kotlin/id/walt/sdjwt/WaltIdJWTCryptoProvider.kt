package id.walt.sdjwt

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

open class WaltIdJWTCryptoProvider(val keys: Map<String, Key>): JWTCryptoProvider {
  constructor(key: Key): this(mapOf(runBlocking{ key.getKeyId() } to key))
  override fun sign(payload: JsonObject, keyID: String?, typ: String, headers: Map<String, Any>): String = runBlocking {
    val key = keyID?.let { keys[it] } ?: throw Exception("No key found")
    if(!key.hasPrivateKey) throw Exception("Key has no private key")
    val allHeaders = mapOf("kid" to key.getKeyId(), "typ" to typ).plus(headers)
    return@runBlocking key.signJws(
      payload.toString().encodeToByteArray(),
      allHeaders.mapValues {
        it.value.toJsonElement()
      })
  }

  override fun verify(jwt: String, keyID: String?): JwtVerificationResult = runBlocking {
    val key = keyID?.let { keys[it] } ?: throw Exception("No key found")
    return@runBlocking key.verifyJws(jwt).let {
      JwtVerificationResult(
        it.isSuccess,
        it.toString()
      )
    }
  }

}

class SingleKeyJWTCryptoProvider(key: Key): WaltIdJWTCryptoProvider(key) {
  fun sign(payload: JsonObject, typ: String, headers: Map<String, Any>) = sign(payload, keys.keys.first(), typ, headers)
}
