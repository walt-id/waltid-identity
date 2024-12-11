package id.walt.crypto.keys.azure

import id.walt.crypto.exceptions.KeyTypeNotSupportedException
import id.walt.crypto.keys.*
import id.walt.crypto.keys.KeyUtils.rawSignaturePayloadForJws
import id.walt.crypto.keys.KeyUtils.signJwsWithRawSignature
import id.walt.crypto.keys.azure.AzureKey.AzureKeyFunctions.azureJsonDataBody
import id.walt.crypto.keys.azure.AzureKey.AzureKeyFunctions.fetchAccessToken
import id.walt.crypto.keys.azure.AzureKey.AzureKeyFunctions.keyTypeToAzureKeyMapping
import id.walt.crypto.keys.azure.AzureKey.AzureKeyFunctions.parseAzurePublicKey
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.jwsSigningAlgorithm
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger { }

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
@SerialName("azure")
class AzureKey(
    val id: String,
    val auth: AzureAuth,
    private var _keyType: KeyType? = null,
    private var _publicKey: DirectSerializedKey? = null
) : Key() {

    @Transient
    private lateinit var accessToken: String

    @Transient
    private lateinit var accessTokenExpiration: Instant

    private fun updateKeyType() {
        _keyType = _publicKey?.key?.keyType
    }

    @JsExport.Ignore
    suspend fun fetchAndUpdatePublicKey() {
        _publicKey = DirectSerializedKey(getPublicKeyFromAzureKms(getKeyId()))
    }

    @JsExport.Ignore
    suspend fun updateAccessToken() {
        val accessTokenResponse = fetchAccessToken(auth)

        accessToken = accessTokenResponse.accessToken
        accessTokenExpiration = accessTokenResponse.expiration
    }

    @JsExport.Ignore
    suspend fun ensureAccessTokenValid() {
        if (!this::accessToken.isInitialized || accessTokenExpiration >= Clock.System.now()) {
            updateAccessToken()
        }
    }

    @JsExport.Ignore
    override suspend fun init() {
        ensureAccessTokenValid()

        if (_publicKey == null) fetchAndUpdatePublicKey()
        if (_keyType == null) updateKeyType()
    }

    override var keyType: KeyType
        get() = _keyType ?: error("Getting keyType without calling init() first")
        set(value) {
            _keyType = value
        }

    override val hasPrivateKey: Boolean
        get() = false

    override fun toString(): String = "[Azure ${keyType.name} key @ ${auth.keyVaultUrl} - $id]"

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getKeyId(): String = id

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getThumbprint(): String = throw UnsupportedOperationException("No private key available")

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun exportJWK(): String = throw UnsupportedOperationException("No private key available")

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun exportJWKObject(): JsonObject = throw UnsupportedOperationException("No private key available")

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun exportPEM(): String = throw UnsupportedOperationException("No private key available")

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @OptIn(ExperimentalStdlibApi::class)
    /**
     * Executes Azure sign operation, and converts Azure signature to DER by default (for ECC keys)
     * @param ieeeP1363Signature set to true to leave signature in Azure IEEE P1363 format (no conversion)
     */
    suspend fun signRawAzure(plaintext: ByteArray, ieeeP1363Signature: Boolean): ByteArray {
        ensureAccessTokenValid()

        val sha256Digest: ByteArray = SHA256().digest(plaintext)
        val base64UrlEncoded: String = sha256Digest.encodeToBase64Url()

        val signingAlgorithm = jwsSigningAlgorithm(keyType)

        val body = buildJsonObject {
            put("alg", JsonPrimitive(signingAlgorithm))
            put("value", JsonPrimitive(base64UrlEncoded))
        }
        val signatureResponse = client.post("$id/sign?api-version=7.4") {
            contentType(ContentType.Application.Json)
            bearerAuth(accessToken)
            setBody(body)
        }
        var signature = signatureResponse.azureJsonDataBody()["value"]!!.jsonPrimitive.content.decodeFromBase64Url()

        // Convert signature from Azure IEEE P1363 to default DER format for raw sign
        // if not explicitly asked to leave it in IEEE P1363 with `ieeeP1363Signature`
        if (!ieeeP1363Signature && keyType in listOf(KeyType.secp256r1, KeyType.secp256k1)) {
            signature = EccUtils.convertP1363toDER(signature)
        }

        return signature
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun signRaw(plaintext: ByteArray): ByteArray {
        return signRawAzure(plaintext, ieeeP1363Signature = false)
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun signJws(
        plaintext: ByteArray,
        headers: Map<String, JsonElement>
    ): String {
        val (header, payload, toSign) = rawSignaturePayloadForJws(plaintext, headers, keyType)
        val rawSignature = signRawAzure(toSign, ieeeP1363Signature = true)
        val jws = signJwsWithRawSignature(rawSignature, header, payload)

        return jws
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?
    ): Result<ByteArray> {

        val publicKey = getPublicKey()
        val verification = publicKey.verifyRaw(signed, detachedPlaintext)
        return Result.success(
            verification.getOrThrow()
        )
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        val publicKey = getPublicKey()
        val verification = publicKey.verifyJws(signedJws)
        return verification
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getPublicKey(): Key = _publicKey?.key ?: error("Init was not called before public key was requested")

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getPublicKeyRepresentation(): ByteArray = getPublicKey().getPublicKeyRepresentation()

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getMeta(): KeyMeta = AzureKeyMeta(getKeyId())

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun deleteKey(): Boolean {
        ensureAccessTokenValid()
        val response = client.delete("$id?api-version=7.4") {
            contentType(ContentType.Application.Json)
            bearerAuth(accessToken)
        }
        return response.status.isSuccess()
    }

    @Serializable
    data class KeyCreateRequest(
        val kty: String,
        val crv: String? = null,
        @SerialName("key_size")
        val keySize: Int? = null,
        @SerialName("key_ops")
        val keyOps: List<String>,
    )


    @JsExport.Ignore
    suspend fun getPublicKeyFromAzureKms(keyId: String): Key {
        ensureAccessTokenValid()

        val keyResponse = client.get("$keyId?api-version=7.4") {
            contentType(ContentType.Application.Json)
            bearerAuth(accessToken)
        }.azureJsonDataBody()

        val publicKeyJson = (keyResponse["key"] ?: error("Missing key in response")).jsonObject

        val parsedAzurePublicKey = parseAzurePublicKey(publicKeyJson)

        return parsedAzurePublicKey.publicKey
    }

    object AzureKeyFunctions {
        internal fun keyTypeToAzureKeyMapping(type: KeyType): Pair<String, String?> = when (type) {
            KeyType.secp256r1 -> "EC" to "P-256"  // EC key with P-256 curve
            KeyType.secp256k1 -> "EC" to "P-256K" // EC key with P-256K curve
            KeyType.RSA -> "RSA" to null  // RSA key, no curve
            else -> throw KeyTypeNotSupportedException(type.name)
        }

        internal fun azureKeyToKeyTypeMapping(crv: String, kty: String): KeyType = when (kty) {
            "EC" -> when (crv) {
                "P-256" -> KeyType.secp256r1  // Mapping P-256 curve to secp256r1
                "P-256K" -> KeyType.secp256k1 // Mapping P-256K curve to secp256k1
                else -> throw KeyTypeNotSupportedException(crv)
            }

            "RSA" -> KeyType.RSA  // Mapping RSA key type
            else -> throw KeyTypeNotSupportedException(kty)
        }

        data class ParsedAzurePublicKey(
            val kid: String,
            val azureKeyType: String,
            val curve: String?,
            val keyType: KeyType,
            val publicKey: JWKKey
        )

        internal suspend fun parseAzurePublicKey(publicKeyJson: JsonObject): ParsedAzurePublicKey {
            val kid = publicKeyJson["kid"]?.jsonPrimitive?.content ?: error("No key id in key response")
            val azureKeyType = publicKeyJson["kty"]?.jsonPrimitive?.content ?: error("Missing key type in public key response")
            val crvFromResponse = publicKeyJson["crv"]?.jsonPrimitive?.content

            val publicKey = JWKKey.importJWK(publicKeyJson.toString())
                .getOrElse { exception -> throw IllegalArgumentException("Invalid JWK in public key: $publicKeyJson", exception) }

            val keyType = azureKeyToKeyTypeMapping(crvFromResponse ?: "", azureKeyType)

            return ParsedAzurePublicKey(kid, azureKeyType, crvFromResponse, keyType, publicKey)
        }


        @JvmBlocking
        @JvmAsync
        @JsPromise
        @JsExport.Ignore
        suspend fun fetchAccessToken(auth: AzureAuth): AzureTokenResponseParsed {
            require(auth.tenantId.all { it.lowercase() in "abcdef0123456789-" }) { "Tenant id contains invalid characters: ${auth.tenantId}" }

            val time = Clock.System.now()
            val response = client.post("https://login.microsoftonline.com/${auth.tenantId}/oauth2/v2.0/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf(
                        "grant_type" to "client_credentials",
                        "client_id" to auth.clientId,
                        "client_secret" to auth.clientSecret,
                        "scope" to "https://vault.azure.net/.default"
                    ).formUrlEncode()
                )
            }.run {
                runCatching { body<AzureTokenResponse>() }.getOrElse { ex ->
                    throw IllegalArgumentException("Could not retrieve access token: ${bodyAsText()}", ex)
                }
            }

            check(response.tokenType.lowercase() == "bearer") { "Can only handle bearer access tokens!" }

            return AzureTokenResponseParsed(
                accessToken = response.accessToken,
                expiration = time + response.expiresIn.seconds
            )
        }

        internal suspend fun HttpResponse.azureJsonDataBody(): JsonObject {
            val baseMsg = { "Azure server (URL: ${this.request.url}) returned an invalid response: " }

            return runCatching { body<JsonObject>() }.getOrElse {
                val bodyStr = this.bodyAsText() // Get the body in case of an exception
                throw IllegalArgumentException(
                    baseMsg.invoke() + if (bodyStr.isEmpty()) "empty response (instead of JSON data)"
                    else "invalid response: $bodyStr",
                    it
                )
            }
        }

        @Serializable
        data class AzureTokenResponse(
            @SerialName("token_type")
            val tokenType: String,

            @SerialName("expires_in")
            val expiresIn: Int,

            @SerialName("ext_expires_in")
            val extExpiresIn: Int,

            @SerialName("access_token")
            val accessToken: String
        )

        @Serializable
        data class AzureTokenResponseParsed(
            val accessToken: String,
            val expiration: Instant
        )
    }

    companion object : AzureKeyCreator {
        private val client = HttpClient {
            install(ContentNegotiation) {
                json(Json { prettyPrint = true })
            }
        }

        @JsExport.Ignore
        override suspend fun generate(type: KeyType, metadata: AzureKeyMetadata): AzureKey {
            val keyName = metadata.name ?: Random.nextInt().toString()

            val accessTokenResponse = fetchAccessToken(metadata.auth)

            val (kty, crv) = keyTypeToAzureKeyMapping(type)
            val keyRequestBody = if (kty == "RSA") {
                KeyCreateRequest(
                    kty = kty,
                    keyOps = listOf("sign", "verify"),
                    keySize = 2048
                )
            } else {
                KeyCreateRequest(
                    kty = kty,
                    crv = crv!!,
                    keyOps = listOf("sign", "verify")
                )
            }
            val response = client.post("${metadata.auth.keyVaultUrl}/keys/$keyName/create?api-version=7.4") {
                contentType(ContentType.Application.Json)
                bearerAuth(accessTokenResponse.accessToken)
                setBody(keyRequestBody)
            }.azureJsonDataBody()

            val parsedAzurePublicKey = parseAzurePublicKey(response.jsonObject["key"]?.jsonObject!!)

            val keyId = parsedAzurePublicKey.kid

            return AzureKey(
                id = keyId,
                auth = metadata.auth,
                _keyType = parsedAzurePublicKey.keyType,
                _publicKey = DirectSerializedKey(parsedAzurePublicKey.publicKey)
            )
        }
    }
}
