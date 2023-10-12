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
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.random.Random

// Works with the Hashicorp Transit Secret Engine
@Suppress("TRANSIENT_IS_REDUNDANT")
@Serializable
@SerialName("tse")
class TSEKey(
    val server: String,
    private val accessKey: String,
    val id: String,
    //private var publicKey: ByteArray? = null,
    //override var keyType: KeyType? = null
    private var _publicKey: ByteArray? = null,
    private var _keyType: KeyType? = null
) : id.walt.crypto.keys.Key() {

    @Transient val retrievedKeyType by lazy { runBlocking { retrieveKeyType() } }
    @Transient val retrievedPublicKey by lazy { runBlocking { retrievePublicKey() } }

    @Transient
    override var keyType: KeyType
        get() = _keyType ?: retrievedKeyType
        set(value) { _keyType = value }

    @Transient
    var publicKey: ByteArray
        get() = _publicKey ?: retrievedPublicKey
        set(value) { _publicKey = value }

    private suspend fun retrievePublicKey(): ByteArray {
        val keyData = http.get("$server/keys/$id") {
            this.header("X-Vault-Token", accessKey)
        }.body<JsonObject>()["data"]!!.jsonObject["keys"]!!.jsonObject

        // TODO: try this
        return keyData["1"]!!
            .jsonObject["public_key"]!!
            .jsonPrimitive.content
            .decodeBase64Bytes()
    }

    private suspend fun retrieveKeyType(): KeyType =
        tseKeyToKeyTypeMapping(http.get("$server/keys/$id") {
            this.header("X-Vault-Token", accessKey)
        }.body<JsonObject>()["data"]!!.jsonObject["type"]!!.jsonPrimitive.content)

    override val hasPrivateKey: Boolean
        get() = TODO("Not yet implemented")

    override suspend fun getKeyId(): String = id

    override suspend fun getThumbprint(): String {
        TODO("Not yet implemented")
    }

    override suspend fun exportJWK(): String =
        throw IllegalArgumentException("The private key should not be exposed.")

    override suspend fun exportJWKObject(): JsonObject =
        throw IllegalArgumentException("The private key should not be exposed.")

    override suspend fun exportPEM(): String =
        throw IllegalArgumentException("The private key should not be exposed.")

    override suspend fun signRaw(plaintext: ByteArray): Any {
        val signatureBase64 = http.post("$server/sign/${id}") {
            header("X-Vault-Token", accessKey) // TODO
            setBody(
                mapOf(
                    "input" to plaintext.encodeBase64()
                )
            )
        }.body<JsonObject>()["data"]!!.jsonObject["signature"]!!.jsonPrimitive.content
            .removePrefix("vault:v1:")

        return signatureBase64
    }

    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String {
        val header = Json.encodeToString(
            mutableMapOf(
                "typ" to "JWT",
                "alg" to keyType.jwsAlg(),
            ).apply { putAll(headers) }
        ).encodeToByteArray().encodeToBase64Url()

        val payload = plaintext.encodeToBase64Url()

        val signable = "$header.$payload"

        val signatureBase64 = signRaw(signable.encodeToByteArray()) as String
        val signatureBase64Url = signatureBase64.base64toBase64Url()

        return "$signable.$signatureBase64Url"
    }

    override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?): Result<ByteArray> {
        val localPublicKey = when (keyType) {
            KeyType.Ed25519 -> LocalKey.importRawPublicKey(
                type = keyType,
                rawPublicKey = publicKey,
                metadata = LocalKeyMetadata() // todo: explicit `keySize`
            )

            KeyType.RSA, KeyType.secp256r1 -> LocalKey.importPEM(getEncodedPublicKey()).getOrThrow()
            KeyType.secp256k1 -> throw IllegalArgumentException("Type not supported for TSE: $keyType")
        }

        println(localPublicKey)

        check(detachedPlaintext != null) { "An detached plaintext is needed." }

        val valid = http.post("$server/verify/${id}") {
            header("X-Vault-Token", accessKey)
            setBody(
                mapOf(
                    "input" to detachedPlaintext.encodeBase64(),
                    "signature" to "vault:v1:${signed.encodeBase64()}"
                )
            )
        }.body<JsonObject>()["data"]!!.jsonObject["valid"]!!.jsonPrimitive.boolean

        return if (valid) Result.success(detachedPlaintext)
        else Result.failure(IllegalArgumentException("Signature failed"))
    }

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

    suspend fun getEncodedPublicKey(): String = // TODO add to base Key
        lazyOf(
            http.get("$server/keys/$id") {
                header("X-Vault-Token", accessKey)
            }.body<JsonObject>()["data"]!!
                .jsonObject["keys"]!!
                .jsonObject["1"]!!
                .jsonObject["public_key"]!!
                .jsonPrimitive.content
        ).value

    override suspend fun getPublicKey(): id.walt.crypto.keys.Key {
        return LocalKey.importRawPublicKey(
            type = keyType,
            rawPublicKey = publicKey,
            metadata = LocalKeyMetadata(), // todo: import with explicit `keySize`
        )
    }

    override suspend fun getPublicKeyRepresentation(): ByteArray {
        return publicKey
    }

    /*
        val user = "corecrypto"
        val password = "Eibaekie0eeshieph1vahho6fengei7vioph"

        val token = http.post("$server/auth/userpass/login/$user") {
            setBody(mapOf("password" to password))
        }.body<JsonObject>()["auth"]!!.jsonObject["client_token"]!!.jsonPrimitive.content
        */

    override fun toString(): String = "[TSE ${keyType.name} key @ $server]"

    suspend fun delete() {
        http.post("$server/keys/$id/config") {
            header("X-Vault-Token", accessKey)
            setBody(
                mapOf(
                    "deletion_allowed" to true
                )
            )
        }

        http.delete("$server/keys/$id") {
            header("X-Vault-Token", accessKey)
        }
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

        override suspend fun generate(type: KeyType, metadata: TSEKeyMetadata): TSEKey {
            val keyData = http.post("${metadata.server}/keys/k${metadata.id ?: Random.nextInt()}") {
                header("X-Vault-Token", metadata.accessKey)
                setBody(
                    mapOf(
                        "type" to keyTypeToTseKeyMapping(type)
                    )
                )
            }.body<JsonObject>()["data"]!!.jsonObject

            val keyName = keyData["name"]!!.jsonPrimitive.content

            val publicKey = keyData["keys"]!!
                .jsonObject["1"]!!
                .jsonObject["public_key"]!!
                .jsonPrimitive.content
                .decodeBase64Bytes()

            return TSEKey(metadata.server, metadata.accessKey, keyName, publicKey, type)
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

suspend fun main() {
    val tseKey = TSEKey.generate(KeyType.Ed25519, TSEKeyMetadata("http://127.0.0.1:8200/v1/transit", "dev-only-token"))
    val plaintext = "This is a plaintext 123".encodeToByteArray()

    val signed = tseKey.signRaw(plaintext) as String

    val verified = tseKey.verifyRaw(signed.decodeBase64Bytes(), plaintext)

    println("TSEKey: $tseKey")
    println("Plaintext: ${plaintext.decodeToString()}")
    println("Signed: $signed")
    println("Verified signature success: ${verified.isSuccess}")
    println("Verified plaintext: ${verified.getOrNull()!!.decodeToString()}")

    tseKey.delete()
}
