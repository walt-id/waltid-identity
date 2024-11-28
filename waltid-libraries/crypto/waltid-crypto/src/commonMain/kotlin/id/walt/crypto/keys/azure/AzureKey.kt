package id.walt.crypto.keys.azure

import id.walt.crypto.exceptions.KeyTypeNotSupportedException
import id.walt.crypto.keys.EccUtils
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUtils.rawSignaturePayloadForJws
import id.walt.crypto.keys.KeyUtils.signJwsWithRawSignature
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

private val logger = KotlinLogging.logger { }
var _accessAzureToken: String? = null

@OptIn(ExperimentalJsExport::class)
@JsExport
@Suppress("TRANSIENT_IS_REDUNDANT")
@Serializable
@SerialName("azure")
class AzureKey(
    private var _publicKey: String? = null,
    private var _keyType: KeyType? = null,
    val config: AzureKeyMetadata,
    val id: String
) : Key() {


    override var keyType: KeyType
        get() = _keyType!!
        set(value) {
            _keyType = value
        }

    override val hasPrivateKey: Boolean
        get() = false

    override fun toString(): String = "[Azure ${keyType.name} key @Azure-Vault ${config.auth.keyVaultUrl} - $id]"

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
    override suspend fun exportJWKObject(): JsonObject = Json.parseToJsonElement(_publicKey!!).jsonObject

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
    @OptIn(ExperimentalStdlibApi::class)
    /**
     * Executes Azure sign operation, and converts Azure signature to DER by default (for ECC keys)
     * @param ieeeP1363Signature set to true to leave signature in Azure IEEE P1363 format (no conversion)
     */
    suspend fun signRawAzure(plaintext: ByteArray, ieeeP1363Signature: Boolean): ByteArray {
        val sha256Digest: ByteArray = SHA256().digest(plaintext)
        val base64UrlEncoded: String = sha256Digest.encodeToBase64Url()

        val accessToken = getAzureAccessToken(
            config.auth.tenantId.toString(),
            config.auth.clientId.toString(), config.auth.clientSecret.toString()
        )
        val signingAlgorithm = jwsSigningAlgorithm(keyType)

        val body = buildJsonObject {
            put("alg", JsonPrimitive(signingAlgorithm))
            put("value", JsonPrimitive(base64UrlEncoded))
        }
        val signatureResponse = client.post("$id/sign?api-version=7.4") {
            contentType(ContentType.Application.Json)
            bearerAuth(accessToken)
            setBody(
                body
            )
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
        println("public key to verify with: $publicKey")
        println("signed data: $signed")
        println("detached plaintext: $detachedPlaintext")
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

    @Transient
    private var backedKey: Key? = null

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getPublicKey(): Key = backedKey ?: when {
        _publicKey != null -> _publicKey!!.let {
            JWKKey.importJWK(it).getOrThrow()
        }

        else -> getPublicKeyFromAzureKms(metadata = config, keyId = id)
    }.also { newBackedKey -> backedKey = newBackedKey }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getPublicKeyRepresentation(): ByteArray {
        TODO("Not yet implemented")
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getMeta(): KeyMeta {
        TODO("Not yet implemented")
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun deleteKey(): Boolean {

        val accessToken = getAzureAccessToken(
            config.auth.tenantId.toString(),
            config.auth.clientId.toString(), config.auth.clientSecret.toString()
        )
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
        val key_size: Int? = null,
        val key_ops: List<String>,
    )


    companion object : AzureKeyCreator {
        private suspend fun HttpResponse.azureJsonDataBody(): JsonObject {
            val baseMsg = { "Azure server (URL: ${this.request.url}) returned an invalid response: " }

            return runCatching {
                // First, get the body as a string
                val bodyStr = this.bodyAsText()

                // Parse the string as JsonObject
                Json.parseToJsonElement(bodyStr).jsonObject
            }.getOrElse {
                val bodyStr = this.bodyAsText() // Get the body in case of an exception
                throw IllegalArgumentException(
                    baseMsg.invoke() + if (bodyStr.isEmpty()) "empty response (instead of JSON data)"
                    else "invalid response: $bodyStr"
                )
            }
        }

        val client = HttpClient {
            install(ContentNegotiation) {
                json(Json { prettyPrint = true })
            }
        }

        @Serializable
        data class AzureTokenResponse(val access_token: String)

        @JvmBlocking
        @JvmAsync
        @JsPromise
        @JsExport.Ignore
        suspend fun getAzureAccessToken(tenantId: String, clientId: String, clientSecret: String): String {

            val response: HttpResponse = client.post("https://login.microsoftonline.com/$tenantId/oauth2/v2.0/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf(
                        "grant_type" to "client_credentials",
                        "client_id" to clientId,
                        "client_secret" to clientSecret,
                        "scope" to "https://vault.azure.net/.default"
                    ).formUrlEncode()
                )
            }
            val responseBody: String = response.body()
            val json = Json { ignoreUnknownKeys = true }
            val tokenResponse = json.decodeFromString<AzureTokenResponse>(responseBody)

            _accessAzureToken = tokenResponse.access_token
            return tokenResponse.access_token
        }

        private fun keyTypeToAzureKeyMapping(type: KeyType): Pair<String, String?> = when (type) {
            KeyType.secp256r1 -> "EC" to "P-256"  // EC key with P-256 curve
            KeyType.secp256k1 -> "EC" to "P-256K" // EC key with P-256K curve
            KeyType.RSA -> "RSA" to null  // RSA key, no curve
            else -> throw KeyTypeNotSupportedException(type.name)
        }

        private fun azureKeyToKeyTypeMapping(crv: String, kty: String): KeyType = when (kty) {
            "EC" -> when (crv) {
                "P-256" -> KeyType.secp256r1  // Mapping P-256 curve to secp256r1
                "P-256K" -> KeyType.secp256k1 // Mapping P-256K curve to secp256k1
                else -> throw KeyTypeNotSupportedException(crv)
            }

            "RSA" -> KeyType.RSA  // Mapping RSA key type
            else -> throw KeyTypeNotSupportedException(kty)
        }


        @JsExport.Ignore
        suspend fun getPublicKeyFromAzureKms(metadata: AzureKeyMetadata, keyId: String): Key {
            val accessToken = getAzureAccessToken(
                metadata.auth.tenantId.toString(),
                metadata.auth.clientId.toString(), metadata.auth.clientSecret.toString()
            )
            val publicKey = client.get("$keyId?api-version=7.4") {
                contentType(ContentType.Application.Json)
                bearerAuth(accessToken)
            }.azureJsonDataBody()

            val keyType = publicKey["key"]?.jsonObject?.get("kty")?.jsonPrimitive?.content!!
            val crvFromResponse = publicKey["key"]?.jsonObject?.get("crv")?.jsonPrimitive?.content

            return AzureKey(
                _keyType = azureKeyToKeyTypeMapping(crvFromResponse ?: "", keyType),
                config = metadata,
                id = keyId,
                _publicKey = publicKey["key"].toString()
            )
        }


        @JsExport.Ignore
        override suspend fun generate(type: KeyType, metadata: AzureKeyMetadata): AzureKey {

            val keyName = "waltid${Random.nextInt()}"
            val accessToken = getAzureAccessToken(
                metadata.auth.tenantId.toString(),
                metadata.auth.clientId.toString(), metadata.auth.clientSecret.toString()
            )
            val (kty, crv) = keyTypeToAzureKeyMapping(type)
            val keyRequestBody = if (kty == "RSA") {
                KeyCreateRequest(
                    kty = kty,
                    key_ops = listOf("sign", "verify"),
                    key_size = 2048
                )
            } else {
                KeyCreateRequest(
                    kty = kty,
                    crv = crv!!,
                    key_ops = listOf("sign", "verify")
                )
            }
            val key =
                client.post("${metadata.auth.keyVaultUrl}/keys/$keyName/create?api-version=7.4") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(accessToken)
                    setBody(keyRequestBody)
                }

            println("generation req: ${key.bodyAsText()}")


            val keyId = key.azureJsonDataBody()["key"]?.jsonObject?.get("kid")?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Azure server returned an invalid response: key ID not found")

            val keyType = key.azureJsonDataBody()["key"]?.jsonObject?.get("kty")?.jsonPrimitive?.content!!
            val crvFromResponse = key.azureJsonDataBody()["key"]?.jsonObject?.get("crv")?.jsonPrimitive?.content

            println(
                "Generated key with ID: $keyId, type: $keyType, curve: $crvFromResponse, metadata: $metadata"
            )

            return AzureKey(
                _keyType = azureKeyToKeyTypeMapping(crvFromResponse ?: "", keyType),
                config = metadata,
                id = keyId,
                _publicKey = key.azureJsonDataBody()["key"].toString()
            )
        }
    }
}
