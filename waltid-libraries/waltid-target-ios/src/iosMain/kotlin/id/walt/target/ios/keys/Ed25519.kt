package id.walt.target.ios.keys

import id.walt.platform.utils.ios.Ed25519KeyUtils
import id.walt.platform.utils.ios.SHA256Utils
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import platform.Foundation.NSError
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

sealed class Ed25519 {
    sealed class PrivateKey : Signing, KeyRepresentation {
        companion object {

            fun createInKeychain(kid: String): PrivateKey = memScoped {
                deleteFromKeychain(kid)
                val nsError = alloc<ObjCObjectVar<NSError?>>()
                val createdKid = Ed25519KeyUtils.createWithKid(kid, appId, nsError.ptr)

                when {
                    createdKid != null -> Ed25519KeychainPrivateKey(createdKid)
                    else -> throw IllegalStateException(
                        nsError.value?.localizedDescription ?: "Create key failed"
                    )
                }
            }

            fun loadFromKeychain(kid: String): PrivateKey = memScoped {
                val nsError = alloc<ObjCObjectVar<NSError?>>()

                Ed25519KeyUtils.loadWithKey(kid, nsError.ptr)

                when {
                    nsError.value != null -> throw IllegalStateException(nsError.value!!.localizedDescription)
                    else -> Ed25519KeychainPrivateKey(kid)
                }
            }

            fun deleteFromKeychain(kid: String) {
                Ed25519KeyUtils.removeWithKey(kid, appId)
            }
        }

        abstract fun publicKey(): PublicKey
    }

    sealed class PublicKey : Verification, KeyRepresentation {
        companion object {
            fun fromJwk(jwk: String): PublicKey {
                return Ed25519PublicKeyJwk(jwk)
            }
        }

        override fun verifyJws(jws: String): Result<JsonObject> {
            val (header, payload, signature) = jws.split('.')
            val signingInput = "$header.$payload"

            val verifyResult =
                verifyRaw(base64UrlDecode(signature), signingInput.encodeToByteArray())
            return when {
                verifyResult.isSuccess -> Result.success(
                    Json.parseToJsonElement(
                        base64UrlDecode(
                            payload
                        ).decodeToString()
                    ).jsonObject
                )

                else -> Result.failure(verifyResult.exceptionOrNull()!!)
            }
        }

        override fun verifyRaw(signature: ByteArray, signedData: ByteArray): Result<ByteArray> =
            memScoped {
                val nsError = alloc<ObjCObjectVar<NSError?>>()
                val verifyResult = Ed25519KeyUtils.verifyRawWithPublicKeyRaw(
                    externalRepresentation().toNSData(),
                    signature.toNSData(),
                    signedData.toNSData(),
                    nsError.ptr
                )

                when {
                    verifyResult?.success() == true && verifyResult.success() -> Result.success(
                        signedData
                    )

                    verifyResult?.success() == false -> Result.failure(
                        IllegalStateException(
                            verifyResult.errorMessage()
                        )
                    )

                    else -> Result.failure(
                        IllegalStateException(
                            nsError.value?.localizedDescription ?: "verifyRaw failed"
                        )
                    )
                }
            }

        override fun jwk(): JsonObject {
            return buildJsonObject {
                put("crv", "Ed25519")
                kid()?.let { put("kid", it) }
                put("kty", "OKP")
                put("x", Base64.UrlSafe.encode(externalRepresentation()))
            }
        }

        override fun pem(): String {
            val start = "-----BEGIN PUBLIC KEY-----\n"
            val end = "\n-----END PUBLIC KEY-----"
            val prefix = byteArrayOf(
                0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00
            )
            val content = prefix + externalRepresentation()
            return start + Base64.encode(content) + end
        }

        override fun thumbprint(): String {
            return jwk().toString().encodeToByteArray().toNSData().let {
                SHA256Utils.hashWithData(it)
            }.let {
                Base64.UrlSafe.encode(it.toByteArray())
            }
        }
    }
}

internal class Ed25519KeychainPrivateKey(private val kid: String) : Ed25519.PrivateKey() {
    override fun publicKey(): Ed25519.PublicKey {
        return Ed25519KeychainPublicKey(kid)
    }

    override fun signJws(plainText: ByteArray, headers: Map<String, String>): String {
        val headersJsonObject = JsonObject(headers.mapValues { (_, v) -> JsonPrimitive(v) })

        val signingInput = Base64.UrlSafe.encode(
            headersJsonObject.toString().encodeToByteArray()
        ).trimEnd('=') + "." + Base64.UrlSafe.encode(plainText).trimEnd('=')

        val signature = signRaw(signingInput.encodeToByteArray())

        return signingInput + "." + Base64.UrlSafe.encode(signature).trimEnd('=')
    }

    override fun signRaw(plainText: ByteArray): ByteArray = memScoped {
        val nsError = alloc<ObjCObjectVar<NSError?>>()

        val bytes = Ed25519KeyUtils.signRawWithKid(kid, plainText.toNSData(), nsError.ptr)
        when {
            bytes != null -> bytes.toByteArray()
            else -> throw IllegalStateException(
                nsError.value?.localizedDescription ?: "signRaw failed"
            )
        }
    }

    override fun jwk(): JsonObject {
        val publicRaw = publicKey().externalRepresentation()
        val privateRaw = externalRepresentation()

        return buildJsonObject {
            put("crv", "Ed25519")
            put("d", Base64.UrlSafe.encode(privateRaw))
            put("kid", kid)
            put("kty", "OKP")
            put("x", Base64.UrlSafe.encode(publicRaw))
        }
    }

    override fun thumbprint(): String {
        return jwk().toString().encodeToByteArray().toNSData().let {
            SHA256Utils.hashWithData(it)
        }.let {
            Base64.UrlSafe.encode(it.toByteArray())
        }
    }

    override fun pem(): String {

//        The headers are:
//
//        For private DER keys: 30:2e:02:01:00:30:05:06:03:2b:65:70:04:22:04:20
//        For public DER keys: 30:2A:30:05:06:03:2B:65:70:03:21:00
//
        val start = "-----BEGIN PRIVATE KEY-----\n"
        val end = "\n-----END PRIVATE KEY-----"
        val prefix = byteArrayOf(
            0x30,
            0x2E,
            0x02,
            0x01,
            0x00,
            0x30,
            0x05,
            0x06,
            0x03,
            0x2b,
            0x65,
            0x70,
            0x04,
            0x22,
            0x04,
            0x20
        )
        val content = prefix + externalRepresentation()
        return start + Base64.encode(content) + end
    }

    override fun kid(): String {
        return kid
    }

    override fun externalRepresentation(): ByteArray {
        return Ed25519KeyUtils.privateRawRepresentationWithKid(kid, null)!!.toByteArray()
    }
}

internal class Ed25519KeychainPublicKey(private val kid: String) : Ed25519.PublicKey() {

    override fun kid(): String {
        return kid
    }

    override fun externalRepresentation(): ByteArray {
        return Ed25519KeyUtils.publicRawRepresentationWithKid(kid, null)!!.toByteArray()
    }
}

internal class Ed25519PublicKeyJwk(private val jwk: String) : Ed25519.PublicKey() {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val _jwk by lazy { json.decodeFromJsonElement<Jwk>(jwk()) }

    @OptIn(ExperimentalEncodingApi::class)

    private val externalRepresentation: ByteArray by lazy {
        base64UrlDecode(_jwk.x)
    }

    @Serializable
    internal data class Jwk(val x: String, val kid: String? = null)

    override fun jwk(): JsonObject {
        return Json.parseToJsonElement(jwk).jsonObject
    }

    override fun kid(): String? {
        return _jwk.kid
    }

    override fun externalRepresentation(): ByteArray {
        return externalRepresentation
    }
}