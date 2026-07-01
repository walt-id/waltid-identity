package id.walt.crypto

import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.josef.toJsonWebKey
import at.asitplus.signum.supreme.SignatureResult
import at.asitplus.signum.supreme.os.AndroidKeyStoreProvider
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.jdk.JDK
import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.bouncycastle.jce.provider.BouncyCastleProvider
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

sealed class AndroidKey : Key() {

    class Options(
        val kid: String = Uuid.random().toString(),
        val keyType: KeyType,
    )

    class Platform internal constructor(
        private val options: Options,
        override val hasPrivateKey: Boolean = true,
    ) : AndroidKey() {

        companion object {
            suspend fun create(options: Options): Platform {
                when (val curve = options.keyType.toPlatformKeyStoreCurve()) {
                    null -> AndroidKeyStoreProvider.createSigningKey(options.kid) {
                        rsa { }
                    }.getOrThrow()
                    else -> AndroidKeyStoreProvider.createSigningKey(options.kid) {
                        ec { this.curve = curve }
                    }.getOrThrow()
                }
                return Platform(options)
            }

            suspend fun load(options: Options): Platform {
                AndroidKeyStoreProvider.getSignerForKey(options.kid).getOrThrow()
                return Platform(options)
            }

            suspend fun delete(kid: String) {
                AndroidKeyStoreProvider.deleteSigningKey(kid).getOrThrow()
            }
        }

        override val keyType get() = options.keyType

        private suspend fun signer() = AndroidKeyStoreProvider.getSignerForKey(options.kid).getOrThrow()

        override suspend fun getKeyId(): String = options.kid

        override suspend fun getThumbprint(): String =
            signer().publicKey.toJsonWebKey(options.kid).jwkThumbprint

        override suspend fun exportJWK(): String = exportJWKObject().toString()

        override suspend fun exportJWKObject(): JsonObject {
            val jwkStr = joseCompliantSerializer.encodeToString(signer().publicKey.toJsonWebKey(options.kid))
            return Json.parseToJsonElement(jwkStr) as JsonObject
        }

        override suspend fun exportPEM(): String {
            val derBytes = signer().publicKey.encodeToTlv().derEncoded
            val base64 = Base64.Mime.encode(derBytes)
            return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
        }

        override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray {
            check(hasPrivateKey) { "Only private key can do signing." }
            val result = signer().sign(plaintext)
            check(result is SignatureResult.Success) { "Signing failed: $result" }
            return result.signature.rawByteArray
        }

        override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
            check(hasPrivateKey) { "Only private key can do signing." }
            val signer = signer()
            return signJwsWithPlatformSigner(options.keyType, plaintext, headers) { data -> signer.sign(data) }
        }

        override suspend fun verifyRaw(
            signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?
        ): Result<ByteArray> {
            val signer = signer()
            return verifyRawWithPlatformSigner(options.keyType, signer.publicKey, signed, detachedPlaintext)
        }

        override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
            val signer = signer()
            return verifyJwsWithPlatformSigner(options.keyType, signer.publicKey, signedJws)
        }

        override suspend fun getPublicKey(): Key = Platform(options, hasPrivateKey = false)
        override suspend fun getPublicKeyRepresentation(): ByteArray = signer().publicKey.encodeToTlv().derEncoded
        override suspend fun getMeta(): KeyMeta = JwkKeyMeta(getKeyId())
        override suspend fun deleteKey(): Boolean = runCatching {
            AndroidKeyStoreProvider.deleteSigningKey(options.kid).getOrThrow()
        }.isSuccess
    }

    class Software private constructor(
        private val delegate: MobileSoftwareKey,
    ) : AndroidKey() {

        companion object {
            private val provider by lazy {
                CryptographyProvider.JDK(BouncyCastleProvider())
            }

            suspend fun create(options: Options): Software =
                Software(MobileSoftwareKey.create(provider, options.kid, options.keyType))

            suspend fun load(options: Options, jwkBytes: ByteArray): Software =
                Software(MobileSoftwareKey.load(provider, options.kid, options.keyType, jwkBytes))

            suspend fun exportKeyMaterial(key: Software): ByteArray =
                key.delegate.exportPrivateKeyMaterial()
        }

        override val keyType get() = delegate.keyType
        override val hasPrivateKey get() = delegate.hasPrivateKey

        override suspend fun getKeyId(): String = delegate.getKeyId()

        override suspend fun getThumbprint(): String = delegate.getThumbprint()

        override suspend fun exportJWK(): String = delegate.exportJWK()

        override suspend fun exportJWKObject(): JsonObject = delegate.exportJWKObject()

        override suspend fun exportPEM(): String = delegate.exportPEM()

        override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray =
            delegate.signRaw(plaintext, customSignatureAlgorithm)

        override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String =
            delegate.signJws(plaintext, headers)

        override suspend fun verifyRaw(
            signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?
        ): Result<ByteArray> = delegate.verifyRaw(signed, detachedPlaintext, customSignatureAlgorithm)

        override suspend fun verifyJws(signedJws: String): Result<JsonElement> =
            delegate.verifyJws(signedJws)

        override suspend fun getPublicKey(): Key = delegate.getPublicKey()
        override suspend fun getPublicKeyRepresentation(): ByteArray = delegate.getPublicKeyRepresentation()

        override suspend fun getMeta(): KeyMeta = delegate.getMeta()
        override suspend fun deleteKey(): Boolean = delegate.deleteKey()
    }
}
