@file:OptIn(ExperimentalTime::class)

package id.walt.crypto.keys.oci

import id.walt.crypto.exceptions.KeyNotFoundException
import id.walt.crypto.exceptions.KeyTypeNotSupportedException
import id.walt.crypto.exceptions.SigningException
import id.walt.crypto.exceptions.VerificationException
import id.walt.crypto.keys.*
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto.utils.sha256WithRsa
import io.github.oshai.kotlinlogging.KotlinLogging
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val log = KotlinLogging.logger { }

@OptIn(ExperimentalJsExport::class)
@JsExport
@Suppress("TRANSIENT_IS_REDUNDANT")
@Serializable
@SerialName("oci-rest-api")
class OCIKeyRestApi(
    val config: OCIKeyMetadata,
    val id: String,

    /** public key as JWK */
    private var _publicKey: String? = null,
    private var _keyType: KeyType? = null,
) : Key() {

    // the OCID of the key (not the oci key-id)
    private val vaultKeyId = "${config.tenancyOcid}/${config.userOcid}/${config.fingerprint}"

    @Transient
    override var keyType: KeyType
        get() = _keyType!!
        set(value) {
            _keyType = value
        }

    override val hasPrivateKey: Boolean
        get() = true

    /** returns public key as PEM */
    private suspend fun retrievePublicKey(): Key {
        val keyData = getKeys(vaultKeyId, config.managementEndpoint, config.tenancyOcid, config.signingKeyPem)
        val key =
            keyData.firstOrNull { it["id"]?.jsonPrimitive?.content == id } ?: throw KeyNotFoundException(id)
        val keyVersion = getKeyVersion(id, vaultKeyId, config.managementEndpoint, config.signingKeyPem)
        val keyId = key["id"]?.jsonPrimitive?.content ?: throw KeyNotFoundException(id)

        return getOCIPublicKey(
            keyId, vaultKeyId, config.managementEndpoint, keyVersion, config.signingKeyPem
        )
    }

    override fun toString(): String = "[OCI ${keyType.name} key @ ${config.tenancyOcid}]"

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getKeyId(): String = getPublicKey().getKeyId()

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getThumbprint(): String = TODO("Not yet implemented")

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun exportJWK(): String = throw NotImplementedError("JWK export is not available for remote keys.")

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun exportJWKObject(): JsonObject = Json.parseToJsonElement(_publicKey!!).jsonObject

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun exportPEM(): String = throw NotImplementedError("PEM export is not available for remote keys.")


    // See: https://docs.oracle.com/en-us/iaas/api/#/en/key/release/datatypes/SignDataDetails
    @Transient
    private val ociSigningAlgorithm by lazy {
        when (keyType) {
            KeyType.secp256r1 -> "ECDSA_SHA_256"
            KeyType.secp384r1 -> "ECDSA_SHA_384"
            KeyType.secp521r1 -> "ECDSA_SHA_512"
            KeyType.RSA -> "SHA_256_RSA_PKCS1_V1_5"
            KeyType.RSA3072 -> "SHA_384_RSA_PKCS1_V1_5"
            KeyType.RSA4096 -> "SHA_512_RSA_PKCS1_V1_5"
            KeyType.secp256k1, KeyType.Ed25519 -> throw KeyTypeNotSupportedException(keyType.name)
        }
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray {
        return retry {
            val encodedMessage: String = SHA256().digest(plaintext).encodeBase64()

            val requestBody = JsonObject(
                mapOf(
                    "keyId" to JsonPrimitive(id),
                    "message" to JsonPrimitive(encodedMessage),
                    "signingAlgorithm" to JsonPrimitive(ociSigningAlgorithm),
                    "messageType" to JsonPrimitive("DIGEST")
                )
            ).toString()

            val signature = signingRequest(
                "POST", "/20180608/sign", config.cryptoEndpoint, requestBody, config.signingKeyPem
            )

            val response = http.post("https://${config.cryptoEndpoint}/20180608/sign") {
                header(
                    "Authorization",
                    """Signature version="1",headers="date (request-target) host content-length content-type x-content-sha256",keyId="$vaultKeyId",algorithm="rsa-sha256",signature="$signature""""
                )

                header("Host", host)
                header("x-content-sha256", calculateSHA256(requestBody))
                setBody(requestBody)
            }.ociJsonDataBody().jsonObject["signature"]?.jsonPrimitive?.content?.decodeFromBase64()
            response ?: throw SigningException("No signature returned from OCI.")
        }
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
        val appendedHeader = HashMap(headers).apply {
            put("alg", keyType.jwsAlg.toJsonElement())
        }

        val header = Json.encodeToString(appendedHeader).encodeToByteArray().encodeToBase64Url()
        val payload = plaintext.encodeToBase64Url()

        var rawSignature = signRaw("$header.$payload".encodeToByteArray())

        if (keyType in KeyTypes.EC_KEYS) {
            log.trace { "Converted DER to IEEE P1363 signature." }
            rawSignature = EccUtils.convertDERtoIEEEP1363(rawSignature)
        } else {
            log.trace { "Did not convert DER to IEEE P1363 signature." }
        }

        val encodedSignature = rawSignature.encodeToBase64Url()
        val jws = "$header.$payload.$encodedSignature"

        return jws
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?): Result<ByteArray> {
        check(detachedPlaintext != null) { "An detached plaintext is needed." }

        val requestBody = JsonObject(
            mapOf(
                "keyId" to JsonPrimitive(id),
                "message" to JsonPrimitive(detachedPlaintext.encodeBase64()),
                "signature" to JsonPrimitive(signed.encodeBase64()),
                "signingAlgorithm" to JsonPrimitive(ociSigningAlgorithm)
            )
        ).toString()

        val signature = signingRequest(
            "POST", "/20180608/verify", config.cryptoEndpoint, requestBody, config.signingKeyPem
        )

        val response = http.post("https://${config.cryptoEndpoint}/20180608/verify") {
            header(
                "Authorization",
                """Signature version="1",headers="date (request-target) host content-length content-type x-content-sha256",keyId="$vaultKeyId",algorithm="rsa-sha256",signature="$signature""""
            )
            header("Host", host)
            header("x-content-sha256", calculateSHA256(requestBody))
            setBody(requestBody)
        }.ociJsonDataBody().jsonObject["isSignatureValid"]?.jsonPrimitive?.boolean ?: false
        return if (response) Result.success(detachedPlaintext)
        else Result.failure(VerificationException("Signature is not valid"))
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        val (header, payload, signature) = signedJws.decodeJws(withSignature = true)
        val headers: Map<String, JsonElement> = header.toMap()
        headers["alg"]?.let {
            val algValue = it.jsonPrimitive.content
            check(algValue == keyType.jwsAlg) {
                "Invalid key algorithm for JWS: JWS has $algValue, key is ${keyType.jwsAlg}!"
            }
        }

        val signable = "$header.$payload".encodeToByteArray()
        return verifyRaw(signature.decodeFromBase64Url(), signable).map {
            val verifiedPayload = it.decodeToString().substringAfter(".").decodeFromBase64Url().decodeToString()
            Json.parseToJsonElement(verifiedPayload)
        }
    }

    @Transient
    private var backedKey: Key? = null

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getPublicKey(): Key = backedKey ?: when {
        _publicKey != null -> _publicKey!!.let { JWKKey.importJWK(it).getOrThrow() }
        else -> retrievePublicKey()
    }.also { newBackedKey -> backedKey = newBackedKey }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getPublicKeyRepresentation(): ByteArray = TODO("Not yet implemented")

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getMeta(): OciKeyMeta = OciKeyMeta(
        keyId = id,
        keyVersion = getKeyVersion(id, vaultKeyId, config.managementEndpoint, config.signingKeyPem)
    )

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun deleteKey(): Boolean {
        TODO("Not yet implemented")
    }

    companion object {

        private fun keyTypeToOciKeyMapping(type: KeyType) = when (type) {
            KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1 -> "ECDSA"
            KeyType.RSA, KeyType.RSA3072, KeyType.RSA4096 -> "RSA"
            KeyType.Ed25519, KeyType.secp256k1 -> throw KeyTypeNotSupportedException(type.name)
        }

        private fun ociKeyToKeyTypeMapping(type: String) = when (type) {
            "ECDSA" -> KeyType.secp256r1 // TODO: other secp types
            "RSA" -> KeyType.RSA
            else -> throw KeyTypeNotSupportedException(type)
        }

        // See: https://docs.oracle.com/en-us/iaas/api/#/en/key/release/datatypes/KeyShape
        @JvmBlocking
        @JvmAsync
        @JsPromise
        @JsExport.Ignore
        suspend fun generateKey(type: KeyType, config: OCIKeyMetadata): OCIKeyRestApi {
            return retry {
                val keyType = keyTypeToOciKeyMapping(type)
                val vaultKeyId = "${config.tenancyOcid}/${config.userOcid}/${config.fingerprint}"
                val host = config.managementEndpoint
                val length = when (type) {
                    KeyType.secp256r1 -> 32
                    KeyType.secp384r1 -> 48
                    KeyType.secp521r1 -> 66
                    KeyType.RSA -> 256
                    KeyType.RSA3072 -> 384
                    KeyType.RSA4096 -> 512
                    KeyType.Ed25519, KeyType.secp256k1 -> throw KeyTypeNotSupportedException(type.name)
                }
                val requestBody = JsonObject(
                    mapOf(
                        "compartmentId" to JsonPrimitive(config.compartmentOcid),
                        "displayName" to JsonPrimitive("WaltID"),
                        "keyShape" to JsonObject(
                            mapOf(
                                "algorithm" to JsonPrimitive(keyType),
                                "length" to JsonPrimitive(length),
                                when (type) {
                                    KeyType.secp256r1 -> "curveId" to JsonPrimitive("NIST_P256")
                                    KeyType.secp384r1 -> "curveId" to JsonPrimitive("NIST_P384")
                                    KeyType.secp521r1 -> "curveId" to JsonPrimitive("NIST_P521")
                                    else -> "curveId" to JsonNull
                                },
                            )
                        ),
                        "protectionMode" to JsonPrimitive("SOFTWARE"),
                    )
                ).toString()

                val signature = signingRequest("POST", "/20180608/keys", host, requestBody, config.signingKeyPem)
                val keyData = http.post("https://$host/20180608/keys") {
                    header(
                        "Authorization",
                        """Signature version="1",headers="date (request-target) host content-length content-type x-content-sha256",keyId="$vaultKeyId",algorithm="rsa-sha256",signature="$signature""""
                    )
                    header("Host", host)
                    header("x-content-sha256", calculateSHA256(requestBody))
                    setBody(requestBody)
                }.ociJsonDataBody()

                val keyVersion = keyData["currentKeyVersion"]?.jsonPrimitive?.content ?: ""
                val OCIDkeyId = keyData["id"]?.jsonPrimitive?.content ?: ""

                val publicKey = getOCIPublicKey(OCIDkeyId, vaultKeyId, host, keyVersion, config.signingKeyPem)
                OCIKeyRestApi(config, OCIDkeyId, publicKey.exportJWK(), ociKeyToKeyTypeMapping(keyType))
            }
        }

        @JsExport.Ignore
        private suspend fun getKeyVersion(
            ocidKeyId: String, keyId: String, host: String, signingKey: String?
        ): String {
            val signature = signingRequest("GET", "/20180608/keys/$ocidKeyId", host, null, signingKey)

            val response = http.get("https://$host/20180608/keys/$ocidKeyId") {
                header(
                    "Authorization",
                    """Signature version="1",headers="host (request-target) date",keyId="$keyId",algorithm="rsa-sha256",signature="$signature""""
                )
                header("Host", host)
            }

            return response.body<JsonObject>()["currentKeyVersion"]?.jsonPrimitive?.content ?: ""
        }

        private suspend fun HttpResponse.ociJsonDataBody(): JsonObject {
            val baseMsg = { "OCI server (URL: ${this.request.url}) returned invalid response: " }

            if (!status.isSuccess()) throw IllegalStateException(
                baseMsg.invoke() + "non-success status: $status - ${this.bodyAsText()}"
            )

            return runCatching { this.body<JsonObject>() }.getOrElse {
                val bodyStr = this.bodyAsText()
                throw IllegalArgumentException(
                    baseMsg.invoke() + if (bodyStr == "") "empty response (instead of JSON data)"
                    else "invalid response: $bodyStr"
                )
            }
        }

        @OptIn(ExperimentalEncodingApi::class)
        private fun signingRequest(
            method: String, restApi: String, host: String, requestBody: String?, signingKey: String? // = null
        ): String {
            val date = GMTDate().toHttpDate()
            val requestTarget = "(request-target): ${method.lowercase()} $restApi"
            val hostHeader = "host: $host"
            val dateHeader = "date: $date"
            val signingString = when (method) {
                "GET" -> "$hostHeader\n$requestTarget\n$dateHeader"
                "POST", "PUT" -> {
                    val contentTypeHeader = "content-type: application/json"
                    val contentLengthHeader = "content-length: ${requestBody?.length ?: 0}"
                    val sha256Header = "x-content-sha256: ${calculateSHA256(requestBody)}"
                    "$dateHeader\n$requestTarget\n$hostHeader\n$contentLengthHeader\n$contentTypeHeader\n$sha256Header"
                }

                else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
            }

            val privateOciApiKey = signingKey
                ?: throw KeyNotFoundException(
                    message = "No private key provided for OCI signing. Please provide a private key."
                )

            return Base64.encode(sha256WithRsa(privateOciApiKey, signingString.encodeToByteArray()))
        }

        @OptIn(ExperimentalEncodingApi::class)
        private fun calculateSHA256(data: String?): String {
            if (data == null) return ""
            val digest = SHA256()
            val hash = digest.digest(data.encodeToByteArray())
            return Base64.encode(hash)
        }

        @JsExport.Ignore
        private suspend fun getKeys(
            keyId: String, host: String, tenancyOcid: String, signingKey: String?
        ): Array<JsonObject> {
            val signature = signingRequest(
                "GET", "/20180608/keys?compartmentId=$tenancyOcid&sortBy=TIMECREATED&sortOrder=DESC", host, null, signingKey
            )

            val response = http.get(
                "https://$host/20180608/keys?compartmentId=$tenancyOcid&sortBy=TIMECREATED&sortOrder=DESC"
            ) {
                header(
                    "Authorization",
                    """Signature version="1",headers="host (request-target) date",keyId="$keyId",algorithm="rsa-sha256",signature="$signature""""
                )
                header("Host", host)
            }

            return response.body<Array<JsonObject>>()
        }

        @JsExport.Ignore
        private suspend fun getOCIPublicKey(
            OCIDKeyID: String, keyId: String, host: String, keyVersion: String, signingKeyPem: String?
        ): Key {
            val signature = signingRequest(
                "GET", "/20180608/keys/$OCIDKeyID/keyVersions/$keyVersion", host, null, signingKeyPem
            )

            val response = http.get("https://$host/20180608/keys/$OCIDKeyID/keyVersions/$keyVersion") {
                header(
                    "Authorization",
                    """Signature version="1",headers="host (request-target) date",keyId="$keyId",algorithm="rsa-sha256",signature="$signature""""
                )
                header("Host", host)
            }

            val publicKeyPem = response.body<JsonObject>()["publicKey"]?.jsonPrimitive?.content
                ?: throw KeyNotFoundException("No public key returned from OCI for key ID: $OCIDKeyID and version: $keyVersion")
            val publicKey = JWKKey.importPEM(publicKeyPem).getOrThrow()

            return publicKey
        }

        @JvmBlocking
        @JvmAsync
        @JsPromise
        @JsExport.Ignore
        suspend fun deleteKey(
            OCIDKeyID: String, keyId: String, host: String, signingKeyPem: String
        ): Pair<HttpResponse, JsonObject> {
            return retry {
                val localDateTime = Clock.System.now()
                val timeOfDeletion = localDateTime.plus(5, DateTimeUnit.MINUTE, TimeZone.currentSystemDefault())

                val requestBody = JsonObject(
                    mapOf(
                        "timeOfDeletion" to JsonPrimitive(timeOfDeletion.toString()),
                    )
                ).toString()
                val signature = signingRequest(
                    "POST", "/20180608/keys/$OCIDKeyID/actions/scheduleDeletion", host, requestBody, signingKeyPem
                )

                val response = http.post("https://$host/20180608/keys/$OCIDKeyID/actions/scheduleDeletion") {
                    header(
                        "Authorization",
                        """Signature version="1",headers="date (request-target) host content-length content-type x-content-sha256",keyId="$keyId",algorithm="rsa-sha256",signature="$signature""""
                    )
                    header("Host", host)
                    header("x-content-sha256", calculateSHA256(requestBody))
                    setBody(requestBody)
                }

                response to response.body<JsonObject>()
            }
        }

        private val http = HttpClient {
            install(ContentNegotiation) { json() }
            defaultRequest {
                header(HttpHeaders.Date, GMTDate().toHttpDate())
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json)
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.ALL
            }
        }
    }
}


private suspend fun <T> retry(retriesLeft: Int = 3, currentTry: Int = 1, block: suspend () -> T): T =
    runCatching { block.invoke() }.fold(
        onSuccess = { it },
        onFailure = { error ->
            when {
                retriesLeft <= 0 -> throw IllegalStateException("Failed after $currentTry retries: ${error.message}", error)
                else -> retry(retriesLeft - 1, currentTry + 1, block)
            }
        })


