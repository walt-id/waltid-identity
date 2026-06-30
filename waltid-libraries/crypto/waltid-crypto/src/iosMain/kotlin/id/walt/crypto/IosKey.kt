package id.walt.crypto

import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.josef.toJsonWebKey
import at.asitplus.signum.supreme.SignatureResult
import at.asitplus.signum.supreme.dsl.REQUIRED
import at.asitplus.signum.supreme.os.IosKeychainProvider
import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.Uuid

class IosKey private constructor(
    private val options: Options,
    override val hasPrivateKey: Boolean = false
) : Key() {

    class Options(
        val kid: String = Uuid.random().toString(),
        val keyType: KeyType,
        val inSecureElement: Boolean = false
    ) {
        init {
            if (inSecureElement) {
                require(keyType == KeyType.secp256r1) { "kid: $kid, Error: Only KeyType.secp256r1 can be stored in secure element." }
            }
        }
    }

    companion object {
        suspend fun create(options: Options): Key {
            val curve = options.keyType.toPlatformKeyStoreCurve()
            val result = if (curve != null) {
                IosKeychainProvider.createSigningKey(options.kid) {
                    ec {
                        this.curve = curve
                    }
                    if (options.inSecureElement) {
                        hardware { backing = REQUIRED }
                    }
                }
            } else {
                IosKeychainProvider.createSigningKey(options.kid) {
                    rsa { }
                }
            }

            result.getOrThrow()
            return IosKey(options, true)
        }

        suspend fun load(options: Options): Key {
            IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
            return IosKey(options, true)
        }

        suspend fun delete(kid: String, type: KeyType) {
            IosKeychainProvider.deleteSigningKey(kid).getOrThrow()
        }
    }

    override val keyType get() = options.keyType

    override suspend fun getKeyId(): String = options.kid

    override suspend fun getThumbprint(): String {
        val signer = IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
        val jwk = signer.publicKey.toJsonWebKey(options.kid)
        return jwk.jwkThumbprint
    }

    override suspend fun exportJWK(): String = exportJWKObject().toString()

    override suspend fun exportJWKObject(): JsonObject {
        val signer = IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
        val jwk = signer.publicKey.toJsonWebKey(options.kid)
        return kotlinx.serialization.json.Json.parseToJsonElement(
            joseCompliantSerializer.encodeToString(jwk)
        ).jsonObject
    }

    override suspend fun exportPEM(): String {
        val signer = IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
        val derBytes = signer.publicKey.encodeToTlv().derEncoded
        val base64 = kotlin.io.encoding.Base64.encode(derBytes).chunked(64).joinToString("\n")
        return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
    }

    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): Any {
        check(hasPrivateKey) { "Only private key can do signing." }
        val signer = IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
        val result = signer.sign(plaintext)
        check(result is SignatureResult.Success) { "Signing failed: $result" }
        return result.signature.rawByteArray
    }

    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
        check(hasPrivateKey) { "Only private key can do signing." }
        val signer = IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
        return signJwsWithPlatformSigner(options.keyType, plaintext, headers) { data -> signer.sign(data) }
    }

    override suspend fun verifyRaw(
        signed: ByteArray, detachedPlaintext: ByteArray?,
        customSignatureAlgorithm: String?
    ): Result<ByteArray> {
        val signer = IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
        return verifyRawWithPlatformSigner(options.keyType, signer.publicKey, signed, detachedPlaintext)
    }

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        val signer = IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
        return verifyJwsWithPlatformSigner(options.keyType, signer.publicKey, signedJws)
    }

    override suspend fun getPublicKey(): Key = IosKey(options, false)

    override suspend fun getPublicKeyRepresentation(): ByteArray {
        val signer = IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
        return signer.publicKey.encodeToTlv().derEncoded
    }

    override suspend fun getMeta(): KeyMeta = JwkKeyMeta(getKeyId())

    override suspend fun deleteKey(): Boolean = runCatching {
        IosKeychainProvider.deleteSigningKey(options.kid).getOrThrow()
    }.isSuccess
}
