package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import kotlinx.serialization.Serializable

/** Immutable authorization policy selected when a wallet key is created. */
@Serializable
public enum class KeyUseAuthorizationPolicy {
    /** Private-key operations retain their ordinary non-interactive behavior. */
    None,

    /** Every private-key operation requires a currently enrolled strong biometric. */
    BiometricCurrentSet,
}

/**
 * Text used by operating-system-owned authorization UI.
 *
 * @property reason Reason displayed for the signing authorization request.
 * @property cancelText Cancellation label where platform customization is supported.
 */
@Serializable
public data class KeyUseAuthorizationPrompt(
    public val reason: String = "Please authorize cryptographic signature",
    public val cancelText: String = "Cancel",
) {
    init {
        require(reason.isNotBlank()) { "Authorization reason cannot be blank" }
        require(cancelText.isNotBlank()) { "Authorization cancel text cannot be blank" }
    }
}

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
    /** Stable wallet-facing reason for the failure. */
    public val failure: KeyUseAuthorizationFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Capabilities required from a wallet signing key.
 *
 * @property spec Cryptographic key specification.
 * @property usages Operations the key must support.
 * @property authorizationPolicy Authorization required for private-key use.
 */
public data class WalletKeyRequirements(
    public val spec: KeySpec,
    public val usages: Set<KeyUsage>,
    public val authorizationPolicy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None,
) {
    init {
        require(usages.isNotEmpty()) { "Wallet key usages cannot be empty" }
    }
}

/**
 * Request to create one wallet signing key.
 *
 * @property id Stable identifier assigned to the generated key.
 * @property requirements Capabilities the generated key must satisfy.
 * @property prompt Text used by OS-owned authorization UI.
 */
public data class WalletKeyCreationRequest(
    public val id: KeyId,
    public val requirements: WalletKeyRequirements,
    public val prompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt(),
)

/** Result of checking whether exact key requirements can be enforced without fallback. */
public sealed interface KeyUseAuthorizationSupport {
    /** The wallet can satisfy the requested key and authorization requirements. */
    public data object Supported : KeyUseAuthorizationSupport

    /** The platform cannot enforce the requested capability set. */
    public data class Unsupported(
        /** Reason the exact requirements cannot currently be enforced. */
        public val reason: KeyUseAuthorizationUnsupportedReason,
    ) : KeyUseAuthorizationSupport
}

/** Reasons an exact key-creation requirement cannot currently be enforced. */
public enum class KeyUseAuthorizationUnsupportedReason {
    UnsupportedCombination,
    BiometricUnavailable,
    BiometricNotEnrolled,
    InteractionContextUnavailable,
}

/**
 * Result of restoring one persisted managed key.
 *
 * The result retains the persisted authorization policy even when native key material is missing.
 */
public sealed interface PlatformManagedKeyRestoration {
    /** Authorization policy persisted with the managed key. */
    public val authorizationPolicy: KeyUseAuthorizationPolicy

    /** Native key material was found and restored. */
    public data class Restored(
        /** Restored platform-managed key. */
        public val key: ManagedKey,
        override val authorizationPolicy: KeyUseAuthorizationPolicy,
    ) : PlatformManagedKeyRestoration

    /** Native key material is absent while its persisted policy remains known. */
    public data class Missing(
        override val authorizationPolicy: KeyUseAuthorizationPolicy,
    ) : PlatformManagedKeyRestoration
}

internal fun KeyUseAuthorizationUnsupportedReason.toAuthorizationFailure(): KeyUseAuthorizationFailure = when (this) {
    KeyUseAuthorizationUnsupportedReason.UnsupportedCombination -> KeyUseAuthorizationFailure.UnsupportedCombination
    KeyUseAuthorizationUnsupportedReason.BiometricUnavailable -> KeyUseAuthorizationFailure.BiometricUnavailable
    KeyUseAuthorizationUnsupportedReason.BiometricNotEnrolled -> KeyUseAuthorizationFailure.BiometricNotEnrolled
    KeyUseAuthorizationUnsupportedReason.InteractionContextUnavailable -> KeyUseAuthorizationFailure.InteractionContextUnavailable
}
