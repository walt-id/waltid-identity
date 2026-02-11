package id.walt.crypto.keys.tse

import id.walt.crypto.exceptions.*
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.TseKeyMeta
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64toBase64Url
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.random.Random

private val logger = KotlinLogging.logger { }

@OptIn(ExperimentalJsExport::class)
@JsExport
@Suppress("TRANSIENT_IS_REDUNDANT")
@Serializable
@SerialName("tse")
// Works with the Hashicorp Transit Secret Engine
class TSEKey(
    val server: String,
    private val auth: TSEAuth? = null,
    @Deprecated("use TSEAuth in `auth` instead") private val accessKey: String? = null,
    private val namespace: String? = null,
    val id: String,
    private var _publicKey: ByteArray? = null,
    private var _keyType: KeyType? = null,
) : Key() {

    @Suppress("DEPRECATION")
    @Transient
    private val effectiveAuth: TSEAuth = auth ?: TSEAuth(
        accessKey = accessKey ?: throw IllegalArgumentException("Either auth or accessKey must be provided")
    )

    private suspend fun httpRequest(
        method: HttpMethod = HttpMethod.Get,
        url: String = "keys/$id",
        body: Any? = null
    ): HttpResponse {
        return http.request {
            this.url("$server/$url")
            this.method = method

            header("X-Vault-Token", effectiveAuth.getCachedLogin(server))
            namespace?.let { header("X-Vault-Namespace", namespace) }

            body?.let { this.setBody(body) }
        }
    }

    @Transient
    override var keyType: KeyType
        get() = _keyType!!
        set(value) {
            _keyType = value
        }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun init() {
        if (_keyType == null) _keyType = retrieveKeyType()
    }

    private suspend fun getBackingPublicKey(): ByteArray = _publicKey ?: retrievePublicKey().also { _publicKey = it }

    private fun throwTSEError(msg: String): Nothing =
        throw RuntimeException("Invalid TSE server ($server) response: $msg")

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun retrievePublicKey(): ByteArray {
        logger.debug { "Retrieving public key: ${this.id}" }

        val keyData = httpRequest(HttpMethod.Get, "keys/$id")
            .tseJsonDataBody().jsonObject["keys"]?.jsonObject ?: throw KeyNotFoundException(id = id)

        val keyStr = keyData["1"]?.jsonObject?.get("public_key")?.jsonPrimitive?.content
            ?: throw KeyNotFoundException(id = id)

        logger.debug { "Public key PEM-encoded string is: $keyStr" }

        val base64PublicKey = keyStr.lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
            .replace("\\s".toRegex(), "") // Remove all whitespace just in case

        logger.debug { "Base64 public key is: $base64PublicKey" }

        return Base64.decode(base64PublicKey)
    }

    private suspend fun retrieveKeyType(): KeyType = tseKeyToKeyTypeMapping(
        httpRequest()
            .tseJsonDataBody().jsonObject["type"]?.jsonPrimitive?.content ?: throwTSEError("No type in data response")
    )

    override val hasPrivateKey: Boolean
        get() = true

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
    override suspend fun exportJWK(): String = throw IllegalArgumentException("The private key should not be exposed.")

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun exportJWKObject(): JsonObject =
        throw IllegalArgumentException("The private key should not be exposed.")

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun exportPEM(): String = throw IllegalArgumentException("The private key should not be exposed.")

    @OptIn(ExperimentalEncodingApi::class)
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray {
        val body = mapOf("input" to plaintext.encodeBase64())
        val signatureBase64 = httpRequest(HttpMethod.Post, "sign/$id", body)
            .tseJsonDataBody().jsonObject["signature"]?.jsonPrimitive?.content?.removePrefix("vault:v1:")
            ?: throw MissingSignatureException(
                "No signature returned from TSE server"
            )

        return Base64.decode(signatureBase64)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
        val header = Json.encodeToString(
            mutableMapOf(
                "typ" to "JWT".toJsonElement(),
                "alg" to keyType.jwsAlg.toJsonElement(),
            ).apply { putAll(headers) }).encodeToByteArray().encodeToBase64Url()

        val payload = plaintext.encodeToBase64Url()

        val signable = "$header.$payload"

        val signatureBase64 = Base64.encode(signRaw(signable.encodeToByteArray()) as ByteArray)
        val signatureBase64Url = signatureBase64.base64toBase64Url()

        return "$signable.$signatureBase64Url"
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?): Result<ByteArray> {
        check(detachedPlaintext != null) { "An detached plaintext is needed." }

        val body = mapOf(
            "input" to detachedPlaintext.encodeBase64(), "signature" to "vault:v1:${signed.encodeBase64()}"
        )
        val valid = httpRequest(HttpMethod.Post, "verify/$id", body)
            .tseJsonDataBody().jsonObject["valid"]?.jsonPrimitive?.boolean
            ?: throw MissingSignatureException(
                "No signature returned from TSE server"
            )

        return if (valid) Result.success(detachedPlaintext)
        else Result.failure(VerificationException("Signature verification failed"))
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        val parts = signedJws.split(".")
        check(parts.size == 3) { "Invalid JWT part count: ${parts.size} instead of 3" }

        val header = parts[0]
        val headers: Map<String, JsonElement> = Json.decodeFromString(header.decodeFromBase64Url().decodeToString())
        headers["alg"]?.let {
            val algValue = it.jsonPrimitive.content
            check(algValue == keyType.jwsAlg) { "Invalid key algorithm for JWS: JWS has $algValue, key is ${keyType.jwsAlg}!" }
        }

        val payload = parts[1]
        val signature = parts[2].decodeFromBase64Url()

        val signable = "$header.$payload".encodeToByteArray()

        return verifyRaw(signature, signable).map {
            val verifiedPayload = it.decodeToString().substringAfter(".").decodeFromBase64Url().decodeToString()
            Json.parseToJsonElement(verifiedPayload).jsonObject
        }
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun getEncodedPublicKey(): String = // TODO add to base Key
        lazyOf(
            httpRequest()
                .tseJsonDataBody().jsonObject["keys"]?.jsonObject?.get("1")?.jsonObject?.get("public_key")?.jsonPrimitive?.content
                ?: throw KeyNotFoundException(message = "No keys/1/public_key in data response")
        ).value

    @OptIn(ExperimentalEncodingApi::class)
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getPublicKey(): Key {
        logger.debug { "Getting public key: $keyType" }

        return JWKKey.importRawPublicKey(
            type = keyType,
            rawPublicKey = getBackingPublicKey(),
            metadata = null,
        )
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getPublicKeyRepresentation(): ByteArray = getBackingPublicKey()

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getMeta(): TseKeyMeta = TseKeyMeta(getKeyId())

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun deleteKey(): Boolean {
        TODO("Not yet implemented")
    }

    override fun toString(): String = "[TSE ${keyType.name} key @ $server]"

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun delete() {
        httpRequest(HttpMethod.Post, "keys/$id/config", mapOf("deletion_allowed" to true))
        httpRequest(HttpMethod.Delete)
    }

    companion object : TSEKeyCreator {
        /**
        See https://developer.hashicorp.com/vault/api-docs/secret/transit#type
        for key types.
         */
        private fun keyTypeToTseKeyMapping(type: KeyType) = when (type) {
            KeyType.Ed25519 -> "ed25519"
            KeyType.secp256r1 -> "ecdsa-p256"
            KeyType.secp384r1 -> "ecdsa-p384"
            KeyType.secp521r1 -> "ecdsa-p521"
            KeyType.RSA -> "rsa-2048"
            KeyType.RSA3072 -> "rsa-3072"
            KeyType.RSA4096 -> "rsa-4096"
            KeyType.secp256k1 -> throw KeyTypeNotSupportedException(type.name)
        }

        /**
        See https://developer.hashicorp.com/vault/api-docs/secret/transit#type
        for key types.
         */
        private fun tseKeyToKeyTypeMapping(type: String) = when (type) {
            "ed25519" -> KeyType.Ed25519
            "ecdsa-p256" -> KeyType.secp256r1
            "ecdsa-p384" -> KeyType.secp384r1
            "ecdsa-p521" -> KeyType.secp521r1
            "rsa-2048" -> KeyType.RSA
            "rsa-3072" -> KeyType.RSA3072
            "rsa-4096" -> KeyType.RSA4096
            else -> throw KeyTypeNotSupportedException(type)
        }

        @JvmBlocking
        @JvmAsync
        @JsPromise
        @JsExport.Ignore
        suspend fun HttpResponse.tseJsonDataBody(): JsonObject {
            val baseMsg = { "TSE server (URL: ${this.request.url}) returned invalid response: " }

            if (!status.isSuccess()) throw RuntimeException(baseMsg.invoke() + "non-success status: $status")

            return runCatching { this.body<JsonObject>() }.getOrElse {
                val bodyStr = this.bodyAsText()
                throw IllegalArgumentException(baseMsg.invoke() + if (bodyStr == "") "empty response (instead of JSON data)" else "invalid response: $bodyStr")
            }["data"]?.jsonObject
                ?: throw IllegalArgumentException(baseMsg.invoke() + "no data in response: ${this.bodyAsText()}")
        }

        @JvmBlocking
        @JvmAsync
        @JsPromise
        @JsExport.Ignore
        override suspend fun generate(type: KeyType, metadata: TSEKeyMetadata): TSEKey {

            logger.debug { "Generating TSE key ($type)" }

            val keyData = http.post("${metadata.server}/keys/k${metadata.id ?: Random.nextInt()}") {
                header("X-Vault-Token", metadata.auth.getCachedLogin(metadata.server))
                metadata.namespace?.let { header("X-Vault-Namespace", metadata.namespace) }
                setBody(mapOf("type" to keyTypeToTseKeyMapping(type)))
            }.tseJsonDataBody()

            val keyName = keyData["name"]?.jsonPrimitive?.content
                ?: throw TSEError.MissingKeyNameException()

            val publicKey = (keyData["keys"]
                ?: throw TSEError.MissingKeyDataException()).jsonObject["1"]!!.jsonObject["public_key"]!!.jsonPrimitive.content.decodeBase64Bytes()

            return TSEKey(
                server = metadata.server,
                auth = metadata.auth,
                namespace = metadata.namespace,
                id = keyName,
                _publicKey = publicKey,
                _keyType = type
            ).apply { init() }
        }

        private val http = HttpClient {
            install(ContentNegotiation) {
                json()
            }
            defaultRequest {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.ALL
            }
        }
    }
}
