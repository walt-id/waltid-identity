package id.walt.crypto.keys

import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.base64toBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JwsUtils.jwsAlg
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlinx.coroutines.*
import kotlinx.datetime.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import org.kotlincrypto.hash.sha2.SHA256

@OptIn(ExperimentalJsExport::class)
@JsExport
@Suppress("TRANSIENT_IS_REDUNDANT")
@Serializable
@SerialName("oci")
class OCIKey(
    val OCIConfig: OCIKeyConfig,
    val id: String,
    private var _publicKey: ByteArray? = null,
    private var _keyType: KeyType? = null,
) : Key() {

  @OptIn(DelicateCoroutinesApi::class)
  private inline fun <T> lazySuspended(
      crossinline block: suspend CoroutineScope.() -> T
  ): Deferred<T> =
      GlobalScope.async(Dispatchers.Unconfined, start = CoroutineStart.LAZY) { block.invoke(this) }

  @Transient val retrievedKeyType = lazySuspended { retrieveKeyType() }

  @Transient val retrievedPublicKey = lazySuspended { retrievePublicKey() }

  @Transient
  override var keyType: KeyType
    get() = _keyType!!
    set(value) {
      _keyType = value
    }

  private suspend fun getBackingPublicKey(): ByteArray = _publicKey ?: retrievedPublicKey.await()

  override val hasPrivateKey: Boolean
    get() = false

  private suspend fun retrievePublicKey(): ByteArray {

    val pk = this._publicKey

    return pk.toString().encodeToByteArray()
  }

  private suspend fun retrieveKeyType(): KeyType {
    val keyType = ociKeyToKeyTypeMapping("RSA")
    return keyType
  }

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  override suspend fun getKeyId(): String = getPublicKey().getKeyId()

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  override suspend fun getThumbprint(): String {
    TODO("Not yet implemented")
  }

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  override suspend fun exportJWK(): String {
    TODO("Not yet implemented")
  }

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  override suspend fun exportJWKObject(): JsonObject {
    TODO("Not yet implemented")
  }

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  override suspend fun exportPEM(): String {
    TODO("Not yet implemented")
  }

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  override suspend fun signRaw(plaintext: ByteArray): Any {
    val encodedMessage = plaintext.encodeBase64()

    val requestBody =
        JsonObject(
                mapOf(
                    "keyId" to JsonPrimitive(id),
                    "message" to JsonPrimitive(encodedMessage),
                    when (keyType) {
                      KeyType.secp256r1 -> "signingAlgorithm" to JsonPrimitive("ECDSA_SHA_256")
                      KeyType.RSA -> "signingAlgorithm" to JsonPrimitive("SHA_256_RSA_PKCS_PSS")
                      else -> "signingAlgorithm" to JsonPrimitive(null)
                    },
                ))
            .toString()
    val signature = signingRequest("POST", "/20180608/sign", OCIConfig.cryptoEndpoint, requestBody)

    val response =
        http
            .post("https://${OCIConfig.cryptoEndpoint}/20180608/sign") {
              header(
                  "Authorization",
                  """Signature version="1",headers="date (request-target) host content-length content-type x-content-sha256",keyId="${OCIConfig.keyId}",algorithm="rsa-sha256",signature="$signature"""")
              header("Date", GMTDate().toHttpDate())
              header("Host", host)
              header("Content-Length", requestBody.length.toString())
              header("Accept", "application/json")
              header("Connection", "keep-alive")
              header("Content-Type", "application/json")
              header("x-content-sha256", calculateSHA256(requestBody))
              setBody(requestBody)
            }
            .ociJsonDataBody()
            .jsonObject["signature"]
            ?.jsonPrimitive
            ?.content ?: ""
    return response
  }

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String {
    val header =
        Json.encodeToString(
                mutableMapOf(
                        "typ" to "JWT",
                        "alg" to keyType.jwsAlg(),
                    )
                    .apply { putAll(headers) })
            .encodeToByteArray()
            .encodeToBase64Url()

    val payload = plaintext.encodeToBase64Url()

    val signable = "$header.$payload"

    val signatureBase64 = signRaw(signable.encodeToByteArray()) as String
    val signatureBase64Url = signatureBase64.base64toBase64Url()

    return "$signable.$signatureBase64Url"
  }

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  override suspend fun verifyRaw(
      signed: ByteArray,
      detachedPlaintext: ByteArray?
  ): Result<ByteArray> {
    check(detachedPlaintext != null) { "An detached plaintext is needed." }

    val requestBody =
        JsonObject(
                mapOf(
                    "keyId" to JsonPrimitive(id),
                    "message" to JsonPrimitive(detachedPlaintext.encodeBase64()),
                    "signature" to JsonPrimitive(signed.encodeBase64()),
                    when (keyType) {
                      KeyType.secp256r1 -> "signingAlgorithm" to JsonPrimitive("ECDSA_SHA_256")
                      KeyType.RSA -> "signingAlgorithm" to JsonPrimitive("SHA_256_RSA_PKCS_PSS")
                      else -> "signingAlgorithm" to JsonPrimitive(null)
                    },
                ))
            .toString()
    val signature =
        signingRequest("POST", "/20180608/verify", OCIConfig.cryptoEndpoint, requestBody)

    val response =
        http
            .post("https://${OCIConfig.cryptoEndpoint}/20180608/verify") {
              header(
                  "Authorization",
                  """Signature version="1",headers="date (request-target) host content-length content-type x-content-sha256",keyId="${OCIConfig.keyId}",algorithm="rsa-sha256",signature="$signature"""")
              header("Date", GMTDate().toHttpDate())
              header("Host", host)
              header("Content-Length", requestBody.length.toString())
              header("Accept", "application/json")
              header("Connection", "keep-alive")
              header("Content-Type", "application/json")
              header("x-content-sha256", calculateSHA256(requestBody))
              setBody(requestBody)
            }
            .ociJsonDataBody()
            .jsonObject["isSignatureValid"]
            ?.jsonPrimitive
            ?.boolean ?: false
    return if (response) Result.success(detachedPlaintext)
    else Result.failure(Exception("Signature is not valid"))
  }

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  override suspend fun verifyJws(signedJws: String): Result<JsonObject> {
    val parts = signedJws.split(".")
    check(parts.size == 3) { "Invalid JWT part count: ${parts.size} instead of 3" }
    val header = parts[0]
    val headers: Map<String, JsonElement> =
        Json.decodeFromString(header.base64UrlDecode().decodeToString())
    headers["alg"]?.let {
      val algValue = it.jsonPrimitive.content
      check(algValue == keyType.jwsAlg()) {
        "Invalid key algorithm for JWS: JWS has $algValue, key is ${keyType.jwsAlg()}!"
      }
    }

    val payload = parts[1]
    val signature = parts[2].base64UrlDecode()

    val signable = "$header.$payload".encodeToByteArray()
    return verifyRaw(signature, signable).map {
      val verifiedPayload =
          it.decodeToString().substringAfter(".").base64UrlDecode().decodeToString()
      Json.parseToJsonElement(verifiedPayload).jsonObject
    }
  }

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  override suspend fun getPublicKey(): Key {
    println("Getting public key: $keyType")

    return LocalKey.importRawPublicKey(
        type = keyType,
        rawPublicKey = getBackingPublicKey(),
        metadata = LocalKeyMetadata(), // todo: import with explicit `keySize`
    )
  }

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  override suspend fun getPublicKeyRepresentation(): ByteArray {
    TODO("Not yet implemented")
  }

  companion object {

    private fun keyTypeToOciKeyMapping(type: KeyType) =
        when (type) {
          KeyType.secp256r1 -> "ECDSA"
          KeyType.RSA -> "RSA"
          KeyType.secp256k1 -> throw IllegalArgumentException("Not supported: $type")
          KeyType.Ed25519 -> throw IllegalArgumentException("Not supported: $type")
        }

    private fun ociKeyToKeyTypeMapping(type: String) =
        when (type) {
          "ECDSA" -> KeyType.secp256r1
          "RSA" -> KeyType.RSA
          else -> throw IllegalArgumentException("Not supported: $type")
        }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun generateKey(type: KeyType, config: OCIKeyConfig): OCIKey {
      val keyType = keyTypeToOciKeyMapping(type)
      val keyId = config.keyId
      val host = config.managementEndpoint
      val length =
          when (type) {
            KeyType.Ed25519 -> throw IllegalArgumentException("Not supported: $type")
            KeyType.secp256r1 -> 32
            KeyType.RSA -> 256
            KeyType.secp256k1 -> throw IllegalArgumentException("Not supported: $type")
          }
      val requestBody =
          JsonObject(
                  mapOf(
                      "compartmentId" to JsonPrimitive(config.tenancyOcid),
                      "displayName" to JsonPrimitive("WaltID"),
                      "keyShape" to
                          JsonObject(
                              mapOf(
                                  "algorithm" to JsonPrimitive(keyType),
                                  "length" to JsonPrimitive(length),
                                  when (type) {
                                    KeyType.secp256r1 -> "curveId" to JsonPrimitive("NIST_P256")
                                    else -> "curveId" to JsonPrimitive(null)
                                  },
                              )),
                      "protectionMode" to JsonPrimitive("SOFTWARE"),
                  ))
              .toString()

      println("the body request is: $requestBody")
      val signature = signingRequest("POST", "/20180608/keys", host, requestBody)
      val keyData =
          http
              .post("https://$host/20180608/keys") {
                header(
                    "Authorization",
                    """Signature version="1",headers="date (request-target) host content-length content-type x-content-sha256",keyId="$keyId",algorithm="rsa-sha256",signature="$signature"""")

                header("Date", GMTDate().toHttpDate())
                header("Host", host)
                header("Content-Length", requestBody.length.toString())
                header("Accept", "application/json")
                header("Connection", "keep-alive")
                header("Content-Type", "application/json")
                header("x-content-sha256", calculateSHA256(requestBody))
                setBody(requestBody)
              }
              .ociJsonDataBody()

      val keyVersion = keyData["currentKeyVersion"]?.jsonPrimitive?.content ?: ""
      val OCIDkeyId = keyData["id"]?.jsonPrimitive?.content ?: ""

      val publicKey = getOCIPublicKey(OCIDkeyId, config.keyId, host, keyVersion)
      return OCIKey(
          config, OCIDkeyId, publicKey.decodeBase64Bytes(), ociKeyToKeyTypeMapping(keyType))
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun HttpResponse.ociJsonDataBody(): JsonObject {
      val baseMsg = { "OCI server (URL: ${this.request.url}) returned invalid response: " }

      if (!status.isSuccess())
          throw RuntimeException(baseMsg.invoke() + "non-success status: $status")

      return runCatching { this.body<JsonObject>() }
          .getOrElse {
            val bodyStr = this.bodyAsText()
            throw IllegalArgumentException(
                baseMsg.invoke() +
                    if (bodyStr == "") "empty response (instead of JSON data)"
                    else "invalid response: $bodyStr")
          }
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun signingRequest(
        method: String,
        restApi: String,
        host: String,
        requestBody: String?
    ): String {

      val date = GMTDate().toHttpDate()
      val requestTarget = "(request-target): ${method.lowercase()} $restApi"
      val hostHeader = "host: $host"
      val dateHeader = "date: $date"
      val signingString =
          when (method) {
            "GET" -> "$hostHeader\n$requestTarget\n$dateHeader"
            "POST",
            "PUT" -> {
              val contentTypeHeader = "content-type: application/json"
              val contentLengthHeader = "content-length: ${requestBody?.length ?: 0}"
              val sha256Header = "x-content-sha256: ${calculateSHA256(requestBody)}"
              "$dateHeader\n$requestTarget\n$hostHeader\n$contentLengthHeader\n$contentTypeHeader\n$sha256Header"
            }
            else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
          }

      val private_Key = "PRIVATE_KEY_HERE"
      val privateKeyPEM =
          private_Key
              .replace("-----BEGIN PRIVATE KEY-----", "")
              .replace("-----END PRIVATE KEY-----", "")
              .replace("\n", "")

      val decodedPrivateKeyBytes = Base64.decode(privateKeyPEM)

      val privateKeySpec = PKCS8EncodedKeySpec(decodedPrivateKeyBytes)

      val privateKey = KeyFactory.getInstance("RSA").generatePrivate(privateKeySpec)

      val signature = Signature.getInstance("SHA256withRSA")
      signature.initSign(privateKey)
      signature.update(signingString.toByteArray())

      //        val hashed = SHA256().digest(signingString.encodeToByteArray())
      //        val key = LocalKey.importPEM(private_Key).getOrThrow()
      //        println(key.exportPEM())
      //        val signed = key.signRaw(hashed)
      //        println("signed: ${Base64.encode(signed)}")
      //        println("signature old :${Base64.encode(signature.sign())}")

      return Base64.encode(signature.sign())
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun calculateSHA256(data: String?): String {
      if (data == null) return ""
      val digest = SHA256()
      val hash = digest.digest(data.encodeToByteArray())
      return Base64.encode(hash)
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun getKeys(keyId: String, host: String, tenancyOcid: String): Array<JsonObject> {
      val signature =
          signingRequest(
              "GET",
              "/20180608/keys?compartmentId=$tenancyOcid&sortBy=TIMECREATED&sortOrder=DESC",
              host,
              null)

      val response =
          http.get(
              "https://$host/20180608/keys?compartmentId=$tenancyOcid&sortBy=TIMECREATED&sortOrder=DESC") {
                header(
                    "Authorization",
                    """Signature version="1",headers="host (request-target) date",keyId="$keyId",algorithm="rsa-sha256",signature="$signature"""")
                header("Date", GMTDate().toHttpDate())
                header("Host", host)
                header("Accept", "application/json")
                header("Connection", "keep-alive")
              }

      return response.body<Array<JsonObject>>()
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun getOCIPublicKey(
        OCIDKeyID: String,
        keyId: String,
        host: String,
        keyVersion: String
    ): String {

      val signature =
          signingRequest("GET", "/20180608/keys/$OCIDKeyID/keyVersions/$keyVersion", host, null)

      val response =
          http.get("https://$host/20180608/keys/$OCIDKeyID/keyVersions/$keyVersion") {
            header(
                "Authorization",
                """Signature version="1",headers="host (request-target) date",keyId="$keyId",algorithm="rsa-sha256",signature="$signature"""")
            header("Date", GMTDate().toHttpDate())
            header("Host", host)
            header("Accept", "application/json")
            header("Connection", "keep-alive")
          }

      return response.body<JsonObject>()["publicKey"].toString()
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun deleteKey(
        OCIDKeyID: String,
        keyId: String,
        host: String
    ): Pair<HttpResponse, JsonObject> {
      val localDateTime = Clock.System.now()
      // add 7 days to the current date
      val timeOfDeletion = localDateTime.plus(7, DateTimeUnit.DAY, TimeZone.currentSystemDefault())

      val requestBody =
          JsonObject(
                  mapOf(
                      "timeOfDeletion" to JsonPrimitive(timeOfDeletion.toString()),
                  ))
              .toString()
      val signature =
          signingRequest(
              "POST", "/20180608/keys/$OCIDKeyID/actions/scheduleDeletion", host, requestBody)

      val response =
          http.post("https://$host/20180608/keys/$OCIDKeyID/actions/scheduleDeletion") {
            header(
                "Authorization",
                """Signature version="1",headers="date (request-target) host content-length content-type x-content-sha256",keyId="$keyId",algorithm="rsa-sha256",signature="$signature"""")

            header("Date", GMTDate().toHttpDate())
            header("Host", host)
            header("Content-Length", requestBody.length.toString())
            header("Accept", "application/json")
            header("Connection", "keep-alive")
            header("Content-Type", "application/json")
            header("x-content-sha256", calculateSHA256(requestBody))
            setBody(requestBody)
          }

      return response to response.body<JsonObject>()
    }

    val http = HttpClient {
      install(ContentNegotiation) { json() }
      defaultRequest { header(HttpHeaders.ContentType, ContentType.Application.Json) }
      install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.ALL
      }
    }
  }
}
