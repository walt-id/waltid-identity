package id.walt.crypto

import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.josef.toJsonWebKey
import at.asitplus.signum.supreme.SignatureResult
import at.asitplus.signum.supreme.os.AndroidKeyStoreProvider
import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

@Suppress("unused")
class AndroidKey private constructor(
    private val options: Options,
    override val hasPrivateKey: Boolean = false
) : Key() {

    class Options(
        val kid: String = Uuid.random().toString(),
        val keyType: KeyType,
    )

    companion object {
        suspend fun create(options: Options): Key {
            when (val curve = options.keyType.toPlatformKeyStoreCurve()) {
                null -> AndroidKeyStoreProvider.createSigningKey(options.kid) {
                    rsa { }
                }.getOrThrow()
                else -> AndroidKeyStoreProvider.createSigningKey(options.kid) {
                    ec { this.curve = curve }
                }.getOrThrow()
            }
            return AndroidKey(options, true)
        }

        suspend fun load(options: Options): Key {
            AndroidKeyStoreProvider.getSignerForKey(options.kid).getOrThrow()
            return AndroidKey(options, true)
        }

        suspend fun delete(kid: String, type: KeyType) {
            AndroidKeyStoreProvider.deleteSigningKey(kid).getOrThrow()
        }
    }

    override val keyType get() = options.keyType

    override suspend fun getKeyId(): String = options.kid

    override suspend fun getThumbprint(): String {
        val signer = AndroidKeyStoreProvider.getSignerForKey(options.kid).getOrThrow()
        val jwk = signer.publicKey.toJsonWebKey(options.kid)
        return jwk.jwkThumbprint
    }

    override suspend fun exportJWK(): String = exportJWKObject().toString()

    override suspend fun exportJWKObject(): JsonObject {
        val signer = AndroidKeyStoreProvider.getSignerForKey(options.kid).getOrThrow()
        val jwkStr = joseCompliantSerializer.encodeToString(signer.publicKey.toJsonWebKey(options.kid))
        return Json.parseToJsonElement(jwkStr) as JsonObject
    }

    override suspend fun exportPEM(): String {
        val signer = AndroidKeyStoreProvider.getSignerForKey(options.kid).getOrThrow()
        val derBytes = signer.publicKey.encodeToTlv().derEncoded
        val base64 = Base64.Mime.encode(derBytes)
        return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
    }

    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray {
        check(hasPrivateKey) { "Only private key can do signing." }
        val signer = AndroidKeyStoreProvider.getSignerForKey(options.kid).getOrThrow()
        val result = signer.sign(plaintext)
        check(result is SignatureResult.Success) { "Signing failed: $result" }
        return result.signature.rawByteArray
    }

    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
        check(hasPrivateKey) { "Only private key can do signing." }
        val signer = AndroidKeyStoreProvider.getSignerForKey(options.kid).getOrThrow()
        return signJwsWithPlatformSigner(options.keyType, plaintext, headers) { data -> signer.sign(data) }
    }

    override suspend fun verifyRaw(
        signed: ByteArray, detachedPlaintext: ByteArray?,
        customSignatureAlgorithm: String?
    ): Result<ByteArray> {
        val signer = AndroidKeyStoreProvider.getSignerForKey(options.kid).getOrThrow()
        return verifyRawWithPlatformSigner(options.keyType, signer.publicKey, signed, detachedPlaintext)
    }

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        val signer = AndroidKeyStoreProvider.getSignerForKey(options.kid).getOrThrow()
        return verifyJwsWithPlatformSigner(options.keyType, signer.publicKey, signedJws)
    }

    override suspend fun getPublicKey(): Key = AndroidKey(options, false)

    override suspend fun getPublicKeyRepresentation(): ByteArray {
        val signer = AndroidKeyStoreProvider.getSignerForKey(options.kid).getOrThrow()
        return signer.publicKey.encodeToTlv().derEncoded
    }

    override suspend fun getMeta(): KeyMeta = JwkKeyMeta(getKeyId())

    override suspend fun deleteKey(): Boolean = runCatching {
        AndroidKeyStoreProvider.deleteSigningKey(options.kid).getOrThrow()
    }.isSuccess
}
