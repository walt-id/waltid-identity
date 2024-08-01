package id.walt.crypto.keys.tse

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.TseKeyMeta
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.base64toBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JwsUtils.jwsAlg
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
import kotlinx.coroutines.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
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
    private val effectiveAuth: TSEAuth = auth ?: TSEAuth(accessKey = accessKey ?: throw IllegalArgumentException("Either auth or accessKey must be provided"))

    @OptIn(DelicateCoroutinesApi::class)
    private inline fun <T> lazySuspended(
        crossinline block: suspend CoroutineScope.() -> T,
    ): Deferred<T> = GlobalScope.async(Dispatchers.Unconfined, start = CoroutineStart.LAZY) {
        block.invoke(this)
        //retrieveKeyType()
    }


    private suspend fun httpRequest(method: HttpMethod = HttpMethod.Get, url: String = "keys/$id", body: Any? = null): HttpResponse {
        return http.request {
            this.url("$server/$url")
            this.method = method

            header("X-Vault-Token", effectiveAuth.getCachedLogin(server))
            namespace?.let { header("X-Vault-Namespace", namespace) }

            body?.let { this.setBody(body) }
        }
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    @Transient
    //val retrievedKeyType = lazySuspended { retrieveKeyType() }
    val retrievedKeyType = lazySuspended { retrieveKeyType() }

    @Suppress("NON_EXPORTABLE_TYPE")
    @Transient
    val retrievedPublicKey = lazySuspended { retrievePublicKey() }

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
        if (_keyType == null) _keyType = coroutineScope { retrievedKeyType.await() }
    }

    private suspend fun getBackingPublicKey(): ByteArray = _publicKey ?: retrievedPublicKey.await()

    private fun throwTSEError(msg: String): Nothing =
        throw RuntimeException("Invalid TSE server ($server) response: $msg")

    private suspend fun retrievePublicKey(): ByteArray {
        logger.debug { "Retrieving public key: ${this.id}" }
        val keyData = httpRequest(HttpMethod.Get, "keys/$id")
            .tseJsonDataBody().jsonObject["keys"]?.jsonObject ?: throwTSEError("No keys in data response")

        // TO\\DO: try this
        val keyStr = keyData["1"]?.jsonObject?.get("public_key")?.jsonPrimitive?.content
            ?: throwTSEError("No data/keys/1/publicKey returned: $keyData")

        logger.debug { "Key string is: $keyStr" }

        return keyStr.decodeBase64Bytes()
    }

    private suspend fun retrieveKeyType(): KeyType = tseKeyToKeyTypeMapping(
        httpRequest()
            .tseJsonDataBody().jsonObject["type"]?.jsonPrimitive?.content ?: throwTSEError("No type in data response")
    )

    override val hasPrivateKey: Boolean
        get() = TODO("Not yet implemented")

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
//    override suspend fun getKeyId(): String = id
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

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun signRaw(plaintext: ByteArray): Any {
        val body = mapOf("input" to plaintext.encodeBase64())
        val signatureBase64 = httpRequest(HttpMethod.Post, "sign/$id", body)
            .tseJsonDataBody().jsonObject["signature"]?.jsonPrimitive?.content?.removePrefix("vault:v1:") ?: throwTSEError(
            "No signature in data response"
        )

        return signatureBase64
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
        val header = Json.encodeToString(mutableMapOf(
            "typ" to "JWT".toJsonElement(),
            "alg" to keyType.jwsAlg().toJsonElement(),
        ).apply { putAll(headers) }).encodeToByteArray().encodeToBase64Url()

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
    override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?): Result<ByteArray> {
        /*val localPublicKey = when (keyType) {
            KeyType.Ed25519 -> JWKKey.importRawPublicKey(
                type = keyType,
                rawPublicKey = getBackingPublicKey(),
                metadata = JWKKeyMetadata() // todo: explicit `keySize`
            )

            KeyType.RSA, KeyType.secp256r1 -> JWKKey.importPEM(getEncodedPublicKey()).getOrThrow()
            KeyType.secp256k1 -> throw IllegalArgumentException("Type not supported for TSE: $keyType")
        }*/

        check(detachedPlaintext != null) { "An detached plaintext is needed." }

        val body = mapOf(
            "input" to detachedPlaintext.encodeBase64(), "signature" to "vault:v1:${signed.encodeBase64()}"
        )
        val valid = httpRequest(HttpMethod.Post, "verify/$id", body)
            .tseJsonDataBody().jsonObject["valid"]?.jsonPrimitive?.boolean
            ?: throwTSEError("No (verification) valid response in data response")

        return if (valid) Result.success(detachedPlaintext)
        else Result.failure(IllegalArgumentException("Signature failed"))
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
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

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun getEncodedPublicKey(): String = // TODO add to base Key
        lazyOf(
            httpRequest()
                .tseJsonDataBody().jsonObject["keys"]?.jsonObject?.get("1")?.jsonObject?.get("public_key")?.jsonPrimitive?.content
                ?: throwTSEError("No keys/1/public_key in data response")
        ).value

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getPublicKey(): Key {
        logger.debug { "Getting public key: $keyType" }

        return JWKKey.importRawPublicKey(
            type = keyType,
            rawPublicKey = getBackingPublicKey(),
            metadata = null, // todo: import with explicit `keySize`
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


        private fun keyTypeToTseKeyMapping(type: KeyType) = when (type) {
            KeyType.Ed25519 -> "ed25519"
            KeyType.secp256r1 -> "ecdsa-p256"
            KeyType.RSA -> "rsa-2048"
            KeyType.secp256k1 -> throw IllegalArgumentException("Not supported: $type")
        }

        private fun tseKeyToKeyTypeMapping(type: String) = when (type) {
            "ed25519" -> KeyType.Ed25519
            "ecdsa-p256" -> KeyType.secp256r1
            "rsa-2048" -> KeyType.RSA
            else -> throw IllegalArgumentException("Not supported: $type")
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

            fun throwTSEError(msg: String): Nothing =
                throw RuntimeException("Invalid TSE server (${metadata.server}) response: $msg")

            val keyName = keyData["name"]?.jsonPrimitive?.content ?: throwTSEError("no key name in key data: $keyData")

            val publicKey = (keyData["keys"]
                ?: throwTSEError("no keys array in key data: $keyData")).jsonObject["1"]!!.jsonObject["public_key"]!!.jsonPrimitive.content.decodeBase64Bytes()

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
