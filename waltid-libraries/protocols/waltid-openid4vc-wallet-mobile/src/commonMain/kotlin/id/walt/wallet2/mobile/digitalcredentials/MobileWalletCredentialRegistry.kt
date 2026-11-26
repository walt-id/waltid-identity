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
public data class MobileWalletCredentialRegistryRecord(
    public val registryEntryId: String,
    public val credentialId: String,
    public val format: MobileWalletDigitalCredentialFormat,
    public val type: String,
    public val fields: List<MobileWalletCredentialRegistryField>,
    public val display: RegistryEntryDisplay,
)

/**
 * One matcher-visible field. Values are individual decoded claims, never the raw credential payload.
 *
 * @property path Format-specific claim path.
 * @property valueJson Claim value encoded as JSON.
 * @property selectivelyDisclosable Whether this claim is selectively disclosable.
 */
public data class MobileWalletCredentialRegistryField(
    public val path: List<String>,
    public val valueJson: String,
    public val selectivelyDisclosable: Boolean,
)

/**
 * Outcome of synchronizing the platform registry with the current wallet credential metadata.
 *
 * A result with [available] false means the platform projection is now out of date, not that the
 * wallet operation which triggered the synchronization failed. Callers that care about the
 * projection should surface the [reason] and retry [MobileWalletCredentialRegistry.replace].
 *
 * @property available Whether registration is available after the attempt.
 * @property registeredEntryCount Number of registered entries.
 * @property reason Optional diagnostic when registration is unavailable.
 */
public data class MobileWalletCredentialRegistrationResult(
    public val available: Boolean,
    public val registeredEntryCount: Int,
    public val reason: String? = null,
)

/** Platform adapter for metadata-only registration. Android framework types never cross this boundary. */
public interface MobileWalletCredentialRegistry {
    /** Current platform capability and registration state. */
    public val capabilities: MobileWalletDigitalCredentialCapabilities

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
        records: List<MobileWalletCredentialRegistryRecord>,
    ): MobileWalletCredentialRegistrationResult

    /**
     * Registers OpenID4VCI creation options (what the wallet can receive via CREATE_CREDENTIAL).
     *
     * Distinct from [replace], which projects currently stored credentials for presentation.
     * Default is a no-op for platforms without an issuance-provider integration.
     */
    public suspend fun registerCreationOptions(): MobileWalletCredentialRegistrationResult =
        MobileWalletCredentialRegistrationResult(
            available = capabilities.registrationAvailable,
            registeredEntryCount = 0,
        )
}

/** Registry used when the current platform or application has no registration integration. */
public object UnavailableMobileWalletCredentialRegistry : MobileWalletCredentialRegistry {
    /** Capability snapshot reporting that no registry integration is available. */
    override val capabilities: MobileWalletDigitalCredentialCapabilities =
        MobileWalletDigitalCredentialCapabilities(
            platform = "unknown",
            platformAvailable = false,
            minimumOsVersion = "not available",
            registrationAvailable = false,
            capabilities = emptyList(),
        )

    override suspend fun replace(
        registryId: String,
        records: List<MobileWalletCredentialRegistryRecord>,
    ): MobileWalletCredentialRegistrationResult = MobileWalletCredentialRegistrationResult(
        available = false,
        registeredEntryCount = 0,
        reason = "Digital credential registration is unavailable",
    )
}
