package id.walt.wallet2.mobile.digitalcredentials

/**
 * Credential metadata shared with the operating system's out-of-process matcher.
 * [fields] can contain the holder's personal data. The signed credential and wallet keys stay in the wallet.
 *
 * @property registryEntryId Stable platform registry entry identifier.
 * @property credentialId Wallet-local credential identifier.
 * @property format Credential format represented by the entry.
 * @property type Credential type or mdoc document type.
 * @property fields Decoded claim values available to the platform matcher.
 * @property display Title and artwork shown in the platform credential picker.
 */
public data class RegistryEntry(
    public val registryEntryId: String,
    public val credentialId: String,
    public val format: DigitalCredentialFormat,
    public val type: String,
    public val fields: List<RegistryField>,
    public val display: RegistryEntryDisplay,
)

/**
 * One matcher-visible field. Values are individual decoded claims, never the raw credential payload.
 *
 * @property path Format-specific claim path.
 * @property valueJson Claim value encoded as JSON.
 * @property selectivelyDisclosable Whether this claim is selectively disclosable.
 */
public data class RegistryField(
    public val path: List<String>,
    public val valueJson: String,
    public val selectivelyDisclosable: Boolean,
)

/** Outcome of replacing operating-system entries or saving a projection for the host to publish. */
public sealed interface CredentialRegistrationResult {
    /** Whether the platform can currently accept registrations. */
    public val available: Boolean
    /** Entries applied or saved by this attempt. An unavailable attempt acknowledges zero entries. */
    public val entryCount: Int
    /** Diagnostic when registration could not complete or the platform is unavailable. */
    public val reason: String?

    /** The platform accepted the replacement. Zero means its registry was explicitly cleared. */
    public data class Applied(override val entryCount: Int) : CredentialRegistrationResult {
        override val available: Boolean get() = true
        override val reason: String? get() = null
        init { require(entryCount >= 0) }
    }

    /** Desired entries were saved. The host still has to publish them to the operating system. */
    public data class ProjectionSaved(
        override val entryCount: Int,
        override val reason: String? = null,
    ) : CredentialRegistrationResult {
        override val available: Boolean get() = reason == null
        init { require(entryCount >= 0) }
    }

    /** The adapter could not replace or save entries. A previous set may still be in effect. */
    public data class Unavailable(override val reason: String) : CredentialRegistrationResult {
        override val available: Boolean get() = false
        override val entryCount: Int get() = 0
    }
}

/** Platform adapter for metadata-only registration. Android framework types never cross this boundary. */
public interface CredentialRegistry {
    /** Current platform capability and registration state. */
    public val capabilities: DigitalCredentialCapabilities

    /**
     * Synchronizes the platform registry named [registryId] to exactly [records].
     *
     * Reusing [registryId] must replace that registry's previous entries rather than add to them,
     * so a credential the wallet no longer holds stops being offered. Implementations need not be
     * atomic, but must report failure instead of throwing: the wallet state this projects has
     * already been committed.
     */
    public suspend fun replace(
        registryId: String,
        records: List<RegistryEntry>,
    ): CredentialRegistrationResult

    /**
     * Registers OpenID4VCI creation options (what the wallet can receive via CREATE_CREDENTIAL).
     *
     * Distinct from [replace], which projects currently stored credentials for presentation.
     * Default is a no-op for platforms without an issuance-provider integration.
     */
    public suspend fun registerCreationOptions(): CredentialRegistrationResult =
        CredentialRegistrationResult.Unavailable("This adapter does not advertise issuance options")
}

/** Registry used when the current platform or application has no registration integration. */
public object UnavailableCredentialRegistry : CredentialRegistry {
    /** Capability snapshot reporting that no registry integration is available. */
    override val capabilities: DigitalCredentialCapabilities =
        DigitalCredentialCapabilities(
            platform = "unknown",
            platformAvailable = false,
            minimumOsVersion = "not available",
            registrationAvailable = false,
            capabilities = emptyList(),
        )

    override suspend fun replace(
        registryId: String,
        records: List<RegistryEntry>,
    ): CredentialRegistrationResult = CredentialRegistrationResult.Unavailable("Digital credential registration is unavailable")
}
