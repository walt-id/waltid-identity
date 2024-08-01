package id.walt.target.ios.keys

import id.walt.platform.utils.ios.DS_Operations
import id.walt.platform.utils.ios.RSAKeyUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import platform.Foundation.CFBridgingRelease
import platform.Security.SecKeyRef
import kotlin.io.encoding.Base64

sealed class RSA {
    sealed class PrivateKey : KeyRepresentation, Signing {
        companion object {

            fun createInKeychain(kid: String, size: UInt): PrivateKey {
                deleteFromKeychain(kid)
                KeychainOperations.RSA.create(kid, size)
                return RSAPrivateKeyKeychain(kid)
            }

            fun loadFromKeychain(kid: String): PrivateKey {
                KeychainOperations.RSA.load(kid)
                return RSAPrivateKeyKeychain(kid)
            }

            fun deleteFromKeychain(kid: String) {
                KeychainOperations.RSA.delete(kid)
            }
        }

        abstract fun publicKey(): PublicKey
    }

    sealed class PublicKey : KeyRepresentation, Verification {
        companion object {

            fun fromJwk(jwk: String): PublicKey {
                return RSAPublicKeyJwk(jwk)
            }
        }

        internal abstract fun <T> publicSecKey(block: (SecKeyRef?) -> T): T

        override fun thumbprint(): String {
            return publicSecKey {
                RSAKeyUtils.thumbprintWithPublicKey(it, null)!!
            }
        }

        override fun externalRepresentation(): ByteArray {
            return publicSecKey {
                KeychainOperations.keyExternalRepresentation(it)
            }
        }

        override fun pem(): String = externalRepresentation().let {
            Base64.encode(it)
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
                KeychainOperations.RSA.verifyRaw(it, signature, signedData)
            }
        }
    }
}

internal class RSAPrivateKeyKeychain(private val kid: String) : RSA.PrivateKey() {
    override fun publicKey(): RSA.PublicKey = RSAPublicKeyKeychain(kid)

    override fun jwk(): JsonObject {
        error("RSAPrivateKeyKeychain::jwk() - Not yet implemented")
    }

    override fun thumbprint(): String {
        error("RSAPrivateKeyKeychain::thumbprint() - Not yet implemented")
    }

    override fun pem(): String {
        error("RSAPrivateKeyKeychain::pem() - Not yet implemented")
    }

    override fun kid(): String = kid

    override fun externalRepresentation(): ByteArray {
        return KeychainOperations.RSA.withPrivateKey(kid) {
            KeychainOperations.keyExternalRepresentation(it)
        }
    }

    override fun signJws(plainText: ByteArray, headers: Map<String, String>): String {
        return KeychainOperations.RSA.withPrivateKey(kid) {
            val result = DS_Operations.signWithBody(
                plainText.toNSData(), "RS256", it, headers as Map<Any?, *>
            )

            check(result.success()) {
                result.errorMessage()!!
            }

            result.data()!!
        }
    }

    override fun signRaw(plainText: ByteArray): ByteArray {
        return KeychainOperations.RSA.signRaw(kid, plainText)
    }

}

internal class RSAPublicKeyKeychain(private val kid: String) : RSA.PublicKey() {
    override fun <T> publicSecKey(block: (SecKeyRef?) -> T): T {
        return KeychainOperations.RSA.withPublicKey(kid) {
            block(it)
        }
    }

    override fun jwk(): JsonObject {
        return KeychainOperations.RSA.withPublicKey(kid) {
            RSAKeyUtils.exportJwkWithPublicKey(it, null)
        }.let {
            Json.parseToJsonElement(it!!).jsonObject
        }
    }

    override fun kid(): String = kid

}

internal class RSAPublicKeyJwk(private val jwk: String) : RSA.PublicKey() {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val _jwk by lazy { json.decodeFromJsonElement<Jwk>(jwk()) }

    @Serializable
    internal data class Jwk(var kid: String? = null)

    override fun <T> publicSecKey(block: (SecKeyRef?) -> T): T {
        val secKeyRef = RSAKeyUtils.publicJwkToSecKeyWithJwk(jwk, error = null)
        return try {
            block(secKeyRef)
        } finally {
            CFBridgingRelease(secKeyRef)
        }
    }

    override fun jwk(): JsonObject {
        return Json.parseToJsonElement(jwk).jsonObject
    }

    override fun kid(): String? {
        return _jwk.kid
    }

}