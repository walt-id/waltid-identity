package id.walt.core.crypto.keys

import id.walt.core.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.core.crypto.utils.Base64Utils.base64toBase64Url
import id.walt.core.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.core.crypto.utils.JwsUtils.jwsAlg
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.random.Random

// Works with the Hashicorp Transit Secret Engine
@Serializable
@SerialName("tse")
class TSEKey(
    private val _tseId: String,
    private val _rawPublicKey: ByteArray,
    override val keyType: KeyType
) : Key() {

    override val hasPrivateKey: Boolean
        get() = TODO("Not yet implemented")

    override suspend fun getKeyId(): String = _tseId

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
        val signatureBase64 = http.post("$url/transit/sign/${_tseId}") {
            header("X-Vault-Token", "dev-only-token") // TODO
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
                metadata = KeyMetadata(),
                rawPublicKey = _rawPublicKey
            )

            KeyType.RSA, KeyType.secp256r1 -> LocalKey.importPEM(getEncodedPublicKey()).getOrThrow()
            KeyType.secp256k1 -> throw IllegalArgumentException("Type not supported for TSE: $keyType")
        }

        println(localPublicKey)

        check(detachedPlaintext != null) { "An detached plaintext is needed." }

        val valid = http.post("$url/transit/verify/${_tseId}") {
            header("X-Vault-Token", "dev-only-token")
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
            http.get("${url}/transit/keys/$_tseId") {
                header("X-Vault-Token", "dev-only-token")
            }.body<JsonObject>()["data"]!!
                .jsonObject["keys"]!!
                .jsonObject["1"]!!
                .jsonObject["public_key"]!!
                .jsonPrimitive.content
        ).value

    override suspend fun getPublicKey(): Key = LocalKey.importRawPublicKey(
        type = keyType,
        metadata = KeyMetadata(),
        rawPublicKey = _rawPublicKey
    )

    override suspend fun getPublicKeyRepresentation(): ByteArray = _rawPublicKey

    /*
        val user = "corecrypto"
        val password = "Eibaekie0eeshieph1vahho6fengei7vioph"

        val token = http.post("$url/auth/userpass/login/$user") {
            setBody(mapOf("password" to password))
        }.body<JsonObject>()["auth"]!!.jsonObject["client_token"]!!.jsonPrimitive.content
        */

    override fun toString(): String = "[TSE ${keyType.name} key @ $url]"

    suspend fun delete() {
        http.post("$url/transit/keys/$_tseId/config") {
            header("X-Vault-Token", "dev-only-token")
            setBody(
                mapOf(
                    "deletion_allowed" to true
                )
            )
        }

        http.delete("$url/transit/keys/$_tseId") {
            header("X-Vault-Token", "dev-only-token")
        }
    }

    companion object : KeyCreator {
        val url = "http://127.0.0.1:8200/v1"

        private fun tseKeyTypeMapping(type: KeyType) = when (type) {
            KeyType.Ed25519 -> "ed25519"
            KeyType.secp256r1 -> "ecdsa-p256"
            KeyType.RSA -> "rsa-2048"
            KeyType.secp256k1 -> throw IllegalArgumentException("Not supported: $type")
        }

        override suspend fun generate(type: KeyType, metadata: KeyMetadata): TSEKey {
            val keyData = http.post("$url/transit/keys/k${Random.nextInt()}") {
                header("X-Vault-Token", "dev-only-token")
                setBody(
                    mapOf(
                        "type" to tseKeyTypeMapping(type)
                    )
                )
            }.body<JsonObject>()["data"]!!.jsonObject

            val keyName = keyData["name"]!!.jsonPrimitive.content

            val publicKey = keyData["keys"]!!
                .jsonObject["1"]!!
                .jsonObject["public_key"]!!
                .jsonPrimitive.content
                .decodeBase64Bytes()

            return TSEKey(keyName, publicKey, type)
        }

        override suspend fun importRawPublicKey(type: KeyType, metadata: KeyMetadata, rawPublicKey: ByteArray): Key =
            throw IllegalArgumentException("This function is only for loading public keys, TSE is meant for private keys.")

        override suspend fun importJWK(jwk: String): Result<TSEKey> {
            TODO() // relevant?
        }

        override suspend fun importPEM(pem: String): Result<TSEKey> {
            TODO() // relevant?
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
    val tseKey = TSEKey.generate(KeyType.Ed25519)
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
