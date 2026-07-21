package id.walt.crypto

import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.josef.toJsonWebKey
import at.asitplus.signum.supreme.CFCryptoOperationFailed
import at.asitplus.signum.supreme.SignatureResult
import at.asitplus.signum.supreme.dsl.REQUIRED
import at.asitplus.signum.supreme.os.IosSigner
import at.asitplus.signum.supreme.os.IosKeychainProvider
import dev.whyoleg.cryptography.CryptographyProvider
import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyHardwareBacking
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationAware
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import id.walt.crypto.keys.KeyUseAuthorizationPrompt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import platform.Security.errSecItemNotFound
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.uuid.Uuid

sealed class IosKey : Key() {

    class Options(
        val kid: String = Uuid.random().toString(),
        val keyType: KeyType,
        val inSecureElement: Boolean = false,
        val keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None,
        val authorizationPrompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt(),
    ) {
        init {
            if (inSecureElement) {
                require(keyType == KeyType.secp256r1) { "kid: $kid, Error: Only KeyType.secp256r1 can be stored in secure element." }
            }
            requireSupportedProtectedCombination()
        }
    }

    class Platform internal constructor(
        private val options: Options,
        override val hasPrivateKey: Boolean = true,
    ) : IosKey(), KeyUseAuthorizationAware {

        companion object {
            suspend fun create(options: Options): Platform {
                when (val curve = options.keyType.toPlatformKeyStoreCurve()) {
                    null -> IosKeychainProvider.createSigningKey(options.kid) {
                        rsa { }
                        configureProtection(options)
                    }.getOrThrow()
                    else -> IosKeychainProvider.createSigningKey(options.kid) {
                        ec { this.curve = curve }
                        configureProtection(options)
                    }.getOrThrow()
                }
                return Platform(options)
            }

            suspend fun load(options: Options): Platform {
                runCatching { options.loadSigner() }
                    .getOrElse { throw options.mapPlatformFailure(it) }
                return Platform(options)
            }

            suspend fun delete(kid: String) {
                IosKeychainProvider.deleteSigningKey(kid).getOrThrow()
            }
        }

        override val keyType get() = options.keyType
        override val keyUseAuthorizationPolicy get() = options.keyUseAuthorizationPolicy
        override val isPlatformBacked: Boolean = true

        private suspend fun signer(): IosSigner = runCatching { options.loadSigner() }
            .getOrElse { throw options.mapPlatformFailure(it) }

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
            return signer().sign(plaintext).mapPlatformFailure(options).signatureBytesOrThrow(
                protectedKeyUse = options.keyUseAuthorizationPolicy != KeyUseAuthorizationPolicy.None,
            )
        }

        override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
            check(hasPrivateKey) { "Only private key can do signing." }
            val signer = signer()
            return signJwsWithPlatformSigner(
                options.keyType,
                plaintext,
                headers,
                protectedKeyUse = options.keyUseAuthorizationPolicy != KeyUseAuthorizationPolicy.None,
            ) { data ->
                signer.sign(data).mapPlatformFailure(options)
            }
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
        override suspend fun effectiveHardwareBacking(): KeyHardwareBacking =
            if (options.inSecureElement) KeyHardwareBacking.SecureEnclave else KeyHardwareBacking.Platform
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

private fun at.asitplus.signum.supreme.os.IosSigningKeyConfiguration.configureProtection(options: IosKey.Options) {
    if (options.inSecureElement || options.keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.BiometricCurrentSet) {
        hardware {
            backing = REQUIRED
            if (options.keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.BiometricCurrentSet) {
                protection {
                    factors {
                        biometry = true
                        biometryWithNewFactors = false
                        deviceLock = false
                    }
                    timeout = Duration.ZERO
                }
            }
        }
    }
    signer {
        unlockPrompt {
            message = options.authorizationPrompt.message
            cancelText = options.authorizationPrompt.cancelText
        }
    }
}

private suspend fun IosKey.Options.loadSigner(): IosSigner {
    val signer = IosKeychainProvider.getSignerForKey(kid) {
        unlockPrompt {
            message = authorizationPrompt.message
            cancelText = authorizationPrompt.cancelText
        }
    }.getOrThrow()
    if (
        keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.BiometricCurrentSet &&
        (!signer.needsAuthentication || !signer.needsAuthenticationForEveryUse)
    ) {
        throw KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.ProtectedKeyInvalidated,
            message = "The stored key does not enforce biometric authorization for every use",
        )
    }
    return signer
}

private fun IosKey.Options.requireSupportedProtectedCombination() {
    if (keyUseAuthorizationPolicy != KeyUseAuthorizationPolicy.BiometricCurrentSet) return
    if (keyType != KeyType.secp256r1 || !inSecureElement) {
        throw KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.UnsupportedCombination,
            message = "iOS biometric current-set authorization requires a Secure Enclave secp256r1 key",
        )
    }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal fun IosKey.Options.mapPlatformFailure(throwable: Throwable): Throwable {
    if (keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.None) return throwable
    val causes = generateSequence(throwable as Throwable?) { it.cause }.toList()
    return when {
        causes.any { it is NoSuchElementException } -> KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.ProtectedKeyMissing,
            message = "The protected key is missing",
            cause = throwable,
        )

        // BiometryCurrentSet invalidation can leave Signum's public-key metadata present while the
        // OS makes the protected private key inaccessible as an absent Keychain item.
        causes.filterIsInstance<CFCryptoOperationFailed>().any { it.osStatus == errSecItemNotFound } ->
            KeyUseAuthorizationException(
                failure = KeyUseAuthorizationFailure.ProtectedKeyInvalidated,
                message = "The protected key is no longer usable under its biometric current-set policy",
                cause = throwable,
            )

        else -> throwable
    }
}

private fun SignatureResult<*>.mapPlatformFailure(options: IosKey.Options): SignatureResult<*> = when (this) {
    is SignatureResult.Error -> SignatureResult.Error(options.mapPlatformFailure(exception))
    else -> this
}
