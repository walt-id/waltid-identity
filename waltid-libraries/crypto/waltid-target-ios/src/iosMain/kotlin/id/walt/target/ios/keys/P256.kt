package id.walt.target.ios.keys

import id.walt.platform.utils.ios.DS_Operations
import id.walt.platform.utils.ios.ECKeyUtils
import id.walt.platform.utils.ios.SignResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import platform.Security.SecKeyRef

sealed class P256 {
    sealed class PrivateKey(val kid: String, protected val inSecureEnclave: Boolean) : KeyRepresentation, Signing {
        companion object {

            fun createInKeychain(kid: String, inSecureEnclave: Boolean): PrivateKey {
                deleteFromKeychain(kid)
                KeychainOperations.P256.create(kid, inSecureEnclave)
                return P256KeychainPrivateKey(kid, inSecureEnclave)
            }

            fun loadFromKeychain(kid: String, inSecureEnclave: Boolean): PrivateKey {
                KeychainOperations.P256.load(kid, inSecureEnclave)
                return P256KeychainPrivateKey(kid, inSecureEnclave)
            }

            fun deleteFromKeychain(kid: String) {
                KeychainOperations.P256.delete(kid)
            }

            fun importInKeychain(
                kid: String,
                externalRepresentation: ByteArray,
                inSecureEnclave: Boolean): PrivateKey =
                KeychainOperations.P256.createPrivateKeyFrom(externalRepresentation) { privateKey ->
                    ExistingP256KeychainPrivateKey(kid, inSecureEnclave, privateKey)
                }

            fun publicKeyJwkFrom(externalRepresentation: ByteArray): JsonObject {
                val jwk = KeychainOperations.usePublicKeyFrom(externalRepresentation) { publicKey ->//.publicKeyFrom(externalRepresentation) { publicKey ->
                    ECKeyUtils.exportJwkWithPublicKey(publicKey, null)!!
                }.let { Json.parseToJsonElement(it).jsonObject }
                return jwk
            }

            fun signJwsUsing(externalRepresentation: ByteArray, plainText: ByteArray, headers: Map<String, JsonElement>): String {
                return KeychainOperations.P256.createPrivateKeyFrom(externalRepresentation) { privateKey ->
                    val result = DS_Operations.signWithBody(
                        plainText.toNSData(),
                        "ES256",
                        privateKey,
                        headersData = JsonObject(headers).toString().toNSData()
                    )

                    check(result.success()) {
                        result.errorMessage()!!
                    }

                    result.data()!!
                }
            }
        }

        fun publicKey(): PublicKey = P256KeychainPublicKey(kid, inSecureEnclave)

        abstract fun <T> loadPrivateSecKey(kid: String, block: (privateSecKey: SecKeyRef?) -> T)

        override fun jwk(): JsonObject {
            return KeychainOperations.P256.withPrivateKey(kid, inSecureEnclave) { privateKey ->
                ECKeyUtils.exportJwkWithPrivateKey(privateKey, null)!!
            }.let { Json.parseToJsonElement(it).jsonObject }
        }

        override fun thumbprint(): String {
            return KeychainOperations.P256.withPrivateKey(kid, inSecureEnclave) { privateKey ->
                ECKeyUtils.thumbprintWithPrivateKey(privateKey, null)!!
            }
        }

        override fun pem(): String {
            return KeychainOperations.P256.withPrivateKey(kid, inSecureEnclave) { privateKey ->
                KeychainOperations.keyExternalRepresentation(privateKey)
            }.let { ECKeyUtils.pemWithPrivateKeyRepresentation(it.toNSData()) }
        }

        override fun kid(): String = kid
        override fun externalRepresentation(): ByteArray {
            return KeychainOperations.P256.withPrivateKey(kid(), inSecureEnclave) { privateKey ->
                KeychainOperations.keyExternalRepresentation(privateKey)
            }
        }

        override fun signJws(plainText: ByteArray, headers: Map<String, JsonElement>): String {
            return KeychainOperations.P256.withPrivateKey(kid, inSecureEnclave) { privateKey ->
                val result = DS_Operations.signWithBody(
                    plainText.toNSData(),
                    "ES256",
                    privateKey,
                    headersData = JsonObject(headers).toString().toNSData()
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

                result.isValidData()!!.toByteArray().let { resultByteArray ->
                    Json.parseToJsonElement(resultByteArray.decodeToString())
                }.let { resultByteArray ->
                    Result.success(resultByteArray.jsonObject)
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

    private val x963Representation: ByteArray by lazy {
        byteArrayOf(0x04) + base64Url.decode(_jwk.x) + base64Url.decode(_jwk.y)
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

internal class P256KeychainPublicKey(private val kid: String, private val inSecureEnclave: Boolean) : P256.PublicKey() {
    override fun jwk(): JsonObject = KeychainOperations.P256.withPublicKey(kid, inSecureEnclave) { publicKey ->
        ECKeyUtils.exportJwkWithPublicKey(publicKey, null)!!
    }.let { Json.parseToJsonElement(it).jsonObject }

    override fun <T> publicSecKey(block: (SecKeyRef?) -> T): T =
        KeychainOperations.P256.withPublicKey(kid, inSecureEnclave) {
            block(it)
        }

    override fun kid(): String = kid
}



internal class P256KeychainPrivateKey(kid: String, inSecureEnclave: Boolean) : P256.PrivateKey(kid, inSecureEnclave) {
    override fun <T> loadPrivateSecKey(kid: String, block: (privateSecKey: SecKeyRef?) -> T) {
        KeychainOperations.P256.withPrivateKey(kid, inSecureEnclave) { key ->
            block(key)
        }
    }
}

internal class ExistingP256KeychainPrivateKey(kid: String, inSecureEnclave: Boolean, private val secKeyRef: SecKeyRef?) : P256.PrivateKey(kid, inSecureEnclave) {
    override fun <T> loadPrivateSecKey(kid: String, block: (privateSecKey: SecKeyRef?) -> T) {
        block(secKeyRef)
    }
}