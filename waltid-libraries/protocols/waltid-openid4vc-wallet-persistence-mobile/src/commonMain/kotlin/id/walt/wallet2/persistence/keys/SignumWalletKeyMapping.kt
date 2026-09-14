package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.signum.SignumInteractionContextUnavailableException
import id.walt.crypto2.signum.SignumKeyInvalidatedException
import id.walt.crypto2.signum.SignumKeyNotFoundException
import id.walt.crypto2.signum.SignumKeyPolicyMismatchException
import id.walt.crypto2.signum.SignumKeyPolicy
import id.walt.crypto2.signum.SignumHardwarePolicy
import id.walt.crypto2.signum.SignumAuthenticationPolicy
import id.walt.crypto2.signum.SignumStoredKeyMetadataException
import id.walt.crypto2.signum.SignumUserCancelledException

/**
 * Translates only the typed Signum failures understood by the built-in mobile adapters.
 * Unknown failures remain provider-specific and cross the adapter boundary unchanged.
 */
internal fun Throwable.toKeyUseAuthorizationException(
    protectedKeyId: String? = null,
    policyMismatchFailure: KeyUseAuthorizationFailure =
        KeyUseAuthorizationFailure.ProtectedKeyUnavailable,
): KeyUseAuthorizationException? = when (this) {
    is SignumInteractionContextUnavailableException ->
        KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.InteractionContextUnavailable,
            message = "Protected key interaction context is unavailable",
            cause = this,
        )

    is SignumKeyPolicyMismatchException ->
        KeyUseAuthorizationException(
            failure = policyMismatchFailure,
            message = when (policyMismatchFailure) {
                KeyUseAuthorizationFailure.UnsupportedCombination ->
                    "The platform could not enforce the requested key policy"
                else ->
                    "Protected key${protectedKeyId?.let { " '$it'" }.orEmpty()} is unavailable"
            },
            cause = this,
        )

    is SignumKeyInvalidatedException,
    is SignumKeyNotFoundException ->
        KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.ProtectedKeyUnavailable,
            message = "Protected key${protectedKeyId?.let { " '$it'" }.orEmpty()} is unavailable",
            cause = this,
        )

    is SignumStoredKeyMetadataException ->
        KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.InvalidStoredKeyMetadata,
            message = "Stored managed-key metadata is invalid",
            cause = this,
        )

    is SignumUserCancelledException ->
        KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.AuthorizationNotCompleted,
            message = "Protected-key authorization was not completed",
            cause = this,
        )

    else -> null
}

/** Wraps protected managed-key signing with the wallet's stable authorization failures. */
internal fun ManagedKey.withWalletAuthorizationMapping(
    authorizationPolicy: KeyUseAuthorizationPolicy,
): ManagedKey {
    if (authorizationPolicy is KeyUseAuthorizationPolicy.None) {
        return this
    }

    val delegate = this
    val storedKey = delegate.storedKey
    return object : ManagedKey {
        override val storedKey = delegate.storedKey
        override val capabilities = delegate.capabilities.copy(
            signer = delegate.capabilities.signer?.let { signer ->
                Signer { data, algorithm ->
                    try {
                        signer.sign(data, algorithm)
                    } catch (cause: Throwable) {
                        throw cause.toKeyUseAuthorizationException(storedKey.id.value) ?: cause
                    }
                }
            },
        )
    }
}

/** Interprets persisted Signum policy only when the complete wallet protected-key shape matches. */
internal fun SignumKeyPolicy.toWalletPolicy(stored: StoredKey.Managed): KeyUseAuthorizationPolicy {
    val authentication = authentication
    return when {
    authentication == SignumAuthenticationPolicy.None -> KeyUseAuthorizationPolicy.None
    stored.spec == KeySpec.Ec(EcCurve.P256) &&
        stored.usages == setOf(KeyUsage.SIGN, KeyUsage.VERIFY) &&
        authentication.isWalletBiometricCurrentSet() -> KeyUseAuthorizationPolicy.BiometricCurrentSet
    stored.spec == KeySpec.Ec(EcCurve.P256) &&
        stored.usages == setOf(KeyUsage.SIGN, KeyUsage.VERIFY) &&
        authentication.isWalletBiometricTimedReuse() -> KeyUseAuthorizationPolicy.BiometricTimedReuse(
        requireNotNull(authentication as? SignumAuthenticationPolicy.UserPresence).timeoutSeconds,
    )
    authentication is SignumAuthenticationPolicy.UserPresence && authentication.timeoutSeconds in 0..30 -> when {
        authentication.biometric && authentication.deviceCredential && authentication.allowNewBiometrics ->
            KeyUseAuthorizationPolicy.BiometricOrDeviceCredential(authentication.timeoutSeconds)
        !authentication.biometric && authentication.deviceCredential -> KeyUseAuthorizationPolicy.DeviceCredential(authentication.timeoutSeconds)
        authentication.biometric && authentication.allowNewBiometrics && authentication.timeoutSeconds == 0 -> KeyUseAuthorizationPolicy.BiometricAny
        else -> throw KeyUseAuthorizationException(KeyUseAuthorizationFailure.InvalidStoredKeyMetadata, "Unsupported authentication factors")
    }
    else -> throw KeyUseAuthorizationException(
        KeyUseAuthorizationFailure.InvalidStoredKeyMetadata,
        "Stored Signum key uses an unsupported wallet authorization policy",
    )
}

}

/** Maps the complete immutable wallet policy to the protected Signum key policy. */
internal fun KeyUseAuthorizationPolicy.toSignumPolicy(
    prompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt(),
): SignumKeyPolicy = when (this) {
    KeyUseAuthorizationPolicy.None -> SignumKeyPolicy()
    KeyUseAuthorizationPolicy.BiometricAny -> SignumKeyPolicy(
        hardware = SignumHardwarePolicy.REQUIRED,
        authentication = SignumAuthenticationPolicy.UserPresence(biometric = true, allowNewBiometrics = true,
            deviceCredential = false, timeoutSeconds = 0, prompt = prompt.reason, cancelText = prompt.cancelText),
    )
    is KeyUseAuthorizationPolicy.DeviceCredential -> SignumKeyPolicy(
        hardware = SignumHardwarePolicy.REQUIRED,
        authentication = SignumAuthenticationPolicy.UserPresence(biometric = false, allowNewBiometrics = false,
            deviceCredential = true, timeoutSeconds = timeoutSeconds, prompt = prompt.reason, cancelText = prompt.cancelText),
    )
    is KeyUseAuthorizationPolicy.BiometricOrDeviceCredential -> SignumKeyPolicy(
        hardware = SignumHardwarePolicy.REQUIRED,
        authentication = SignumAuthenticationPolicy.UserPresence(biometric = true, allowNewBiometrics = true,
            deviceCredential = true, timeoutSeconds = timeoutSeconds, prompt = prompt.reason, cancelText = prompt.cancelText),
    )
    KeyUseAuthorizationPolicy.BiometricCurrentSet -> SignumKeyPolicy(
        hardware = SignumHardwarePolicy.REQUIRED,
        authentication = SignumAuthenticationPolicy.UserPresence(
            biometric = true,
            allowNewBiometrics = false,
            deviceCredential = false,
            timeoutSeconds = 0,
            prompt = prompt.reason,
            cancelText = prompt.cancelText,
        ),
    )
    is KeyUseAuthorizationPolicy.BiometricTimedReuse -> SignumKeyPolicy(
        hardware = SignumHardwarePolicy.REQUIRED,
        authentication = SignumAuthenticationPolicy.UserPresence(
            biometric = true,
            allowNewBiometrics = true,
            deviceCredential = false,
            timeoutSeconds = timeoutSeconds,
            prompt = prompt.reason,
            cancelText = prompt.cancelText,
        ),
    )
}

private fun SignumAuthenticationPolicy.isWalletBiometricCurrentSet(): Boolean =
    this is SignumAuthenticationPolicy.UserPresence &&
        biometric &&
        !allowNewBiometrics &&
        !deviceCredential &&
        timeoutSeconds == 0

private fun SignumAuthenticationPolicy.isWalletBiometricTimedReuse(): Boolean =
    this is SignumAuthenticationPolicy.UserPresence &&
        biometric &&
        allowNewBiometrics &&
        !deviceCredential &&
        timeoutSeconds in 1..30

/** Maps explicitly selected protection without changing the authorization policy. */
internal fun WalletKeyRequirements.toSignumPolicy(prompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt()): SignumKeyPolicy {
    val legacy = authorizationPolicy.toSignumPolicy(prompt)
    return legacy.copy(hardware = when (protection) {
        WalletKeyProtection.PlatformDefault -> legacy.hardware
        WalletKeyProtection.HardwareRequired -> SignumHardwarePolicy.REQUIRED
        WalletKeyProtection.HardwarePreferred -> SignumHardwarePolicy.PREFERRED
        WalletKeyProtection.NativeStorage -> SignumHardwarePolicy.DISCOURAGED
    }, platform = platform, attestationChallenge = attestationChallenge)
}

internal val KeyUseAuthorizationPolicy.reuseSeconds: Int get() = when (this) {
    is KeyUseAuthorizationPolicy.BiometricTimedReuse -> timeoutSeconds
    is KeyUseAuthorizationPolicy.DeviceCredential -> timeoutSeconds
    is KeyUseAuthorizationPolicy.BiometricOrDeviceCredential -> timeoutSeconds
    else -> 0
}

internal val KeyUseAuthorizationPolicy.requiresNativeControls: Boolean get() =
    this == KeyUseAuthorizationPolicy.BiometricAny || this is KeyUseAuthorizationPolicy.DeviceCredential ||
        this is KeyUseAuthorizationPolicy.BiometricOrDeviceCredential
