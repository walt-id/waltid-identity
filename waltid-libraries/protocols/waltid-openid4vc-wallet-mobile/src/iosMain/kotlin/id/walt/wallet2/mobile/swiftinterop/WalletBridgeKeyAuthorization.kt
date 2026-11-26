@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile.swiftinterop

import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.KeyUseAuthorizationReuseEnforcement
import id.walt.crypto2.keys.KeyUseAuthorizationReuseTimeoutValidation
import id.walt.crypto2.keys.KeyUseAuthorizationSupport
import id.walt.crypto2.keys.KeyUseAuthorizationUnsupportedReason
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Swift-bridge representation of the authorization policy selected for a newly created key.
 *
 * @property type Selected authorization-policy variant.
 * @property timeoutSeconds Fixed timed-reuse interval in seconds, only for [WalletBridgeKeyUseAuthorizationPolicyType.BiometricTimedReuse].
 */
@Serializable
public data class WalletBridgeKeyUseAuthorizationPolicy(
    public val type: WalletBridgeKeyUseAuthorizationPolicyType,
    public val timeoutSeconds: Int? = null,
) {
    init {
        when (type) {
            WalletBridgeKeyUseAuthorizationPolicyType.BiometricTimedReuse ->
                require(timeoutSeconds != null && timeoutSeconds in 1..30) {
                    "Timed biometric reuse timeout must be between 1 and 30 seconds"
                }

            WalletBridgeKeyUseAuthorizationPolicyType.DeviceCredential,
            WalletBridgeKeyUseAuthorizationPolicyType.BiometricOrDeviceCredential -> require(timeoutSeconds != null && timeoutSeconds in 0..30)
            WalletBridgeKeyUseAuthorizationPolicyType.BiometricAny,
            WalletBridgeKeyUseAuthorizationPolicyType.None,
            WalletBridgeKeyUseAuthorizationPolicyType.BiometricCurrentSet ->
                require(timeoutSeconds == null) { "Only timed biometric reuse accepts a timeout" }
        }
    }

    /** Predefined bridge policies that do not require a timeout. */
    public companion object {
        /** Ordinary non-interactive private-key operations. */
        public val None: WalletBridgeKeyUseAuthorizationPolicy =
            WalletBridgeKeyUseAuthorizationPolicy(WalletBridgeKeyUseAuthorizationPolicyType.None)
        /** Strong biometric authentication for every private-key operation. */
        public val BiometricCurrentSet: WalletBridgeKeyUseAuthorizationPolicy =
            WalletBridgeKeyUseAuthorizationPolicy(WalletBridgeKeyUseAuthorizationPolicyType.BiometricCurrentSet)
    }
}

/** Variants supported by [WalletBridgeKeyUseAuthorizationPolicy]. */
@Serializable
public enum class WalletBridgeKeyUseAuthorizationPolicyType {
    None,
    BiometricCurrentSet,
    BiometricTimedReuse,
    BiometricAny,
    DeviceCredential,
    BiometricOrDeviceCredential,
}

/** Swift-friendly result of a key-use authorization preflight. */
@ConsistentCopyVisibility
@Serializable
public data class WalletBridgeKeyPreflight internal constructor(
    /** Whether the requested authorization policy is supported with the reported metadata. */
    public val supported: Boolean,
    /** The authorization policy that will be effective when [supported] is true. */
    public val effectivePolicy: WalletBridgeKeyUseAuthorizationPolicy? = null,
    /** The enforcement boundary for timed reuse, when a timed policy is supported. */
    public val reuseEnforcement: WalletBridgeKeyUseAuthorizationReuseEnforcement? = null,
    /** How a timed-reuse interval can be validated after key creation or restoration. */
    public val timeoutValidation: WalletBridgeKeyUseAuthorizationReuseTimeoutValidation? = null,
    /** Core preflight reason when the request is unsupported. */
    public val failure: KeyUseAuthorizationUnsupportedReason? = null,
) {
    init {
        require(supported == (failure == null) && supported == (effectivePolicy != null)) {
            "Wallet bridge preflight must include an effective policy exactly when supported"
        }
        val timed = (effectivePolicy?.timeoutSeconds ?: 0) > 0
        require((reuseEnforcement != null) == timed && (timeoutValidation != null) == timed) {
            "Timed bridge preflight must include enforcement and timeout validation only for timed policy"
        }
    }
}

/** Enforcement boundary reported for a supported timed-reuse key. */
@Serializable
public enum class WalletBridgeKeyUseAuthorizationReuseEnforcement {
    PlatformKeyStore,
    ProviderProcess,
}

/** Timeout-validation capability reported for a supported timed-reuse key. */
@Serializable
public enum class WalletBridgeKeyUseAuthorizationReuseTimeoutValidation {
    IndependentReadback,
    ProviderConfigurationOnly,
}

internal fun WalletBridgeKeyUseAuthorizationPolicy.toCorePolicy(): KeyUseAuthorizationPolicy = when (type) {
    WalletBridgeKeyUseAuthorizationPolicyType.BiometricAny -> KeyUseAuthorizationPolicy.BiometricAny
    WalletBridgeKeyUseAuthorizationPolicyType.DeviceCredential -> KeyUseAuthorizationPolicy.DeviceCredential(requireNotNull(timeoutSeconds))
    WalletBridgeKeyUseAuthorizationPolicyType.BiometricOrDeviceCredential -> KeyUseAuthorizationPolicy.BiometricOrDeviceCredential(requireNotNull(timeoutSeconds))
    WalletBridgeKeyUseAuthorizationPolicyType.None -> KeyUseAuthorizationPolicy.None
    WalletBridgeKeyUseAuthorizationPolicyType.BiometricCurrentSet -> KeyUseAuthorizationPolicy.BiometricCurrentSet
    WalletBridgeKeyUseAuthorizationPolicyType.BiometricTimedReuse ->
        KeyUseAuthorizationPolicy.BiometricTimedReuse(requireNotNull(timeoutSeconds))
}

internal fun KeyUseAuthorizationPolicy.toBridgePolicy(): WalletBridgeKeyUseAuthorizationPolicy = when (this) {
    KeyUseAuthorizationPolicy.BiometricAny -> WalletBridgeKeyUseAuthorizationPolicy(WalletBridgeKeyUseAuthorizationPolicyType.BiometricAny)
    is KeyUseAuthorizationPolicy.DeviceCredential -> WalletBridgeKeyUseAuthorizationPolicy(WalletBridgeKeyUseAuthorizationPolicyType.DeviceCredential, timeoutSeconds)
    is KeyUseAuthorizationPolicy.BiometricOrDeviceCredential -> WalletBridgeKeyUseAuthorizationPolicy(WalletBridgeKeyUseAuthorizationPolicyType.BiometricOrDeviceCredential, timeoutSeconds)
    KeyUseAuthorizationPolicy.None -> WalletBridgeKeyUseAuthorizationPolicy.None
    KeyUseAuthorizationPolicy.BiometricCurrentSet -> WalletBridgeKeyUseAuthorizationPolicy.BiometricCurrentSet
    is KeyUseAuthorizationPolicy.BiometricTimedReuse -> WalletBridgeKeyUseAuthorizationPolicy(
        type = WalletBridgeKeyUseAuthorizationPolicyType.BiometricTimedReuse,
        timeoutSeconds = timeoutSeconds,
    )
}

internal fun KeyUseAuthorizationSupport.toBridgeModel(): WalletBridgeKeyPreflight = when (this) {
    is KeyUseAuthorizationSupport.Supported -> WalletBridgeKeyPreflight(
        supported = true,
        effectivePolicy = effectivePolicy.toBridgePolicy(),
        reuseEnforcement = reuseEnforcement?.toBridgeModel(),
        timeoutValidation = timeoutValidation?.toBridgeModel(),
    )
    is KeyUseAuthorizationSupport.Unsupported -> WalletBridgeKeyPreflight(supported = false, failure = reason)
}

internal fun KeyUseAuthorizationReuseEnforcement.toBridgeModel(): WalletBridgeKeyUseAuthorizationReuseEnforcement = when (this) {
    KeyUseAuthorizationReuseEnforcement.PlatformKeyStore ->
        WalletBridgeKeyUseAuthorizationReuseEnforcement.PlatformKeyStore
    KeyUseAuthorizationReuseEnforcement.ProviderProcess ->
        WalletBridgeKeyUseAuthorizationReuseEnforcement.ProviderProcess
}

internal fun KeyUseAuthorizationReuseTimeoutValidation.toBridgeModel():
    WalletBridgeKeyUseAuthorizationReuseTimeoutValidation = when (this) {
    KeyUseAuthorizationReuseTimeoutValidation.IndependentReadback ->
        WalletBridgeKeyUseAuthorizationReuseTimeoutValidation.IndependentReadback
    KeyUseAuthorizationReuseTimeoutValidation.ProviderConfigurationOnly ->
        WalletBridgeKeyUseAuthorizationReuseTimeoutValidation.ProviderConfigurationOnly
}
