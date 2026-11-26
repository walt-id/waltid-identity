package id.walt.wallet2.mobile.digitalcredentials

/**
 * Matcher-visible credential metadata supplied to a platform credential registry.
 *
 * This is not the credential: no issuer-signed payload, proof, or key material is included. It does
 * carry decoded claim values, which are the user's personal data - the platform matcher runs out of
 * process and cannot ask the wallet for a value it was not given.
 *
 * @property registryEntryId Stable platform registry entry identifier.
 * @property credentialId Wallet-local credential identifier.
 * @property format Credential format represented by the entry.
 * @property type Credential type or mdoc document type.
 * @property fields Matcher-visible credential fields, which may contain personal data.
 * @property displayName Human-readable entry title shown in the platform picker.
 * @property subtitle Secondary picker line. Never the raw docType or vct when a mapping or
 * humanized type is available.
 * @property iconPng Optional already-resolved card-art thumbnail for platform pickers. Android
 * Credential Manager rescales this to a 32x32 entry icon; iOS Identity Document Services ignores it.
 * Presentation preview leaves this empty so remote artwork stays off the consent path.
 * @property cardArtImageUris HTTPS card-art URIs in preference order, for Android thumbnail refresh.
 * Not registered with the platform matcher.
 * @property cardArtBackgroundColor CSS Color Level 3 background used when no image is available.
 * Not registered with the platform matcher.
 * @property cardArtFallbackPng Optional decoded portrait or other local image used when remote art
 * is missing. Not registered with the platform matcher.
 */
public data class MobileWalletCredentialRegistryRecord(
    public val registryEntryId: String,
    public val credentialId: String,
    public val format: MobileWalletDigitalCredentialFormat,
    public val type: String,
    public val fields: List<MobileWalletCredentialRegistryField>,
    public val displayName: String,
    public val subtitle: String = "",
    public val iconPng: ByteArray? = null,
    public val cardArtImageUris: List<String> = emptyList(),
    public val cardArtBackgroundColor: String? = null,
    public val cardArtFallbackPng: ByteArray? = null,
) {
    /** Compares registry identity, display fields, and thumbnail bytes. */
    public override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as MobileWalletCredentialRegistryRecord
        return registryEntryId == other.registryEntryId &&
            credentialId == other.credentialId &&
            format == other.format &&
            type == other.type &&
            fields == other.fields &&
            displayName == other.displayName &&
            subtitle == other.subtitle &&
            iconPng.contentEquals(other.iconPng) &&
            cardArtImageUris == other.cardArtImageUris &&
            cardArtBackgroundColor == other.cardArtBackgroundColor &&
            cardArtFallbackPng.contentEquals(other.cardArtFallbackPng)
    }

    /** Hash of registry identity, display fields, and thumbnail bytes. */
    public override fun hashCode(): Int {
        var result = registryEntryId.hashCode()
        result = 31 * result + credentialId.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + fields.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + subtitle.hashCode()
        result = 31 * result + (iconPng?.contentHashCode() ?: 0)
        result = 31 * result + cardArtImageUris.hashCode()
        result = 31 * result + (cardArtBackgroundColor?.hashCode() ?: 0)
        result = 31 * result + (cardArtFallbackPng?.contentHashCode() ?: 0)
        return result
    }
}

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
