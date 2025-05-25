package id.walt.w3c.schemes

import id.walt.crypto.exceptions.VerificationException
import id.walt.crypto.keys.Key
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import id.walt.sdjwt.JWTCryptoProvider
import id.walt.sdjwt.SDJwt
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

private val log = KotlinLogging.logger { }

@OptIn(ExperimentalJsExport::class)
@JsExport
class JwsSignatureScheme : SignatureScheme {

  object JwsHeader {
    const val KEY_ID = "kid"
  }

  object JwsOption {
    const val SUBJECT = "sub"
    const val ISSUER = "iss"
    const val EXPIRATION = "exp"
    const val NOT_BEFORE = "nbf"
    const val VC_ID = "jti"
    const val VC = "vc"
  }

  data class KeyInfo(val keyId: String, val key: Key)

  fun toPayload(data: JsonObject, jwtOptions: Map<String, JsonElement> = emptyMap()) =
    mapOf(
      JwsOption.ISSUER to jwtOptions[JwsOption.ISSUER],
      JwsOption.SUBJECT to jwtOptions[JwsOption.SUBJECT],
      JwsOption.VC to data,
      *(jwtOptions.entries.map { it.toPair() }.toTypedArray())
    ).toJsonObject()

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  suspend fun getIssuerKeyInfo(jws: String): KeyInfo {
    val jwsParsed = jws.substringBefore("~").decodeJws()
    val keyId = jwsParsed.header[JwsHeader.KEY_ID]!!.jsonPrimitive.content
    val issuerId = (jwsParsed.payload[JwsOption.ISSUER]?.jsonPrimitive?.content ?: keyId)
    val key = if (DidUtils.isDidUrl(issuerId)) {
      log.trace { "Resolving key from issuer did: $issuerId" }
      DidService.resolveToKey(issuerId)
        .also {
          if (log.isTraceEnabled()) {
            val exportedJwk = it.getOrNull()?.getPublicKey()?.exportJWK()
            log.trace { "Imported key: $it from did: $issuerId, public is: $exportedJwk" }
          }
        }
        .getOrThrow()
    } else
      TODO("Issuer IDs other than DIDs are currently not supported for W3C credentials.")
    return KeyInfo(keyId, key)
  }

  /**
   * args:
   * - kid: Key ID
   * - subjectDid: Holder DID
   * - issuerDid: Issuer DID
   */
  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  suspend fun sign(
    data: JsonObject, key: Key,
    /** Set additional options in the JWT header */
    jwtHeaders: Map<String, JsonElement> = emptyMap(),
    /** Set additional options in the JWT payload */
    jwtOptions: Map<String, JsonElement> = emptyMap(),
  ): String {
    val payload = Json.encodeToString(
      toPayload(data, jwtOptions)
    ).encodeToByteArray()

    return key.signJws(payload, jwtHeaders)
  }

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  suspend fun verify(data: String): Result<JsonElement> = runCatching {
    val keyInfo = getIssuerKeyInfo(data)
    return keyInfo.key.verifyJws(data.split("~")[0])
      .also { log.trace { "Verification result: $it" } }
  }

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  suspend fun verifySDJwt(data: String, jwtCryptoProvider: JWTCryptoProvider): Result<JsonElement> = runCatching {
    return SDJwt.verifyAndParse(data, jwtCryptoProvider).let {
      if(it.verified)
        Result.success(it.sdJwt.fullPayload)
      else
        Result.failure(VerificationException(it.message ?: "Verification failed"))
    }
  }
}
