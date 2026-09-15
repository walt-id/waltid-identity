package id.walt.wallet2.mobile.identity

/**
 * Trusted recovery integration. Implementations must protect secret records in transit and at rest,
 * scope access to the intended user/application, and make repeated writes of the same ID idempotent.
 * A local write must not be reported as verified cloud delivery.
 */
public interface IdentityRecoveryProvider {
    /** Stable identifier persisted with backups; never reuse it for an incompatible integration. */
    public val id: String
    /** Provider name suitable for identity settings. */
    public val displayName: String
    /** Current prerequisites and protection of this integration. */
    public suspend fun availability(): RecoveryAvailability
    /** Lists only records belonging to the provider's configured namespace and user. */
    public suspend fun list(): List<String>
    /** Stores a secret record under [recordId]. Existing different content must not be silently overwritten. */
    public suspend fun store(recordId: String, record: IdentityRecoveryData): RecoveryReceipt
    /** Retrieves a protected record, or returns null if it is absent. */
    public suspend fun retrieve(recordId: String): IdentityRecoveryData?
    /** Requests deletion; returns the strongest completion evidence actually available. */
    public suspend fun delete(recordId: String): RecoveryReceipt
}

/** Secret bytes crossing the trusted provider boundary. Never log or include them in UI/analytics. */
public class IdentityRecoveryData(bytes: ByteArray) {
    private val bytes: ByteArray = bytes.copyOf()
    init { require(bytes.size in 1..MAX_BYTES) { "Recovery record exceeds the supported size" } }
    /** Returns a defensive copy for a trusted storage/encryption adapter. */
    public fun copyBytes(): ByteArray = bytes.copyOf()
    /** Redacted representation safe for diagnostic output. */
    override fun toString(): String = "IdentityRecoveryData(redacted)"
    /** Recovery record bounds. */
    public companion object {
        /** Upper bound for a single identity record, compatible with Block Store's per-entry limit. */
        public const val MAX_BYTES: Int = 4096
    }
}

/** Current provider prerequisites. Availability is rechecked when executing an option. */
@kotlinx.serialization.Serializable
public sealed interface RecoveryAvailability {
    /** The integration can accept a record with the stated protection and delivery scope.
     * @property protection Protection supplied by the configured provider.
     * @property scope Supported delivery scope; availability does not prove delivery. */
    @kotlinx.serialization.Serializable
    public data class Available(
        public val protection: RecoveryProtection,
        public val scope: RecoveryScope,
    ) : RecoveryAvailability
    /** The integration cannot currently meet its configured requirements.
     * @property reason Human-readable unmet prerequisite. */
    @kotlinx.serialization.Serializable
    public data class Unavailable(public val reason: String) : RecoveryAvailability
}

/** Protection of the recovery record, independent of signing-key hardware protection. */
@kotlinx.serialization.Serializable
public enum class RecoveryProtection {
    /** Uses OS-protected device transfer; it does not claim cloud end-to-end encryption. */
    OperatingSystemProtected,
    /** Relies on the OS service's documented end-to-end protection. */
    OperatingSystemEndToEnd,
    /** Provider encrypts/authenticates a portable envelope with a separately recoverable key. */
    ApplicationEncrypted,
}

/** Delivery scope supported by a registered integration. */
@kotlinx.serialization.Serializable
public enum class RecoveryScope { Cloud, DeviceTransfer, Custom }

/** Evidence returned by a backup operation; OS submission is not proof of remote durability. */
@kotlinx.serialization.Serializable
public enum class RecoveryReceipt { AcceptedLocally, ConfirmedByProvider }

/** A safe identifier for a backup; it carries no signing secret or authorization.
 * @property providerId Stable configured provider identifier.
 * @property recordId Record identifier within the provider namespace. */
@kotlinx.serialization.Serializable
public data class IdentityBackupReference(public val providerId: String, public val recordId: String)

/** Actionable provider failures. Unknown integration errors remain retryable without exposing their messages. */
public enum class IdentityProviderFailure {
    TemporarilyUnavailable, InteractionRequired, Rejected, Conflict, ConfirmationPending,
}

/** A trusted integration's structured failure; never includes record bytes or provider credentials.
 * @property failure Action required before retrying the operation. */
public class IdentityProviderException(public val failure: IdentityProviderFailure) : Exception("Identity provider: $failure")
