package id.walt.crypto2.signum

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import at.asitplus.signum.supreme.os.AndroidKeystoreSigner
import at.asitplus.signum.supreme.os.AndroidKeyStoreProvider
import at.asitplus.signum.supreme.os.PlatformSigningProviderSigner
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ProviderId
import kotlinx.coroutines.CancellationException

/**
 * Android-only Signum backend backed by Android KeyStore.
 *
 * Per-use Android [SignumAuthenticationPolicy.UserPresence] operations resolve a current resumed
 * [FragmentActivity] at operation time so AndroidX BiometricPrompt can perform interactive
 * authorization. Timed operations only use an available activity if Keystore reports that
 * reusable authorization has expired; an already authorized signing operation remains headless.
 */
public class AndroidSignumKeyBackend(
    context: Context,
    private val interactionContextProvider: () -> FragmentActivity? = { null },
) : SignumPlatformBackend, SignumPrivateKeyImportBackend {
    private val hasStrongBox = context.applicationContext.packageManager
        .hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    override val id = ProviderId("android-keystore-signum")

    override fun supports(spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy): Boolean =
        policy.supportsAndroidSettings(importing = false) &&
            (policy.platform !is SignumPlatformPolicy.AndroidKeystore ||
                (spec == KeySpec.Ec(EcCurve.P256) && !policy.keyAgreement)) &&
            spec.isSupportedSignumSpec() &&
            usages.all { it == KeyUsage.SIGN || it == KeyUsage.VERIFY || it == KeyUsage.KEY_AGREEMENT } &&
            (KeyUsage.KEY_AGREEMENT !in usages || spec is KeySpec.Ec) &&
            (KeyUsage.KEY_AGREEMENT in usages) == policy.keyAgreement

    override suspend fun create(
        alias: String,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy,
    ): SignumPlatformKey {
        require(supports(spec, usages, policy)) { "Android Signum backend does not support the requested key and policy" }
        val signer = if (policy.platform is SignumPlatformPolicy.AndroidKeystore) {
            generateAndroidP256Key(alias, policy, hasStrongBox)
            AndroidKeyStoreProvider.getSignerForKey(alias).getOrThrow()
        } else AndroidKeyStoreProvider.createSigningKey(alias) {
            configureSignumKey(spec, usages, policy)
        }.getOrThrow()
        try {
            validateNativePolicy(signer, policy, alias)
        } catch (cause: Throwable) {
            try {
                delete(alias)
            } catch (cleanupFailure: Throwable) {
                cause.addSuppressed(cleanupFailure)
            }
            throw cause
        }
        return handle(alias, spec, usages, policy, signer)
    }

    override fun supportsImport(spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy): Boolean =
        spec == KeySpec.Ec(EcCurve.P256) && usages == setOf(KeyUsage.SIGN, KeyUsage.VERIFY) &&
            !policy.keyAgreement && policy.supportsAndroidSettings(importing = true)

    override suspend fun importPrivateKey(alias: String, material: id.walt.crypto2.keys.EncodedKey.Jwk,
        spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy): SignumPlatformKey {
        require(supportsImport(spec, usages, policy)) { "Unsupported Android private-key import policy" }
        importAndroidPrivateKey(alias, material, spec, policy, hasStrongBox)
        try { return requireNotNull(loadImportedKey(alias, spec, usages, policy)) }
        catch (cause: Throwable) {
            try { deleteImportedKey(alias, policy) } catch (cleanup: Throwable) { cause.addSuppressed(cleanup) }
            throw cause
        }
    }

    override suspend fun loadImportedKey(alias: String, spec: KeySpec, usages: Set<KeyUsage>,
        policy: SignumKeyPolicy): SignumPlatformKey? {
        require(supportsImport(spec, usages, policy)) { "Unsupported Android private-key import policy" }
        return load(alias, spec, usages, policy)?.also {
            require(it.origin == SignumKeyOrigin.IMPORTED) { "Expected an imported Android key" }
        }
    }

    override suspend fun deleteImportedKey(alias: String, policy: SignumKeyPolicy) = delete(alias)

    override suspend fun load(
        alias: String,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy,
    ): SignumPlatformKey? {
        val signer = AndroidKeyStoreProvider.getSignerForKey(alias).getOrElse { failure ->
            throw failure.mapSignumFailure(alias)
        }
        validateNativePolicy(signer, policy, alias)
        if (policy.authentication is SignumAuthenticationPolicy.UserPresence) {
            try {
                (signer as AndroidKeystoreSigner).checkKeyInvalidation()
            } catch (cause: Throwable) {
                throw cause.mapSignumFailure(alias)
            }
        }
        return handle(alias, spec, usages, policy, signer)
    }

    override suspend fun delete(alias: String) {
        AndroidKeyStoreProvider.deleteSigningKey(alias).getOrElse { failure ->
            val mapped = failure.mapSignumFailure(alias)
            if (mapped is SignumKeyNotFoundException) return
            throw mapped
        }
    }

    private fun handle(
        alias: String,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy,
        signer: PlatformSigningProviderSigner<*, *>,
    ): SignumPlatformKey {
        val attestation = signer.toAttestation()
        return SignumPlatformKeyHandle(
            alias = alias,
            spec = spec,
            protectionLevel = signer.observedProtection(),
            attestation = attestation,
            authentication = policy.authentication,
            signerFor = { algorithm: SignatureAlgorithm ->
                val interactionContext = when {
                    (policy.authentication as? SignumAuthenticationPolicy.UserPresence)?.timeoutSeconds == 0 -> requireInteractionContext(alias)
                    policy.authentication is SignumAuthenticationPolicy.UserPresence -> availableInteractionContext()
                    else -> null
                }
                AndroidKeyStoreProvider.getSignerForKey(alias) {
                    configureSignumOperation(algorithm, policy.authentication)
                    if (interactionContext != null) {
                        unlockPrompt {
                            (policy.authentication as? SignumAuthenticationPolicy.UserPresence)?.let { auth ->
                                message = auth.prompt
                                allowedAuthenticators = auth.androidPromptAuthenticators
                                cancelText = auth.androidPromptCancelText
                            }
                            activity = interactionContext
                        }
                    }
                }.getOrElse { failure ->
                    throw failure.mapSignumFailure(alias)
                }
            },
            operationFailureMapper = { failure ->
                val mapped = failure.mapSignumFailure(alias)
                if ((policy.authentication as? SignumAuthenticationPolicy.UserPresence)?.timeoutSeconds?.let { it > 0 } == true) {
                    mapped.mapTimedReuseInteractionContextFailure(alias, availableInteractionContext() != null)
                } else {
                    mapped
                }
            },
            nativePublicKey = signer.publicKey,
            keyAgreementEnabled = KeyUsage.KEY_AGREEMENT in usages && policy.keyAgreement,
        ).let { delegate -> object : SignumPlatformKey by delegate {
            override val origin = when ((signer as? AndroidKeystoreSigner)?.keyInfo?.origin) {
                KeyProperties.ORIGIN_IMPORTED -> SignumKeyOrigin.IMPORTED
                KeyProperties.ORIGIN_GENERATED -> SignumKeyOrigin.GENERATED
                else -> SignumKeyOrigin.UNKNOWN
            }
            override val securityLevel = signer.observedSecurityLevel()
        } }
    }

    @Suppress("DEPRECATION") // Required as the pre-S fallback when KeyInfo.securityLevel is unavailable.
    private fun validateNativePolicy(
        signer: PlatformSigningProviderSigner<*, *>,
        policy: SignumKeyPolicy,
        alias: String,
    ) {
        val androidSigner = signer as? AndroidKeystoreSigner
            ?: throw SignumKeyPolicyMismatchException(alias, "the native signer is not Android Keystore-backed")
        val info = androidSigner.keyInfo
        val settings = policy.androidSettings()
        if (policy.platform is SignumPlatformPolicy.AndroidKeystore) {
            if (info.isUserConfirmationRequired != settings.userConfirmationRequired ||
                info.isTrustedUserPresenceRequired != settings.userPresenceRequired ||
                info.keyValidityStart?.time != settings.validFromEpochMillis ||
                info.keyValidityForOriginationEnd?.time != settings.validUntilEpochMillis) {
                throw SignumKeyPolicyMismatchException(alias, "native usage constraints differ from the policy")
            }
            if (Build.VERSION.SDK_INT >= 31 && settings.maxUsageCount != null &&
                info.remainingUsageCount !in 0..settings.maxUsageCount) {
                throw SignumKeyPolicyMismatchException(alias, "native usage limit was not observed")
            }
        }
        if (policy.authentication == SignumAuthenticationPolicy.None && info.isUserAuthenticationRequired) {
            throw SignumKeyPolicyMismatchException(alias, "native authorization was unexpectedly required")
        }
        if (settings.strongBox == SignumHardwarePolicy.REQUIRED &&
            (Build.VERSION.SDK_INT < 31 || info.securityLevel != KeyProperties.SECURITY_LEVEL_STRONGBOX)) {
            throw SignumKeyPolicyMismatchException(alias, "StrongBox could not be independently verified")
        }
        (policy.authentication as? SignumAuthenticationPolicy.UserPresence)?.let { auth ->
            val timeoutMatches = if (auth.timeoutSeconds == 0) info.userAuthenticationValidityDurationSeconds in setOf(-1, 0)
                else info.userAuthenticationValidityDurationSeconds == auth.timeoutSeconds
            if (!info.isUserAuthenticationRequired || info.userAuthenticationType != auth.androidAuthenticationTypes() || !timeoutMatches ||
                (auth.biometric && !auth.deviceCredential && info.isInvalidatedByBiometricEnrollment != !auth.allowNewBiometrics)) {
                throw SignumKeyPolicyMismatchException(alias, "native authentication factors differ from the policy")
            }
        }
        validateAndroidNativePolicy(
            alias = alias,
            policy = policy,
            isInsideSecureHardware = info.isInsideSecureHardware,
            securityLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) info.securityLevel else null,
            isUserAuthenticationRequired = info.isUserAuthenticationRequired,
            userAuthenticationValidityDurationSeconds = info.userAuthenticationValidityDurationSeconds,
            isInvalidatedByBiometricEnrollment = info.isInvalidatedByBiometricEnrollment,
            userAuthenticationType = info.userAuthenticationType,
        )
    }

    private fun requireInteractionContext(alias: String): FragmentActivity {
        return availableInteractionContext()
            ?: throw SignumInteractionContextUnavailableException(
                "A resumed FragmentActivity is required to use protected Signum key $alias",
            )
    }

    private fun availableInteractionContext(): FragmentActivity? {
        val activity = interactionContextProvider()
        if (activity == null || activity.isFinishing || activity.isDestroyed || activity.isChangingConfigurations ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            return null
        }
        return activity
    }
}

internal fun validateAndroidNativePolicy(
    alias: String,
    policy: SignumKeyPolicy,
    isInsideSecureHardware: Boolean,
    securityLevel: Int?,
    isUserAuthenticationRequired: Boolean,
    userAuthenticationValidityDurationSeconds: Int,
    isInvalidatedByBiometricEnrollment: Boolean,
    userAuthenticationType: Int,
) {
    if (policy.hardware == SignumHardwarePolicy.REQUIRED) {
        if (!isInsideSecureHardware) {
            throw SignumKeyPolicyMismatchException(alias, "the native key is not backed by secure hardware")
        }
        if (securityLevel != null && securityLevel !in setOf(
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,
                KeyProperties.SECURITY_LEVEL_STRONGBOX,
                KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE,
            )
        ) {
            throw SignumKeyPolicyMismatchException(alias, "the native key is not backed by a hardware security level")
        }
    }
    if (policy.authentication.isBiometricCurrentSetEveryUse()) {
        if (!isUserAuthenticationRequired ||
            userAuthenticationValidityDurationSeconds !in setOf(-1, 0) ||
            !isInvalidatedByBiometricEnrollment
        ) {
            throw SignumKeyPolicyMismatchException(
                alias,
                "the native key does not require biometric authentication for every use",
            )
        }
        if (userAuthenticationType != KeyProperties.AUTH_BIOMETRIC_STRONG) {
            throw SignumKeyPolicyMismatchException(alias, "the native key does not require BIOMETRIC_STRONG")
        }
    }
    if (policy.authentication.isBiometricTimedReuse()) {
        val timeoutSeconds = (policy.authentication as SignumAuthenticationPolicy.UserPresence).timeoutSeconds
        if (!isUserAuthenticationRequired ||
            userAuthenticationValidityDurationSeconds != timeoutSeconds ||
            isInvalidatedByBiometricEnrollment
        ) {
            throw SignumKeyPolicyMismatchException(
                alias,
                "the native key does not enforce the requested biometric authorization reuse interval",
            )
        }
        if (userAuthenticationType != KeyProperties.AUTH_BIOMETRIC_STRONG) {
            throw SignumKeyPolicyMismatchException(alias, "the native key does not require BIOMETRIC_STRONG")
        }
    }
}

/**
 * Converts an expired timed-reuse operation with no viable AndroidX prompt host into the stable
 * wallet boundary failure before a provider-specific exception can escape.
 */
internal fun Throwable.mapTimedReuseInteractionContextFailure(alias: String, hasInteractionContext: Boolean): Throwable {
    if (hasInteractionContext || this is CancellationException || this is SignumKeyInvalidatedException) return this
    val causes = generateSequence(this) { it.cause }.toList()
    return if (causes.any { it is UserNotAuthenticatedException || it is UnsupportedOperationException }) {
        SignumInteractionContextUnavailableException(
            "A resumed FragmentActivity is required to reauthorize timed Signum key $alias",
            this,
        )
    } else {
        this
    }
}

internal fun Throwable.mapSignumFailure(alias: String): Throwable {
    if (this is CancellationException) return this
    val causes = generateSequence(this) { it.cause }.toList()
    return when {
        causes.any { it is android.security.keystore.KeyPermanentlyInvalidatedException } ->
            SignumKeyInvalidatedException(alias, this)
        causes.any { it is NoSuchElementException } -> SignumKeyNotFoundException(alias, this)
        else -> this
    }
}

private fun KeySpec.isSupportedSignumSpec(): Boolean = when (this) {
    is KeySpec.Ec -> curve == EcCurve.P256 || curve == EcCurve.P384 || curve == EcCurve.P521
    is KeySpec.Rsa -> bits == 2048 || bits == 3072 || bits == 4096
    else -> false
}

@Suppress("DEPRECATION")
private fun PlatformSigningProviderSigner<*, *>.observedProtection(): SignumProtectionLevel =
    (this as? AndroidKeystoreSigner)?.keyInfo?.let {
        if (it.isInsideSecureHardware) SignumProtectionLevel.HARDWARE else SignumProtectionLevel.SOFTWARE
    } ?: SignumProtectionLevel.UNKNOWN

@Suppress("DEPRECATION")
private fun PlatformSigningProviderSigner<*, *>.observedSecurityLevel(): SignumSecurityLevel {
    val info = (this as? AndroidKeystoreSigner)?.keyInfo ?: return SignumSecurityLevel.UNKNOWN
    if (Build.VERSION.SDK_INT < 31) return if (info.isInsideSecureHardware) SignumSecurityLevel.UNKNOWN else SignumSecurityLevel.SOFTWARE
    return when (info.securityLevel) {
        KeyProperties.SECURITY_LEVEL_STRONGBOX -> SignumSecurityLevel.STRONGBOX
        KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> SignumSecurityLevel.TRUSTED_ENVIRONMENT
        KeyProperties.SECURITY_LEVEL_SOFTWARE -> SignumSecurityLevel.SOFTWARE
        else -> SignumSecurityLevel.UNKNOWN
    }
}
