package id.walt.crypto.keys

import id.walt.crypto.keys.TSEKey.Companion.tseJsonDataBody
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
import io.ktor.utils.io.core.*
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.*
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

class OCIKey(
    val OCIConfig: OCIKeyConfig,
    private var _publicKey: ByteArray? = null,
    private var _keyType: KeyType? = null,


) : Key() {

  @OptIn(DelicateCoroutinesApi::class)
  private inline fun <T> lazySuspended(
      crossinline block: suspend CoroutineScope.() -> T
  ): Deferred<T> =
      GlobalScope.async(Dispatchers.Unconfined, start = CoroutineStart.LAZY) {
        block.invoke(this)
        // retrieveKeyType()
      }


    @Transient
    //val retrievedKeyType = lazySuspended { retrieveKeyType() }
    val retrievedKeyType = lazySuspended { retrieveKeyType() }

    @Transient
    val retrievedPublicKey = lazySuspended { retrievePublicKey() }
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


  override suspend fun getKeyId(): String = OCIConfig.OCIDKeyID

  override suspend fun getThumbprint(): String {
    TODO("Not yet implemented")
  }

  override suspend fun exportJWK(): String {
    TODO("Not yet implemented")
  }

  override suspend fun exportJWKObject(): JsonObject {
    TODO("Not yet implemented")
  }

  override suspend fun exportPEM(): String {
    TODO("Not yet implemented")
  }

  override suspend fun signRaw(plaintext: ByteArray): Any {
    val encodedMessage = plaintext.encodeBase64()

    val requestBody =
        JsonObject(
                mapOf(
                    "keyId" to JsonPrimitive(OCIConfig.OCIDKeyID),
                    "message" to JsonPrimitive(encodedMessage),
                    "signingAlgorithm" to JsonPrimitive("SHA_384_RSA_PKCS_PSS"),
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

  override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String {
      val header = Json.encodeToString(mutableMapOf(
          "typ" to "JWT",
          "alg" to keyType.jwsAlg(),
      ).apply { putAll(headers) }).encodeToByteArray().encodeToBase64Url()

      val payload = plaintext.encodeToBase64Url()

      val signable = "$header.$payload"

      val signatureBase64 = signRaw(signable.encodeToByteArray()) as String
      val signatureBase64Url = signatureBase64.base64toBase64Url()

      return "$signable.$signatureBase64Url"
  }

  override suspend fun verifyRaw(
      signed: ByteArray,
      detachedPlaintext: ByteArray?
  ): Result<ByteArray> {
      check(detachedPlaintext != null) { "An detached plaintext is needed." }

      val requestBody =
          JsonObject(
              mapOf(
                  "keyId" to JsonPrimitive(OCIConfig.OCIDKeyID),
                  "message" to JsonPrimitive(detachedPlaintext.encodeBase64()),
                  "signature" to JsonPrimitive(signed.encodeBase64()),
                  "signingAlgorithm" to JsonPrimitive("SHA_384_RSA_PKCS_PSS"),
              ))
              .toString()
      val signature = signingRequest("POST", "/20180608/verify", OCIConfig.cryptoEndpoint, requestBody)

      val response =
          http.post("https://${OCIConfig.cryptoEndpoint}/20180608/verify") {
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
          }.ociJsonDataBody().jsonObject["isSignatureValid"]?.jsonPrimitive?.boolean
                ?: false
      return if (response) Result.success(signed) else Result.failure(Exception("Signature is not valid"))
  }


  @OptIn(ExperimentalJsExport::class)
  override suspend fun verifyJws(signedJws: String): Result<JsonObject> {
      val parts = signedJws.split(".")
      check(parts.size == 3) { "Invalid JWT part count: ${parts.size} instead of 3" }
      val header = parts[0]
      val headers: Map<String, JsonElement> = Json.decodeFromString(header.base64UrlDecode().decodeToString())
      headers["alg"]?.let {
          val algValue = it.jsonPrimitive.content
          check(algValue == keyType.jwsAlg()) { "Invalid key algorithm for JWS: JWS has $algValue, key is ${keyType.jwsAlg()}!" }
      }

      val payload = parts[1]
      val signature = parts[2].base64UrlDecode()

      val signable = "$header.$payload".encodeToByteArray()
      return verifyRaw(signature, signable).map {
           val verifiedPayload = it.decodeToString().substringAfter(".").base64UrlDecode().decodeToString()
              Json.parseToJsonElement(verifiedPayload).jsonObject
      }
  }

  override suspend fun getPublicKey(): Key {
      println("Getting public key: $keyType")

      return LocalKey.importRawPublicKey(
          type = keyType,
          rawPublicKey = getBackingPublicKey(),
          metadata = LocalKeyMetadata(), // todo: import with explicit `keySize`
      )
  }

  override suspend fun getPublicKeyRepresentation(): ByteArray {
    TODO("Not yet implemented")
  }

  companion object {

      private fun keyTypeToOciKeyMapping(type: KeyType) = when (type) {
          KeyType.Ed25519 -> throw IllegalArgumentException("Not supported: $type")
          KeyType.secp256r1 -> "ECDSA"
          KeyType.RSA -> "RSA"
          KeyType.secp256k1 -> throw IllegalArgumentException("Not supported: $type")
      }

      private fun ociKeyToKeyTypeMapping(type: String) = when (type) {
          "ed25519" -> throw IllegalArgumentException("Not supported: $type")
          "ECDSA" -> KeyType.secp256r1
          "RSA" -> KeyType.RSA
          else -> throw IllegalArgumentException("Not supported: $type")
      }

      suspend fun generateKey(type: KeyType, config: OCIKeyConfig): OCIKey {
          val keyType = keyTypeToOciKeyMapping(type)
          val keyId = config.keyId
          println("keyId: $keyId")
          val host = config.managementEndpoint
          println("host: $host")
          val requestBody =
              JsonObject(
                  mapOf(
                      "compartmentId" to JsonPrimitive(config.tenancyOcid),
                      "displayName" to JsonPrimitive("WaltID"),

                      "keyShape" to JsonObject(
                          mapOf(
                              "algorithm" to JsonPrimitive(keyTypeToOciKeyMapping(type)),
                              "length" to JsonPrimitive(256),
                          )),
                      "protectionMode" to JsonPrimitive("SOFTWARE"),

              )).toString()

          println("the body request is: $requestBody")
          val signature = signingRequest("POST", "/20180608/keys", host, requestBody)
          val keyData = http.post("https://$host/20180608/keys") {
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
          }.ociJsonDataBody()


          val keyVersion = keyData["currentKeyVersion"]?.jsonPrimitive?.content ?: ""
          val OCIDkeyId = keyData["id"]?.jsonPrimitive?.content ?: ""

          val publicKey = getPublicKey(OCIDkeyId, config.keyId, host, keyVersion)
          return OCIKey(config, publicKey.decodeBase64Bytes(), ociKeyToKeyTypeMapping(keyType))

      }


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



    @OptIn(ExperimentalEncodingApi::class)
    fun signingRequest(
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

      val privateKeycon = "PRIVATE_KEY_HERE"
      val privateKeyPEM =
          privateKeycon
              .replace("-----BEGIN PRIVATE KEY-----", "")
              .replace("-----END PRIVATE KEY-----", "")
              .replace("\n", "")

      val decodedPrivateKeyBytes = Base64.decode(privateKeyPEM)

      val privateKeySpec = PKCS8EncodedKeySpec(decodedPrivateKeyBytes)

      val privateKey = KeyFactory.getInstance("RSA").generatePrivate(privateKeySpec)

      val signature = Signature.getInstance("SHA256withRSA")
      signature.initSign(privateKey)
      signature.update(signingString.toByteArray())
      val signedBytes = signature.sign()
      val signedString = Base64.encode(signedBytes)

      return signedString
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun calculateSHA256(data: String?): String {
      if (data == null) return ""
      val digest = java.security.MessageDigest.getInstance("SHA-256")
      val hash = digest.digest(data.toByteArray(io.ktor.utils.io.charsets.Charsets.UTF_8))
      return Base64.encode(hash)
    }

    suspend fun getKeys(keyId: String, host: String): Array<JsonObject> {
      val signature =
          signingRequest(
              "GET",
              "/20180608/keys?compartmentId=ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q&sortBy=TIMECREATED&sortOrder=DESC",
              host,
              null)

      val response =
          http.get(
              "https://$host/20180608/keys?compartmentId=ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q&sortBy=TIMECREATED&sortOrder=DESC") {
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




    suspend fun getPublicKey(
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

//suspend fun main() {
//
//  val Config =
//      OCIKeyConfig(
//          "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q",
//          "ocid1.user.oc1..aaaaaaaaxjkkfjqxdqk7ldfjrxjmacmbi7sci73rbfiwpioehikavpbtqx5q",
//          "bb:d4:4b:0c:c8:3a:49:15:7f:87:55:d5:2b:7e:dd:bc",
//          "ens3g6m3aabyo-management.kms.eu-frankfurt-1.oraclecloud.com",
//          "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q/ocid1.user.oc1..aaaaaaaaxjkkfjqxdqk7ldfjrxjmacmbi7sci73rbfiwpioehikavpbtqx5q/bb:d4:4b:0c:c8:3a:49:15:7f:87:55:d5:2b:7e:dd:bc",
//          "ocid1.key.oc1.eu-frankfurt-1.ens3g6m3aabyo.abtheljssgbbeedlwujnvfyqxfjrfhtfdnlwioevpvjoqj6675n7twqzzixq",
//          "ens3g6m3aabyo-crypto.kms.eu-frankfurt-1.oraclecloud.com",
//      )
//
//
//
//      val key = OCIKey.generateKey(KeyType.RSA, Config)
//
//      val message = JsonObject(
//          mapOf(
//              "sub" to JsonPrimitive("16bb17e0-e733-4622-9384-122bc2fc6290"),
//              "iss" to JsonPrimitive("http://localhost:3000"),
//              "aud" to JsonPrimitive("TOKEN"),
//          )
//      )
//    key.signRaw(message.toString().encodeToByteArray())
//          val signedData = key.signRaw(message.toString().encodeToByteArray()) as String
//          println("Signed Data: $signedData")
//          val verification = key.verifyRaw(
//              signedData.decodeBase64Bytes(),
//              message.toString().encodeToByteArray()
//          )
//          println("Verification: ${verification.isSuccess}")
//
//}


