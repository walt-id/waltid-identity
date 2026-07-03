package id.walt.crypto

import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.josef.toJsonWebKey
import at.asitplus.signum.supreme.SignatureResult
import at.asitplus.signum.supreme.dsl.REQUIRED
import at.asitplus.signum.supreme.os.IosKeychainProvider
import dev.whyoleg.cryptography.CryptographyProvider
import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

sealed class IosKey : Key() {

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

    class Platform internal constructor(
        private val options: Options,
        override val hasPrivateKey: Boolean = true,
    ) : IosKey() {

        companion object {
            suspend fun create(options: Options): Platform {
                when (val curve = options.keyType.toPlatformKeyStoreCurve()) {
                    null -> IosKeychainProvider.createSigningKey(options.kid) {
                        rsa { }
                    }.getOrThrow()
                    else -> IosKeychainProvider.createSigningKey(options.kid) {
                        ec { this.curve = curve }
                        if (options.inSecureElement) {
                            hardware { backing = REQUIRED }
                        }
                    }.getOrThrow()
                }
                return Platform(options)
            }

            suspend fun load(options: Options): Platform {
                IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
                return Platform(options)
            }

            suspend fun delete(kid: String) {
                IosKeychainProvider.deleteSigningKey(kid).getOrThrow()
            }
        }

        override val keyType get() = options.keyType

        private suspend fun signer() = IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()

        override suspend fun getKeyId(): String = options.kid

        override suspend fun getThumbprint(): String =
            signer().publicKey.toJsonWebKey(options.kid).jwkThumbprint

        override suspend fun exportJWK(): String = exportJWKObject().toString()

        override suspend fun exportJWKObject(): JsonObject {
            val jwk = signer().publicKey.toJsonWebKey(options.kid)
            return Json.parseToJsonElement(joseCompliantSerializer.encodeToString(jwk)).jsonObject
        }

        override suspend fun exportPEM(): String {
            val derBytes = signer().publicKey.encodeToTlv().derEncoded
            val base64 = Base64.encode(derBytes).chunked(64).joinToString("\n")
            return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
        }

        override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): Any {
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
            IosKeychainProvider.deleteSigningKey(options.kid).getOrThrow()
        }.isSuccess
    }

    class Software private constructor(
        private val delegate: MobileSoftwareKey,
    ) : IosKey() {

        companion object {
            suspend fun create(options: Options): Software =
                Software(MobileSoftwareKey.create(CryptographyProvider.Default, options.kid, options.keyType))

            suspend fun load(options: Options, jwkBytes: ByteArray): Software =
                Software(MobileSoftwareKey.load(CryptographyProvider.Default, options.kid, options.keyType, jwkBytes))

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
            delegate.signRawBytes(plaintext, customSignatureAlgorithm)

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
