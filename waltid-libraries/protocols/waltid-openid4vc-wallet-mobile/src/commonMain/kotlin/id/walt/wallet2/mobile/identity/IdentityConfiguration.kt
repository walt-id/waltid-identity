package id.walt.wallet2.mobile.identity

import id.walt.crypto2.signum.SignumPlatformPolicy
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy
import kotlinx.serialization.Serializable

/** Identity configuration. Native recovery integrations are opt-in dependencies and registrations. */
public data class IdentityConfiguration(
    /** Registered providers are trusted with recovery secrets. Empty means backup is disabled. */
    public val recoveryProviders: List<IdentityRecoveryProvider> = emptyList(),
    /** Authorization inherited from the wallet configuration unless explicitly overridden. */
    public val authorization: IdentityAuthorization = IdentityAuthorization.WalletDefault,
    /** Host/issuer constraints, not a declaration of EUDI or HAIP certification. */
    public val policy: IdentityKeyPolicy = IdentityKeyPolicy.GeneralPurpose,
    /** Advanced settings for this device's native key backend. */
    public val platform: SignumPlatformPolicy = SignumPlatformPolicy.Default,
    /** Explicit alternatives the host permits. Choosing weaker authorization always requires a different option. */
    public val alternativeAuthorizations: List<KeyUseAuthorizationPolicy> = emptyList(),
    /** Minimum provider evidence required for activation and disposal of local recovery material. */
    public val recoveryConfirmation: RecoveryConfirmation = RecoveryConfirmation.LocalAcceptance,
    /** Whether the additional local recovery record is retained after the required confirmation. */
    public val localRecoveryMaterial: LocalRecoveryMaterialRetention = LocalRecoveryMaterialRetention.Retain,
) {
    init {
        require(recoveryProviders.all { it.id.isNotBlank() && it.displayName.isNotBlank() })
        require(recoveryProviders.map { it.id }.distinct().size == recoveryProviders.size) {
            "Recovery provider identifiers must be unique"
        }
    }
}

/** Makes inheritance explicit without a nullable authorization policy. */
public sealed interface IdentityAuthorization {
    /** Uses the wallet's configured key-use authorization policy. */
    public data object WalletDefault : IdentityAuthorization
    /** Uses an explicit policy for newly created identities only.
     * @property policy Required private-key use authorization. */
    public data class Explicit(public val policy: KeyUseAuthorizationPolicy) : IdentityAuthorization
}

/** Constraints retained with the identity and applied to creation, backup and restoration. */
@Serializable
public enum class IdentityKeyPolicy {
    /** Permits recoverable signing identities; credential eligibility is evaluated separately. */
    GeneralPurpose,
    /** Prohibits backup/export of the signing secret. */
    DeviceBound,
    /** Requires generation in observed hardware and prohibits signing-secret backup/export. */
    HardwareGenerated,
}

/** Requested lifecycle. Only compatible complete options can be passed to creation. */
public enum class IdentityIntent { WithoutRecovery, Recoverable }

/** Execution backend selected by a validated option. NativeStorage does not promise hardware. */
@Serializable
public enum class IdentityKeyStorage { Hardware, NativeStorage, EncryptedDatabase }

/** Does not erase an operational software signing key; it controls the additional recovery record only. */
public enum class LocalRecoveryMaterialRetention { Retain, DiscardAfterSubmission }

/** Attestation is a creation-time request, so challenges are never baked into wallet defaults. */
public sealed interface IdentityAttestationRequest {
    /** No native evidence requested. */
    public data object None : IdentityAttestationRequest
    /** Requests platform generation evidence. The relying party must verify freshness, chain and claims.
     * @property challenge Relying-party challenge, containing 1 through 128 bytes. */
    public data class Native(public val challenge: id.walt.crypto2.serialization.BinaryData) : IdentityAttestationRequest {
        init { require(challenge.size in 1..128) { "Native attestation challenge must contain 1 to 128 bytes" } }
    }
}

/** Minimum evidence required before backup-dependent activation or local-record disposal. */
@Serializable
public enum class RecoveryConfirmation {
    /** Accepts an exact local readback; remote delivery may still be unknown. */
    LocalAcceptance,
    /** Requires the provider to confirm delivery within its documented scope. */
    ProviderConfirmation,
}
