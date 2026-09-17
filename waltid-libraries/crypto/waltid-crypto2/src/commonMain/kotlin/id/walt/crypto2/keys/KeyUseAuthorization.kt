package id.walt.crypto2.keys

import kotlinx.serialization.Serializable

/** Immutable authorization policy selected when a wallet key is created. */
@Serializable
public sealed interface KeyUseAuthorizationPolicy {
    /** Private-key operations retain their ordinary non-interactive behavior. */
    @Serializable
    @kotlinx.serialization.SerialName("id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy.None")
    public data object None : KeyUseAuthorizationPolicy

    /** Every private-key use accepts an enrolled strong biometric without binding to the current set.
     * On iOS, protected-key access is deferred while biometrics are unavailable to avoid
     * enrollment-reset query failures. Accepting new enrollment is not a recovery guarantee. */
    @Serializable
    @kotlinx.serialization.SerialName("id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy.BiometricAny")
    public data object BiometricAny : KeyUseAuthorizationPolicy

    /** Requires the device PIN/passcode/password, with optional bounded reuse.
     * @property timeoutSeconds Fixed non-sliding reuse interval from 0 through 30; zero requires authorization for each use. */
    @Serializable
    @kotlinx.serialization.SerialName("id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy.DeviceCredential")
    public data class DeviceCredential(public val timeoutSeconds: Int = 0) : KeyUseAuthorizationPolicy {
        init { require(timeoutSeconds in 0..30) { "Authorization reuse must be between 0 and 30 seconds" } }
    }

    /** Accepts either a strong biometric or the device credential; enrollment changes do not invalidate it.
     * @property timeoutSeconds Fixed non-sliding reuse interval from 0 through 30; zero requires authorization for each use. */
    @Serializable
    @kotlinx.serialization.SerialName("id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy.BiometricOrDeviceCredential")
    public data class BiometricOrDeviceCredential(public val timeoutSeconds: Int = 0) : KeyUseAuthorizationPolicy {
        init { require(timeoutSeconds in 0..30) { "Authorization reuse must be between 0 and 30 seconds" } }
    }

    /** Every private-key operation requires a currently enrolled strong biometric. */
    @Serializable
    @kotlinx.serialization.SerialName("id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy.BiometricCurrentSet")
    public data object BiometricCurrentSet : KeyUseAuthorizationPolicy

    /**
     * Strong biometric authorization may be reused for private-key operations during the fixed
     * interval. The interval starts with successful authentication and never slides on signing.
     *
     * This policy accepts new enrollment and uses the iOS availability guard described by [BiometricAny].
     * Android can independently read back
     * this interval from native KeyStore metadata after creation or restoration. iOS enforces the interval
     * through a fixed LocalAuthentication context lifetime; native Keychain metadata does not expose
     * that reuse interval for independent readback.
     *
     * Timed reuse is recent platform or provider authentication. It is not authorization or
     * consent for issuance, presentation, or another wallet action. Android reuse may cover other
     * eligible keys; iOS contexts are scoped to a key within the provider process.
     */
    @Serializable
    @kotlinx.serialization.SerialName("id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy.BiometricTimedReuse")
    public data class BiometricTimedReuse(
        /** Fixed, non-sliding strong-biometric reuse interval in seconds, from 1 through 30. */
        public val timeoutSeconds: Int,
    ) : KeyUseAuthorizationPolicy {
        init {
            require(timeoutSeconds in 1..30) {
                "Biometric authorization reuse timeout must be between 1 and 30 seconds"
            }
        }
    }
}

/** Stable failure reasons exposed by the mobile wallet boundary. */
@Serializable
public enum class KeyUseAuthorizationFailure {
    UnsupportedCombination,
    BiometricUnavailable,
    BiometricNotEnrolled,
    /** The device has no PIN, passcode or password configured. */
    DeviceCredentialNotSet,
    InteractionContextUnavailable,
    AuthorizationNotCompleted,
    ProtectedKeyUnavailable,
    InvalidStoredKeyMetadata,
}

/** A stable wallet failure that does not require platform exception-message parsing. */
public class KeyUseAuthorizationException(
    /** Stable wallet-facing reason for the failure. */
    public val failure: KeyUseAuthorizationFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Result of checking whether the wallet can satisfy the requested key and authorization requirements. */
public sealed interface KeyUseAuthorizationSupport {
    /** The wallet can satisfy the requested key and authorization requirements. */
    public data class Supported(
        /** Authorization policy that will be effective for the created key. */
        public val effectivePolicy: KeyUseAuthorizationPolicy,
        /** How a timed-reuse interval is enforced, when one is selected. */
        public val reuseEnforcement: KeyUseAuthorizationReuseEnforcement? = null,
        /** How a timed-reuse interval can be validated after key creation or restoration. */
        public val timeoutValidation: KeyUseAuthorizationReuseTimeoutValidation? = null,
    ) : KeyUseAuthorizationSupport {
        init {
            val timed = effectivePolicy.reuseSeconds > 0
            require((reuseEnforcement != null) == timed && (timeoutValidation != null) == timed) {
                "Timed support must include enforcement and timeout validation only for timed policy"
            }
        }
    }

    /** The wallet cannot satisfy the requested capability set. */
    public data class Unsupported(
        /** Reason the requested requirements cannot currently be satisfied. */
        public val reason: KeyUseAuthorizationUnsupportedReason,
    ) : KeyUseAuthorizationSupport
}

/** Distinguishes platform-keystore and provider-process enforcement for timed authorization reuse. */
@Serializable
public enum class KeyUseAuthorizationReuseEnforcement {
    /** The native key store enforces the authorization validity interval. */
    PlatformKeyStore,

    /** The platform crypto provider reuses authenticated process-local authorization state. */
    ProviderProcess,
}

/** How a timed-reuse interval can be validated after key creation or restoration. */
@Serializable
public enum class KeyUseAuthorizationReuseTimeoutValidation {
    /** Native metadata can be read back independently and compared to the requested interval. */
    IndependentReadback,

    /** The requested interval can be passed to the provider but cannot be independently read back. */
    ProviderConfigurationOnly,
}

/** Reasons a requested key-creation requirement cannot currently be satisfied. */
public enum class KeyUseAuthorizationUnsupportedReason {
    UnsupportedCombination,
    BiometricUnavailable,
    BiometricNotEnrolled,
    /** The device has no PIN, passcode or password configured. */
    DeviceCredentialNotSet,
}

public fun KeyUseAuthorizationUnsupportedReason.toAuthorizationFailure(): KeyUseAuthorizationFailure = when (this) {
    KeyUseAuthorizationUnsupportedReason.UnsupportedCombination -> KeyUseAuthorizationFailure.UnsupportedCombination
    KeyUseAuthorizationUnsupportedReason.BiometricUnavailable -> KeyUseAuthorizationFailure.BiometricUnavailable
    KeyUseAuthorizationUnsupportedReason.BiometricNotEnrolled -> KeyUseAuthorizationFailure.BiometricNotEnrolled
    KeyUseAuthorizationUnsupportedReason.DeviceCredentialNotSet -> KeyUseAuthorizationFailure.DeviceCredentialNotSet
}

/** Fixed authorization reuse interval, or zero when each operation requires fresh approval. */
public val KeyUseAuthorizationPolicy.reuseSeconds: Int get() = when (this) {
    is KeyUseAuthorizationPolicy.BiometricTimedReuse -> timeoutSeconds
    is KeyUseAuthorizationPolicy.DeviceCredential -> timeoutSeconds
    is KeyUseAuthorizationPolicy.BiometricOrDeviceCredential -> timeoutSeconds
    else -> 0
}
