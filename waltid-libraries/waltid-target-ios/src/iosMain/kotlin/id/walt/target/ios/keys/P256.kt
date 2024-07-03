package id.walt.target.ios.keys

import KeyRepresentation
import Signing
import Verification
import id.walt.platform.utils.ios.DS_Operations
import id.walt.platform.utils.ios.ECKeyUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


sealed class P256 {
    sealed class PrivateKey : KeyRepresentation, Signing, Verification {
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
    }
}

internal class P256JwkPublicKey(private val jwk: String) : P256.PublicKey() {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val _jwk by lazy { json.decodeFromJsonElement<Jwk>(jwk()) }

    @OptIn(ExperimentalEncodingApi::class)

    private val x963Representation: ByteArray by lazy {
        byteArrayOf(0x04) + Base64.UrlSafe.decode(_jwk.x) + Base64.UrlSafe.decode(_jwk.y)
    }

    @Serializable
    internal data class Jwk(val x: String, val y: String, var kid: String? = null)

    override fun jwk(): JsonObject {
        return Json.parseToJsonElement(jwk).jsonObject
    }

    override fun thumbprint(): String {
        return KeychainOperations.P256.createPublicKeyFrom(x963Representation) { publicKey ->
            ECKeyUtils.exportJwkWithPublicKey(publicKey, null)!!
        }
    }

    override fun pem(): String {
        return ECKeyUtils.pemWithPublicKeyRepresentation(x963Representation.toNSData())
    }

    override fun kid(): String? {
        return _jwk.kid
    }

    override fun externalRepresentation(): ByteArray {
        return KeychainOperations.P256.createPublicKeyFrom(x963Representation) { publicKey ->
            KeychainOperations.keyExternalRepresentation(publicKey)
        }
    }

    override fun verifyJws(jws: String): Result<JsonObject> {
        return KeychainOperations.P256.createPublicKeyFrom(x963Representation) { publicKey ->
            val result = DS_Operations.verifyWithJws(jws, publicKey)

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
        return KeychainOperations.P256.createPublicKeyFrom(x963Representation) { publicKey ->
            KeychainOperations.P256.verifyRaw(publicKey, signature, signedData)
        }
    }
}

internal class P256KeychainPublicKey(private val kid: String) : P256.PublicKey() {
    override fun jwk(): JsonObject = KeychainOperations.P256.withPublicKey(kid) { privateKey ->
        ECKeyUtils.exportJwkWithPublicKey(privateKey, null)!!
    }.let { Json.parseToJsonElement(it).jsonObject }

    override fun thumbprint(): String {
        return KeychainOperations.P256.withPublicKey(kid) { privateKey ->
            ECKeyUtils.thumbprintWithPublicKey(privateKey, null)!!
        }
    }

    override fun pem(): String {
        return KeychainOperations.P256.withPublicKey(kid) { publicKey ->
            KeychainOperations.keyExternalRepresentation(publicKey)
        }.let { ECKeyUtils.pemWithPublicKeyRepresentation(it.toNSData()) }
    }

    override fun kid(): String = kid
    override fun externalRepresentation(): ByteArray {
        return KeychainOperations.P256.withPublicKey(kid()) { publicKey ->
            KeychainOperations.keyExternalRepresentation(publicKey)
        }
    }

    override fun verifyJws(jws: String): Result<JsonObject> {
        return KeychainOperations.P256.withPublicKey(kid) { publicKey ->
            val result = DS_Operations.verifyWithJws(jws, publicKey)

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
        return KeychainOperations.P256.verifyRaw(kid, signature, signedData)
    }
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

    override fun verifyJws(jws: String): Result<JsonObject> {
        return KeychainOperations.P256.withPublicKey(kid) { publicKey ->
            val result = DS_Operations.verifyWithJws(jws, publicKey)

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
        return KeychainOperations.P256.verifyRaw(kid, signature, signedData)
    }
}