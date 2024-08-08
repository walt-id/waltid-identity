package id.walt.target.ios.keys

import id.walt.platform.utils.ios.DS_Operations
import id.walt.platform.utils.ios.ECKeyUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import platform.Security.SecKeyRef
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


sealed class P256 {
    sealed class PrivateKey : KeyRepresentation, Signing {
        companion object {

            fun createInKeychain(kid: String): PrivateKey {
                deleteFromKeychain(kid)
                KeychainOperations.P256.create(kid)
                return P256KeychainPrivateKey(kid)
            }

            fun loadFromKeychain(kid: String): PrivateKey {
                KeychainOperations.P256.load(kid)
                return P256KeychainPrivateKey(kid)
            }

            fun deleteFromKeychain(kid: String) {
                KeychainOperations.P256.delete(kid)
            }
        }

        abstract fun publicKey(): PublicKey
    }

    sealed class PublicKey : KeyRepresentation, Verification {
        companion object {

            fun fromJwk(jwk: String): PublicKey {
                return P256JwkPublicKey(jwk)
            }
        }

        internal abstract fun <T> publicSecKey(block: (SecKeyRef?) -> T): T

        override fun thumbprint(): String {
            return publicSecKey {
                ECKeyUtils.thumbprintWithPublicKey(it, null)!!
            }
        }

        override fun externalRepresentation(): ByteArray {
            return publicSecKey {
                KeychainOperations.keyExternalRepresentation(it)
            }
        }

        override fun pem(): String = externalRepresentation().let {
            ECKeyUtils.pemWithPublicKeyRepresentation(it.toNSData())
        }

        override fun verifyJws(jws: String): Result<JsonObject> {
            return publicSecKey {
                val result = DS_Operations.verifyWithJws(jws, it)

                check(result.success()) {
                    result.errorMessage()!!
                }

                result.isValidData()!!.toByteArray().let {
                    Json.parseToJsonElement(it.decodeToString())
                }.let {
                    Result.success(it.jsonObject)
                }
            }
        }

        override fun verifyRaw(signature: ByteArray, signedData: ByteArray): Result<ByteArray> {
            return publicSecKey {
                KeychainOperations.P256.verifyRaw(it, signature, signedData)
            }
        }
    }
}

internal class P256JwkPublicKey(private val jwk: String) : P256.PublicKey() {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val _jwk by lazy { json.decodeFromJsonElement<Jwk>(jwk()) }

    @OptIn(ExperimentalEncodingApi::class)

    private val x963Representation: ByteArray by lazy {
        byteArrayOf(0x04) + base64UrlDecode(_jwk.x) + base64UrlDecode(_jwk.y)
    }

    @Serializable
    private data class Jwk(val x: String, val y: String, var kid: String? = null)

    override fun jwk(): JsonObject {
        return Json.parseToJsonElement(jwk).jsonObject
    }

    override fun <T> publicSecKey(block: (SecKeyRef?) -> T) =
        KeychainOperations.P256.createPublicKeyFrom(x963Representation) { publicKey ->
            block(publicKey)
        }

    override fun kid(): String? {
        return _jwk.kid
    }
}

internal class P256KeychainPublicKey(private val kid: String) : P256.PublicKey() {
    override fun jwk(): JsonObject = KeychainOperations.P256.withPublicKey(kid) { privateKey ->
        ECKeyUtils.exportJwkWithPublicKey(privateKey, null)!!
    }.let { Json.parseToJsonElement(it).jsonObject }

    override fun <T> publicSecKey(block: (SecKeyRef?) -> T): T =
        KeychainOperations.P256.withPublicKey(kid) {
            block(it)
        }

    override fun kid(): String = kid
}

internal class P256KeychainPrivateKey(private val kid: String) : P256.PrivateKey() {
    override fun publicKey(): P256.PublicKey = P256KeychainPublicKey(kid)

    override fun jwk(): JsonObject {
        return KeychainOperations.P256.withPrivateKey(kid) { privateKey ->
            ECKeyUtils.exportJwkWithPrivateKey(privateKey, null)!!
        }.let { Json.parseToJsonElement(it).jsonObject }
    }

    override fun thumbprint(): String {
        return KeychainOperations.P256.withPrivateKey(kid) { privateKey ->
            ECKeyUtils.thumbprintWithPrivateKey(privateKey, null)!!
        }
    }

    override fun pem(): String {
        return KeychainOperations.P256.withPrivateKey(kid) { privateKey ->
            KeychainOperations.keyExternalRepresentation(privateKey)
        }.let { ECKeyUtils.pemWithPrivateKeyRepresentation(it.toNSData()) }
    }

    override fun kid(): String = kid
    override fun externalRepresentation(): ByteArray {
        return KeychainOperations.P256.withPrivateKey(kid()) { privateKey ->
            KeychainOperations.keyExternalRepresentation(privateKey)
        }
    }

    override fun signJws(plainText: ByteArray, headers: Map<String, String>): String {
        return KeychainOperations.P256.withPrivateKey(kid) { privateKey ->
            val result = DS_Operations.signWithBody(
                plainText.toNSData(), "ES256", privateKey, headers as Map<Any?, *>
            )

            check(result.success()) {
                result.errorMessage()!!
            }

            result.data()!!
        }
    }

    override fun signRaw(plainText: ByteArray): ByteArray =
        KeychainOperations.P256.signRaw(kid, plainText)
}