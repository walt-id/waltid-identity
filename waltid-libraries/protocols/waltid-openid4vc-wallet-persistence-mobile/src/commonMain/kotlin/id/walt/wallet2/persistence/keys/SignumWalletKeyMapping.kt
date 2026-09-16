package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.HardwarePreference
import id.walt.crypto2.keys.KeyAttestation
import id.walt.crypto2.keys.KeyAuthorizationEvidence
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.keys.KeyOrigin
import id.walt.crypto2.keys.KeyProtectionLevel
import id.walt.crypto2.keys.KeySecurityLevel
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.KeychainAccessibility
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.PlatformKeyConfiguration
import id.walt.crypto2.keys.PrivateKeyExporter
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.signum.SignumAuthenticationPolicy
import id.walt.crypto2.signum.SignumAuthorizationException
import id.walt.crypto2.signum.SignumHardwarePolicy
import id.walt.crypto2.signum.SignumInteractionContextUnavailableException
import id.walt.crypto2.signum.SignumKeyUnavailableException
import id.walt.crypto2.signum.SignumKeyInvalidatedException
import id.walt.crypto2.signum.SignumKeyNotFoundException
import id.walt.crypto2.signum.SignumKeyOrigin
import id.walt.crypto2.signum.SignumKeyPolicy
import id.walt.crypto2.signum.SignumKeyPolicyMismatchException
import id.walt.crypto2.signum.SignumKeychainAccessibility
import id.walt.crypto2.signum.SignumManagedKey
import id.walt.crypto2.signum.SignumPlatformPolicy
import id.walt.crypto2.signum.SignumProtectionLevel
import id.walt.crypto2.signum.SignumSecurityLevel
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

    is SignumKeyUnavailableException,
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

    is SignumAuthorizationException,
    is SignumUserCancelledException ->
        KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.AuthorizationNotCompleted,
            message = "Protected-key authorization was not completed",
            cause = this,
        )

    else -> null
}

/** Gives signing and eligible private-key export the same mobile failure vocabulary. */
internal fun ManagedKey.withWalletAuthorizationMapping(
    authorizationAvailabilityFailure: () -> KeyUseAuthorizationFailure? = { null },
): ManagedKey {
    val delegate = this
    return object : ManagedKey {
        override val storedKey = delegate.storedKey
        override val capabilities = delegate.capabilities.copy(
            signer = delegate.capabilities.signer?.let { signer ->
                Signer { data, algorithm -> mapKeyFailure(storedKey.id.value, authorizationAvailabilityFailure) { signer.sign(data, algorithm) } }
            },
            privateKeyExporter = delegate.capabilities.privateKeyExporter?.let { exporter ->
                object : PrivateKeyExporter {
                    override suspend fun exportPrivateKey(): EncodedKey =
                        mapKeyFailure(storedKey.id.value, authorizationAvailabilityFailure) { exporter.exportPrivateKey() }
                    override suspend fun exportPrivateKey(format: KeyEncodingFormat): EncodedKey =
                        mapKeyFailure(storedKey.id.value, authorizationAvailabilityFailure) { exporter.exportPrivateKey(format) }
                }
            },
        )
    }
}

private suspend inline fun <T> mapKeyFailure(
    keyId: String,
    authorizationAvailabilityFailure: () -> KeyUseAuthorizationFailure?,
    operation: () -> T,
): T = try {
    operation()
} catch (cause: Throwable) {
    // Stable Signum can collapse missing enrollment and cancellation into the same failure.
    // Consult availability only after authorization fails; valid native reuse still succeeds.
    val unavailable = if (cause is SignumAuthorizationException || cause is SignumUserCancelledException) {
        authorizationAvailabilityFailure()
    } else null
    throw unavailable?.let {
        KeyUseAuthorizationException(it, "Required key-use authentication is unavailable", cause)
    } ?: cause.toKeyUseAuthorizationException(keyId) ?: cause
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
    val authorization = authorizationPolicy.toSignumPolicy(prompt)
    return authorization.copy(hardware = when (protection) {
        WalletKeyProtection.PlatformDefault -> authorization.hardware
        WalletKeyProtection.HardwareRequired -> SignumHardwarePolicy.REQUIRED
        WalletKeyProtection.HardwarePreferred -> SignumHardwarePolicy.PREFERRED
        WalletKeyProtection.NativeStorage -> SignumHardwarePolicy.DISCOURAGED
    }, platform = platform.toSignumPlatformPolicy(), attestationChallenge = attestationChallenge)
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

/** Keeps provider types out of the mobile configuration and facts. */
internal fun PlatformKeyConfiguration.toSignumPlatformPolicy(): SignumPlatformPolicy = when (this) {
    PlatformKeyConfiguration.Default -> SignumPlatformPolicy.Default
    is PlatformKeyConfiguration.AndroidKeystore -> SignumPlatformPolicy.AndroidKeystore(
        strongBox = when (strongBox) {
            HardwarePreference.REQUIRED -> SignumHardwarePolicy.REQUIRED
            HardwarePreference.PREFERRED -> SignumHardwarePolicy.PREFERRED
            HardwarePreference.DISCOURAGED -> SignumHardwarePolicy.DISCOURAGED
        }, unlockedDeviceRequired = unlockedDeviceRequired,
        userConfirmationRequired = userConfirmationRequired, userPresenceRequired = userPresenceRequired,
        maxUsageCount = maxUsageCount, validFromEpochMillis = validFromEpochMillis, validUntilEpochMillis = validUntilEpochMillis,
        attestKeyAlias = attestKeyAlias,
    )
    is PlatformKeyConfiguration.IosKeychain -> SignumPlatformPolicy.IosKeychain(
        accessibility = when (accessibility) {
            KeychainAccessibility.WHEN_UNLOCKED -> SignumKeychainAccessibility.WHEN_UNLOCKED
            KeychainAccessibility.AFTER_FIRST_UNLOCK -> SignumKeychainAccessibility.AFTER_FIRST_UNLOCK
            KeychainAccessibility.WHEN_UNLOCKED_DEVICE_ONLY -> SignumKeychainAccessibility.WHEN_UNLOCKED_DEVICE_ONLY
            KeychainAccessibility.AFTER_FIRST_UNLOCK_DEVICE_ONLY -> SignumKeychainAccessibility.AFTER_FIRST_UNLOCK_DEVICE_ONLY
            KeychainAccessibility.WHEN_PASSCODE_SET_DEVICE_ONLY -> SignumKeychainAccessibility.WHEN_PASSCODE_SET_DEVICE_ONLY
        }, accessGroup = accessGroup,
    )
}

internal fun SignumManagedKey.toWalletKeyFacts(
    authorizationEvidence: KeyAuthorizationEvidence,
): PlatformKeyFacts = PlatformKeyFacts(
    origin = when (origin) {
        SignumKeyOrigin.GENERATED -> KeyOrigin.GENERATED
        SignumKeyOrigin.IMPORTED -> KeyOrigin.IMPORTED
        SignumKeyOrigin.UNKNOWN -> KeyOrigin.UNKNOWN
    },
    securityLevel = when (securityLevel) {
        SignumSecurityLevel.SOFTWARE -> KeySecurityLevel.SOFTWARE
        SignumSecurityLevel.TRUSTED_ENVIRONMENT -> KeySecurityLevel.TRUSTED_ENVIRONMENT
        SignumSecurityLevel.STRONGBOX -> KeySecurityLevel.STRONGBOX
        SignumSecurityLevel.SECURE_ENCLAVE -> KeySecurityLevel.SECURE_ENCLAVE
        SignumSecurityLevel.UNKNOWN -> KeySecurityLevel.UNKNOWN
    },
    protection = when (protectionLevel) {
        SignumProtectionLevel.HARDWARE -> KeyProtectionLevel.HARDWARE
        SignumProtectionLevel.SOFTWARE -> KeyProtectionLevel.SOFTWARE
        SignumProtectionLevel.UNKNOWN -> KeyProtectionLevel.UNKNOWN
    },
    attestation = attestation?.let { KeyAttestation(it.format, it.statement, it.certificateChain) },
    authorizationEvidence = authorizationEvidence,
)
