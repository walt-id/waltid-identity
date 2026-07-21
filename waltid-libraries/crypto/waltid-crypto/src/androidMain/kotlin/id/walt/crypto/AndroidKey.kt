package id.walt.crypto

import android.os.Build
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.fragment.app.FragmentActivity
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.josef.toJsonWebKey
import at.asitplus.signum.supreme.SignatureResult
import at.asitplus.signum.supreme.os.AndroidKeystoreSigner
import at.asitplus.signum.supreme.os.AndroidKeyStoreProvider
import at.asitplus.signum.supreme.os.needsAuthentication
import at.asitplus.signum.supreme.os.needsAuthenticationForEveryUse
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.jdk.JDK
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
import org.bouncycastle.jce.provider.BouncyCastleProvider
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.uuid.Uuid

sealed class AndroidKey : Key() {

    class Options(
        val kid: String = Uuid.random().toString(),
        val keyType: KeyType,
        val keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None,
        val authorizationPrompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt(),
        private val interactionContextProvider: () -> FragmentActivity? = { null },
    ) {
        internal val interactionContext: FragmentActivity?
            get() = interactionContextProvider().takeIf { it.canHostBiometricPrompt() }
    }

    class Platform internal constructor(
        private val options: Options,
        override val hasPrivateKey: Boolean = true,
    ) : AndroidKey(), KeyUseAuthorizationAware {

        companion object {
            suspend fun create(options: Options): Platform {
                options.requireSupportedProtectedCombination()
                when (val curve = options.keyType.toPlatformKeyStoreCurve()) {
                    null -> AndroidKeyStoreProvider.createSigningKey(options.kid) {
                        rsa { }
                        if (options.keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.BiometricCurrentSet) {
                            configureBiometricCurrentSet(options)
                        }
                    }.getOrThrow()
                    else -> AndroidKeyStoreProvider.createSigningKey(options.kid) {
                        ec { this.curve = curve }
                        if (options.keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.BiometricCurrentSet) {
                            configureBiometricCurrentSet(options)
                        }
                    }.getOrThrow()
                }
                return Platform(options)
            }

            suspend fun load(options: Options): Platform {
                options.requireSupportedProtectedCombination()
                runCatching { options.loadSigner() }
                    .getOrElse { throw options.mapPlatformFailure(it) }
                return Platform(options)
            }

            suspend fun delete(kid: String) {
                AndroidKeyStoreProvider.deleteSigningKey(kid).getOrThrow()
            }
        }

        override val keyType get() = options.keyType
        override val keyUseAuthorizationPolicy get() = options.keyUseAuthorizationPolicy
        override val isPlatformBacked: Boolean = true

        private suspend fun signer(): AndroidKeystoreSigner = runCatching { options.loadSigner() }
            .getOrElse { throw options.mapPlatformFailure(it) }

        private fun requireSigningInteractionContext() {
            if (
                options.keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.BiometricCurrentSet &&
                options.interactionContext == null
            ) {
                throw KeyUseAuthorizationException(
                    failure = KeyUseAuthorizationFailure.InteractionContextUnavailable,
                    message = "A FragmentActivity interaction context is required for biometric key use",
                )
            }
        }

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
            requireSigningInteractionContext()
            return signer().sign(plaintext).mapPlatformFailure(options).signatureBytesOrThrow(
                protectedKeyUse = options.keyUseAuthorizationPolicy != KeyUseAuthorizationPolicy.None,
            )
        }

        override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
            check(hasPrivateKey) { "Only private key can do signing." }
            requireSigningInteractionContext()
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
        @Suppress("DEPRECATION")
        override suspend fun effectiveHardwareBacking(): KeyHardwareBacking {
            val keyInfo = signer().keyInfo
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (keyInfo.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeyHardwareBacking.Software
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeyHardwareBacking.TrustedEnvironment
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeyHardwareBacking.StrongBox
                    KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE -> KeyHardwareBacking.SecureHardware
                    else -> KeyHardwareBacking.Unknown
                }
            } else if (keyInfo.isInsideSecureHardware) {
                KeyHardwareBacking.SecureHardware
            } else {
                KeyHardwareBacking.Software
            }
        }
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

private fun at.asitplus.signum.supreme.os.AndroidSigningKeyConfiguration.configureBiometricCurrentSet(
    options: AndroidKey.Options,
) {
    hardware {
        protection {
            factors {
                biometry = true
                biometryWithNewFactors = false
                deviceLock = false
            }
            timeout = Duration.ZERO
        }
    }
    signer {
        unlockPrompt {
            message = options.authorizationPrompt.message
            cancelText = options.authorizationPrompt.cancelText
            allowedAuthenticators = BIOMETRIC_STRONG
            options.interactionContext?.let { activity = it }
        }
    }
}

private fun FragmentActivity?.canHostBiometricPrompt(): Boolean =
    this != null && !isFinishing && !isDestroyed && !isChangingConfigurations

private suspend fun AndroidKey.Options.loadSigner(): AndroidKeystoreSigner {
    val signer = AndroidKeyStoreProvider.getSignerForKey(kid) {
        unlockPrompt {
            message = authorizationPrompt.message
            cancelText = authorizationPrompt.cancelText
            if (keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.BiometricCurrentSet) {
                allowedAuthenticators = BIOMETRIC_STRONG
            }
            interactionContext?.let { activity = it }
        }
    }.getOrThrow()
    if (keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.BiometricCurrentSet) {
        val biometricOnly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            signer.keyInfo.userAuthenticationType == KeyProperties.AUTH_BIOMETRIC_STRONG
        } else {
            true
        }
        if (
            !signer.needsAuthentication ||
            !signer.needsAuthenticationForEveryUse ||
            !signer.keyInfo.isInvalidatedByBiometricEnrollment ||
            !biometricOnly
        ) {
            throw KeyUseAuthorizationException(
                failure = KeyUseAuthorizationFailure.ProtectedKeyInvalidated,
                message = "The stored key does not enforce biometric current-set authorization for every use",
            )
        }
    }
    return signer
}

private fun AndroidKey.Options.requireSupportedProtectedCombination() {
    if (
        keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.BiometricCurrentSet &&
        keyType != KeyType.secp256r1
    ) {
        throw KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.UnsupportedCombination,
            message = "Android biometric current-set authorization is supported only for secp256r1 keys",
        )
    }
}

private fun AndroidKey.Options.mapPlatformFailure(throwable: Throwable): Throwable {
    if (keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.None) return throwable
    val causes = generateSequence(throwable as Throwable?) { it.cause }.toList()
    return when {
        causes.any { it is KeyPermanentlyInvalidatedException } -> KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.ProtectedKeyInvalidated,
            message = "The protected key was invalidated",
            cause = throwable,
        )
        causes.any { it is NoSuchElementException } -> KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.ProtectedKeyMissing,
            message = "The protected key is missing",
            cause = throwable,
        )
        else -> throwable
    }
}

private fun SignatureResult<*>.mapPlatformFailure(options: AndroidKey.Options): SignatureResult<*> = when (this) {
    is SignatureResult.Error -> SignatureResult.Error(options.mapPlatformFailure(exception))
    else -> this
}
