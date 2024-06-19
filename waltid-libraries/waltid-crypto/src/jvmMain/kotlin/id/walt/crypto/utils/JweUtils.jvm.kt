package id.walt.crypto.utils

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.ECDHEncrypter
import com.nimbusds.jose.crypto.factories.DefaultJWEDecrypterFactory
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.util.Base64URL
import kotlinx.serialization.json.*

actual object JweUtils {
  actual fun toJWE(payload: JsonObject, jwk: String, alg: String, enc: String, headerParams: Map<String, JsonElement>): String {
    if(alg != "ECDH-ES") throw UnsupportedOperationException("Algorithm $alg is not supported")

    val jweObject = JWEObject(JWEHeader(JWEAlgorithm.parse(alg), EncryptionMethod.parse(enc),
      null, null, null, null, null, null, null, null, null,
      headerParams["kid"]?.jsonPrimitive?.content,
      headerParams["epk"]?.let { JWK.parse(it.jsonObject.toString()) }, null,
      headerParams["apu"]?.let { Base64URL.from(it.jsonPrimitive.content) },
      headerParams["apv"]?.let { Base64URL.from(it.jsonPrimitive.content) }, null, 0,
      null, null, null, null, null), Payload(payload.toString()))
    val encrypter = ECDHEncrypter(ECKey.parse(jwk))
    jweObject.encrypt(encrypter)
    return jweObject.serialize()
  }

  actual fun parseJWE(jwe: String, jwk: String): JwsUtils.JwsParts {
    val jweObject = JWEObject.parse(jwe)
    if(jweObject.header.algorithm != JWEAlgorithm.ECDH_ES) throw UnsupportedOperationException("Algorithm ${jweObject.header.algorithm} is not supported")
    val decrypter = DefaultJWEDecrypterFactory().createJWEDecrypter(jweObject.header, ECKey.parse(jwk).toECPrivateKey())
    jweObject.decrypt(decrypter)
    return JwsUtils.JwsParts(
      header = Json.parseToJsonElement(jweObject.header.toString()).jsonObject,
      payload =  Json.parseToJsonElement(jweObject.payload.toString()).jsonObject,
      signature =  "")
  }
}
