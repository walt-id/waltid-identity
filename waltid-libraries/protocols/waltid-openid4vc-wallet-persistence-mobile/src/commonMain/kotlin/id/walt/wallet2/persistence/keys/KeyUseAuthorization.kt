package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import kotlinx.serialization.Serializable

/** Immutable authorization policy selected when a wallet key is created. */
@Serializable
public enum class KeyUseAuthorizationPolicy {
    /** Private-key operations retain their ordinary non-interactive behavior. */
    None,

    /** Every private-key operation requires a currently enrolled strong biometric. */
    BiometricCurrentSet,
}

/** Text shown by the operating-system-owned authorization prompt. */
@Serializable
public data class KeyUseAuthorizationPrompt(
    public val message: String = "Please authorize cryptographic signature",
    public val cancelText: String = "Cancel",
)

/** Stable failure reasons exposed by the mobile wallet boundary. */
@Serializable
public enum class KeyUseAuthorizationFailure {
    UnsupportedCombination,
    BiometricUnavailable,
    BiometricNotEnrolled,
    InteractionContextUnavailable,
    AuthorizationNotCompleted,
    ProtectedKeyUnavailable,
    InvalidStoredKeyMetadata,
}

/** A stable wallet failure that does not require platform exception-message parsing. */
public class KeyUseAuthorizationException(
    public val failure: KeyUseAuthorizationFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Provider-neutral request for one managed native key. */
public data class PlatformKeyRequest(
    public val id: KeyId,
    public val spec: KeySpec,
    public val usages: Set<KeyUsage>,
    public val authorizationPolicy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None,
    public val prompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt(),
)

/** Result of checking whether an exact request can be enforced without fallback. */
public data class PlatformKeyPreflight(
    public val supported: Boolean,
    public val failure: KeyUseAuthorizationFailure? = null,
)

/** Derived metadata for a persisted managed platform key. */
public data class PlatformManagedKeyInfo(
    public val authorizationPolicy: KeyUseAuthorizationPolicy,
    public val isPlatformBacked: Boolean = true,
)
