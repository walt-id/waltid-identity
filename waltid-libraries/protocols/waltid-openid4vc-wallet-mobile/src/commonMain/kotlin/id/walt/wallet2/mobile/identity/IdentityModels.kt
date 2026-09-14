package id.walt.wallet2.mobile.identity

import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.keys.PlatformKeyFacts

/** Public identity details. Never contains a seed, private key or database encryption key.
 * @property id Stable identity identifier, preserved across recovery.
 * @property keyId Logical signing-key identifier, independent of the installation's native alias.
 * @property did Exact DID bound to this signing key.
 * @property publicJwk Public P-256 JWK, without private material.
 * @property storage Configured signing backend; hardware claims are in [keyFacts].
 * @property authorization Private-key use policy, separate from app authentication.
 * @property keyFacts Observed native origin, protection and evidence.
 * @property recovery Latest known backup or restoration state. */
@kotlinx.serialization.Serializable
public data class WalletIdentity(
    public val id: String,
    public val keyId: String,
    public val did: String,
    public val publicJwk: String,
    public val storage: IdentityKeyStorage,
    public val authorization: KeyUseAuthorizationPolicy,
    public val keyFacts: PlatformKeyFacts,
    public val recovery: IdentityRecoveryState = IdentityRecoveryState.Disabled,
)

/** Backup status says what is known, separately from native signing-key protection. */
@kotlinx.serialization.Serializable
public sealed interface IdentityRecoveryState {
    /** No recovery secret has been submitted. */
    @kotlinx.serialization.Serializable
    public data object Disabled : IdentityRecoveryState
    /** Deletion was submitted; OS local acceptance does not prove removal from the cloud or other devices.
     * @property reference Record for which deletion was requested.
     * @property receipt Evidence returned by the provider. */
    @kotlinx.serialization.Serializable
    public data class RemovalRequested(public val reference: IdentityBackupReference, public val receipt: RecoveryReceipt) : IdentityRecoveryState
    /** This installation retrieved and successfully restored the original signing key.
     * @property reference Record used for restoration. */
    @kotlinx.serialization.Serializable
    public data class Recovered(public val reference: IdentityBackupReference) : IdentityRecoveryState
    /** A provider accepted the record; the receipt states the actual delivery evidence.
     * @property reference Submitted record.
     * @property receipt Evidence returned by the provider. */
    @kotlinx.serialization.Serializable
    public data class Submitted(
        public val reference: IdentityBackupReference,
        public val receipt: RecoveryReceipt,
    ) : IdentityRecoveryState
}

/** Complete SDK-issued creation option. It cannot be constructed, copied or deserialized by callers.
 * @property storage Selected signing backend.
 * @property authorization Required private-key authorization.
 * @property recoveryProviderName Selected backup provider's display name, or null for no backup.
 * @property recoveryAvailability Provider protection and route, or null when recovery is disabled.
 * @property attestation Native evidence requested at creation. */
public class IdentityCreationOption internal constructor(
    internal val owner: Any,
    public val storage: IdentityKeyStorage,
    public val authorization: KeyUseAuthorizationPolicy,
    public val recoveryProviderName: String?,
    internal val providerId: String?,
    public val recoveryAvailability: RecoveryAvailability.Available?,
    public val attestation: IdentityAttestationRequest,
) {
    /** Whether this option retains a secret from which the same key can be recovered. */
    public val recoverable: Boolean get() = providerId != null
}

/** Complete creation choices, or explicit reasons why the current requirements cannot be met. */
public sealed interface IdentityOptions {
    /** Supported choices, ordered by configured preference.
     * @property recommended Preferred complete choice.
     * @property alternatives Other supported choices requiring explicit selection.
     */
    public class Available internal constructor(
        public val recommended: IdentityCreationOption,
        public val alternatives: List<IdentityCreationOption>,
    ) : IdentityOptions
    /** No complete choice meets current requirements.
     * @property reasons Human-readable unmet requirements.
     */
    public data class Unavailable(public val reasons: List<String>) : IdentityOptions
}

/** SDK-issued backup choice for an existing exportable identity.
 * @property identityId Identity whose signing secret will be backed up.
 * @property providerName Selected provider's display name.
 * @property recoveryAvailability Provider protection and route, rechecked before submission. */
public class IdentityBackupOption internal constructor(
    internal val owner: Any,
    public val identityId: String,
    public val providerName: String,
    internal val providerId: String,
    public val recoveryAvailability: RecoveryAvailability.Available,
)

/** A record discovered through a configured provider; executable recovery options require validation.
 * @property reference Provider and record identifiers, without a signing secret.
 * @property providerName Configured provider's display name. */
public class RecoveryCandidate internal constructor(
    internal val owner: Any,
    public val reference: IdentityBackupReference,
    public val providerName: String,
)

/** SDK-issued restoration option tied to a validated record and current destination policy.
 * @property did Exact original DID to restore.
 * @property storage Destination signing backend.
 * @property authorization Required private-key authorization on this installation. */
public class IdentityRestorationOption internal constructor(
    internal val owner: Any,
    public val did: String,
    public val storage: IdentityKeyStorage,
    public val authorization: KeyUseAuthorizationPolicy,
    internal val reference: IdentityBackupReference,
    internal val fingerprint: ByteArray,
)

/** Identity availability is distinct from whether credential data exists in the wallet. */
public sealed interface WalletIdentityState {
    /** No identity or conflicting unassociated key/DID exists. */
    public data object Absent : WalletIdentityState
    /** The identity is active and available for signing.
     * @property identity Established signing identity.
     */
    public data class Active(public val identity: WalletIdentity) : WalletIdentityState
    /** An interrupted setup must be resumed or cancelled before activation.
     * @property identityId Identifier of the journaled operation's identity.
     */
    public data class Pending(public val identityId: String) : WalletIdentityState
    /** Existing state requires attention; a replacement is never generated automatically.
     * @property identityId Known identity identifier, or null for unassociated state.
     * @property reason Stable failure category.
     */
    public data class Unavailable(public val identityId: String?, public val reason: IdentityFailure) : WalletIdentityState
}

/** Stable failure categories; callers do not need native error-message parsing. */
public enum class IdentityFailure {
    UnsupportedPolicy, StaleOption, KeyUnavailable, InvalidRecoveryRecord,
    AuthorizationNotCompleted, NativeOperationFailed, RecoveryUnavailable, ExistingIdentity,
}

/** Creation and restore share one lifecycle result. Pending operations are resumable. */
public sealed interface IdentityOperationResult {
    /** The identity is active and available for signing.
     * @property identity Established signing identity.
     */
    public data class Active(public val identity: WalletIdentity) : IdentityOperationResult
    /** An interrupted setup must be resumed or cancelled before activation.
     * @property identityId Identifier of the journaled operation's identity.
     */
    public data class Pending(public val identityId: String) : IdentityOperationResult
    /** The operation could not complete.
     * @property reason Stable failure category.
     */
    public data class Failed(public val reason: IdentityFailure) : IdentityOperationResult
}
