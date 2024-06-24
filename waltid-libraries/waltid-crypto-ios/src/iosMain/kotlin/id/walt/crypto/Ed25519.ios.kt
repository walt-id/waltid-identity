package id.walt.crypto

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.platform.utils.ios.Ed25519KeyUtils
import io.ktor.util.decodeBase64String
import io.ktor.utils.io.core.toByteArray
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import platform.Foundation.NSError

class Ed25519Key(
    val keyId: String,
    override val hasPrivateKey: Boolean,
    override val keyType: KeyType = KeyType.Ed25519
) : IosKey() {
    override suspend fun getKeyId(): String = keyId

    override suspend fun getThumbprint(): String {
        TODO("Not yet implemented")
    }

    override suspend fun exportJWK(): String = exportJWKObject().toString()

    override suspend fun exportJWKObject(): JsonObject = buildJsonObject {
        put("kid", keyId)
        put("kty", "OKP")
        put("crv", "Ed25519")
        put("x", getPublicKeyRepresentation().encodeToBase64Url())
    }

    override suspend fun exportPEM(): String {
        TODO("Not yet implemented")
    }

    override suspend fun signRaw(plaintext: ByteArray): ByteArray = memScoped {
        val nsError = alloc<ObjCObjectVar<NSError?>>()

        val bytes = Ed25519KeyUtils.signRawWithKid(keyId, plaintext.toNSData(), nsError.ptr)
        when {
            bytes != null -> bytes.toByteArray()
            else -> throw IllegalStateException(
                nsError.value?.localizedDescription ?: "signRaw failed"
            )
        }
    }

    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String {
        return signJws(plaintext, headers.toJsonObject().toString().encodeToByteArray())
    }

    override suspend fun signJws(bodyJson: ByteArray, headersJson: ByteArray): String {
        val signingInput = headersJson.encodeToBase64Url() + "." + bodyJson.encodeToBase64Url()

        val signature = signRaw(signingInput.toByteArray())

        return signingInput + "." + signature.encodeToBase64Url()
    }

    override suspend fun verifyRaw(
        signed: ByteArray, detachedPlaintext: ByteArray?
    ): Result<ByteArray> = memScoped {
        val nsError = alloc<ObjCObjectVar<NSError?>>()
        val verifyResult = Ed25519KeyUtils.verifyRawWithKid(
            keyId, signed.toNSData(), detachedPlaintext!!.toNSData(), nsError.ptr
        )

        when {
            verifyResult?.success() == true && verifyResult.success() -> Result.success(
                detachedPlaintext
            )

            verifyResult?.success() == false -> Result.failure(IllegalStateException(verifyResult.errorMessage()))
            else -> Result.failure(
                IllegalStateException(
                    nsError.value?.localizedDescription ?: "verifyRaw failed"
                )
            )
        }
    }

    override suspend fun verifyJws(signedJws: String): Result<JsonObject> {
        val parts = signedJws.split('.')
        val signature = parts[2]
        val signingInput = parts[0] + "." + parts[1]

        val verifyResult = verifyRaw(signature.base64UrlDecode(), signingInput.encodeToByteArray())
        return when {
            verifyResult.isSuccess -> Result.success(Json.parseToJsonElement(parts[1].decodeBase64String()).jsonObject)
            else -> Result.failure(verifyResult.exceptionOrNull()!!)
        }
    }

    override suspend fun getPublicKey(): Key = this

    override suspend fun getPublicKeyRepresentation(): ByteArray = memScoped {

        val nsError = alloc<ObjCObjectVar<NSError?>>()
        val bytes = Ed25519KeyUtils.publicRawRepresentationWithKid(keyId, nsError.ptr)

        when {
            bytes != null -> bytes.toByteArray()
            else -> throw IllegalStateException(
                nsError.value?.localizedDescription ?: "getPublicKeyRepresentation failed"
            )
        }
    }

    override suspend fun getMeta(): KeyMeta {
        TODO("Not yet implemented")
    }


    companion object {
        public fun create(kid: String, appId: String): Ed25519Key = memScoped {

            delete(kid)

            val nsError = alloc<ObjCObjectVar<NSError?>>()
            val createdKid = Ed25519KeyUtils.createWithKid(kid, appId, nsError.ptr)

            when {
                createdKid != null -> Ed25519Key(createdKid, true)
                else -> throw IllegalStateException(
                    nsError.value?.localizedDescription ?: "Create key failed"
                )
            }
        }

        internal fun delete(kid: String) {
            Ed25519KeyUtils.removeWithKey(kid, null)
        }

        internal fun load(kid: String): Ed25519Key = memScoped {
            val nsError = alloc<ObjCObjectVar<NSError?>>()

            Ed25519KeyUtils.loadWithKey(kid, nsError.ptr)

            when {
                nsError.value != null -> throw IllegalStateException(nsError.value!!.localizedDescription)
                else -> Ed25519Key(kid, true)
            }
        }

        internal fun exist(kid: String) = try {
            load(kid)
            true
        } catch (e: Throwable) {
            false
        }.also { println("Ed25519:$kid:exist=$it") }
    }
}