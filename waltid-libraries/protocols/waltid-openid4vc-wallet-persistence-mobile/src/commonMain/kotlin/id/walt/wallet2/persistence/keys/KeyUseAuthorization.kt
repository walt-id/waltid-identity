package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import kotlinx.serialization.Serializable

/** Immutable authorization policy selected when a wallet key is created. */
@Serializable
public sealed interface KeyUseAuthorizationPolicy {
    /** Private-key operations retain their ordinary non-interactive behavior. */
    @Serializable
    public data object None : KeyUseAuthorizationPolicy

    /** Every private-key operation requires a currently enrolled strong biometric. */
    @Serializable
    public data object BiometricCurrentSet : KeyUseAuthorizationPolicy

    /**
     * Strong biometric authorization may be reused for private-key operations during the fixed
     * interval. The interval starts with successful authentication and never slides on signing.
     *
     * New biometric enrollment does not invalidate this key. Android can independently read back
     * this interval from native KeyStore metadata after creation or restoration. iOS configures it
     * in Signum, but Signum's pinned public API does not expose an effective positive timeout after
     * restoration for independent readback.
     *
     * Timed reuse is recent platform or provider authentication. It is not authorization or
     * consent for issuance, presentation, or another wallet action, and is not guaranteed to be
     * key-local.
     */
    @Serializable
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
            val timed = effectivePolicy is KeyUseAuthorizationPolicy.BiometricTimedReuse
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
}
