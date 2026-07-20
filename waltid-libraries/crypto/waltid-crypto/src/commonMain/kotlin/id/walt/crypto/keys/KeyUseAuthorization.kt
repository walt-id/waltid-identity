package id.walt.crypto.keys

import kotlinx.serialization.Serializable

/**
 * Immutable authorization policy enforced when a private key is used.
 *
 * The policy is selected when a key is created. Changing a wallet default does not change an
 * existing key's policy.
 */
@Serializable
public enum class KeyUseAuthorizationPolicy {
    /** Private-key operations keep their current non-interactive behavior. */
    None,

    /**
     * Every private-key signing operation requires a currently enrolled biometric.
     *
     * Device credentials are not accepted, and biometric enrollment changes make the key unusable.
     */
    BiometricCurrentSet,
}

/** Localized text shown by the operating-system-owned key authorization prompt. */
@Serializable
public data class KeyUseAuthorizationPrompt(
    /** Reason shown to the user when authorizing a signing operation. */
    public val message: String = "Please authorize cryptographic signature",
    /** Cancellation action text where the platform supports configuring it. */
    public val cancelText: String = "Cancel",
)

/** Stable failure reasons exposed for protected key creation and use. */
@Serializable
public enum class KeyUseAuthorizationFailure {
    UnsupportedCombination,
    BiometricUnavailable,
    BiometricNotEnrolled,
    InteractionContextUnavailable,
    AuthorizationFailed,
    ProtectedKeyInvalidated,
    ProtectedKeyMissing,
}

/** Stable protected-key failure without platform exception-message parsing. */
public class KeyUseAuthorizationException(
    public val failure: KeyUseAuthorizationFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Effective private-key backing determined from platform key information. */
@Serializable
public enum class KeyHardwareBacking {
    Software,
    Platform,
    SecureHardware,
    TrustedEnvironment,
    StrongBox,
    SecureEnclave,
    Unknown,
}

/** Non-secret policy and backing metadata carried by mobile platform key implementations. */
public interface KeyUseAuthorizationAware {
    public val keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy
    public val isPlatformBacked: Boolean
    public suspend fun effectiveHardwareBacking(): KeyHardwareBacking
}
