package id.walt.wallet2.mobile

/** Protocol identifiers understood by platform Digital Credentials APIs. */
public object MobileWalletDigitalCredentialProtocols {
    /** Unsigned OpenID4VP Digital Credentials protocol identifier. */
    public const val OPENID4VP_UNSIGNED: String = "openid4vp-v1-unsigned"
    /** Signed OpenID4VP Digital Credentials protocol identifier. */
    public const val OPENID4VP_SIGNED: String = "openid4vp-v1-signed"
    /** Multi-signed OpenID4VP Digital Credentials protocol identifier. */
    public const val OPENID4VP_MULTISIGNED: String = "openid4vp-v1-multisigned"
    /** ISO 18013-7 Annex C mobile-document protocol identifier. */
    public const val ISO_MDOC_ANNEX_C: String = "org-iso-mdoc"
}

/** Credential formats exposed through the platform Digital Credentials integration. */
public enum class MobileWalletDigitalCredentialFormat {
    MDOC,
    SD_JWT_VC,
}

/** Authentication applied to a Digital Credentials request. */
public enum class MobileWalletDigitalCredentialRequestProtection {
    UNSIGNED,
    SIGNED,
    MULTISIGNED,
    READER_AUTHENTICATED,
}

/** Protection applied to the response returned to the requesting platform. */
public enum class MobileWalletDigitalCredentialResponseProtection {
    UNENCRYPTED,
    JWE,
    HPKE,
}

/**
 * Runtime support for one protocol/format/protection combination.
 *
 * @property protocol Platform Digital Credentials protocol identifier.
 * @property credentialFormats Credential formats accepted for this protocol.
 * @property requestProtection Authentication applied to the request.
 * @property responseProtection Protection applied to the response.
 * @property supported Whether this exact combination is currently available.
 * @property unsupportedReason Human-readable reason when [supported] is false.
 */
public data class MobileWalletDigitalCredentialCapability(
    public val protocol: String,
    public val credentialFormats: List<MobileWalletDigitalCredentialFormat>,
    public val requestProtection: List<MobileWalletDigitalCredentialRequestProtection>,
    public val responseProtection: List<MobileWalletDigitalCredentialResponseProtection>,
    public val supported: Boolean,
    public val unsupportedReason: String? = null,
)

/**
 * Truthful platform capability snapshot, including runtime and registration availability.
 *
 * @property platform Platform integration that produced this snapshot.
 * @property platformAvailable Whether the required platform API is available.
 * @property minimumOsVersion Lowest OS version supported by the platform integration.
 * @property registrationAvailable Whether metadata registration can currently be used.
 * @property capabilities Per-protocol capability details.
 */
public data class MobileWalletDigitalCredentialCapabilities(
    public val platform: String,
    public val platformAvailable: Boolean,
    public val minimumOsVersion: String,
    public val registrationAvailable: Boolean,
    public val capabilities: List<MobileWalletDigitalCredentialCapability>,
)

/**
 * Minimal, non-secret metadata supplied to a platform credential registry.
 *
 * @property registryEntryId Stable platform registry entry identifier.
 * @property credentialId Wallet-local credential identifier.
 * @property format Credential format represented by the entry.
 * @property type Credential type or mdoc document type.
 * @property fields Matcher-visible, non-secret credential fields.
 * @property displayName Human-readable entry label.
 */
public data class MobileWalletCredentialRegistryRecord(
    public val registryEntryId: String,
    public val credentialId: String,
    public val format: MobileWalletDigitalCredentialFormat,
    public val type: String,
    public val fields: List<MobileWalletCredentialRegistryField>,
    public val displayName: String,
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
 * Result of replacing the platform registry with the current wallet credential metadata.
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

    /** Replaces the registry atomically. Reusing [registryId] must overwrite stale entries. */
    public suspend fun replace(
        registryId: String,
        records: List<MobileWalletCredentialRegistryRecord>,
    ): MobileWalletCredentialRegistrationResult
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

/**
 * Platform-neutral request passed by Android Credential Manager or an Apple provider extension.
 *
 * @property protocol Platform protocol identifier.
 * @property dataJson Protocol request data encoded as JSON.
 * @property verifiedOrigin Authenticated origin reported by the platform.
 * @property selectedRegistryEntryIds Entries selected by the platform matcher.
 */
public data class MobileWalletDigitalCredentialRequest(
    public val protocol: String,
    public val dataJson: String,
    public val verifiedOrigin: String,
    public val selectedRegistryEntryIds: List<String> = emptyList(),
)

/**
 * Consent preview retained by the SDK until [MobileWallet.submitDigitalCredentialPresentation].
 *
 * @property requestId Opaque identifier binding the later submission to this preview.
 * @property protocol Platform protocol identifier.
 * @property verifiedOrigin Authenticated requesting origin.
 * @property request Parsed presentation request metadata.
 * @property credentialOptions Matching wallet credentials.
 * @property credentialRequirements Required credential-query combinations.
 * @property encryption Response-encryption requirements.
 * @property readerTrust Reader authentication and application-trust state.
 */
public data class MobileWalletDigitalCredentialPreview(
    public val requestId: String,
    public val protocol: String,
    public val verifiedOrigin: String,
    public val request: MobileWalletPresentationRequestInfo,
    public val credentialOptions: List<MobileWalletPresentationCredentialOption>,
    public val credentialRequirements: List<MobileWalletPresentationCredentialRequirement>,
    public val encryption: MobileWalletEncryptionInfo,
    public val readerTrust: MobileWalletReaderTrust,
)

/** Reader authentication state. Unknown or unverifiable readers are never represented as trusted. */
public sealed interface MobileWalletReaderTrust {
    /** No reader authentication applies to this request. */
    public data object NotApplicable : MobileWalletReaderTrust
    /**
     * Reader authentication was not accepted by the configured trust policy.
     *
     * @property reason Reason the reader was not trusted.
     */
    public data class Unverified(public val reason: String) : MobileWalletReaderTrust
    /**
     * Reader authentication was accepted by the configured trust policy.
     *
     * @property certificateSubject Subject from the trusted reader certificate.
     */
    public data class Trusted(public val certificateSubject: String) : MobileWalletReaderTrust
}

/**
 * OS-mediated response. [dataJson] is returned to the platform and is never direct-posted over HTTP.
 *
 * @property protocol Protocol identifier from the request.
 * @property dataJson Platform response data encoded as JSON.
 */
public data class MobileWalletDigitalCredentialResponse(
    public val protocol: String,
    public val dataJson: String,
)

/** Explicit user cancellation; adapters must map this to the platform cancellation contract. */
public class MobileWalletDigitalCredentialCancellationException : Exception("Digital credential presentation cancelled")

/**
 * Selected platform registry entry no longer maps to a current wallet credential.
 *
 * @property registryEntryId Stale platform registry entry identifier.
 */
public class MobileWalletStaleRegistryEntryException(public val registryEntryId: String) :
    IllegalArgumentException("Selected credential registry entry is stale")

/**
 * Parsed request shape Apple exposes before the user grants access to the raw Annex C request.
 *
 * @property documents Requested mdoc documents.
 */
public data class MobileWalletAnnexCParsedRequest(
    public val documents: List<MobileWalletAnnexCDocumentRequest>,
)

/**
 * Requested mdoc document and namespace elements.
 *
 * @property docType Requested mdoc document type.
 * @property namespaces Requested namespace elements keyed by namespace.
 */
public data class MobileWalletAnnexCDocumentRequest(
    public val docType: String,
    public val namespaces: Map<String, List<String>>,
)

/**
 * Two-phase Annex C input. Android supplies raw fields immediately; Apple may defer them.
 *
 * @property parsedRequest Parsed request supplied before consent.
 * @property verifiedOrigin Authenticated requesting origin.
 * @property selectedRegistryEntryIds Entries selected by the platform matcher.
 * @property deviceRequestBase64Url Raw DeviceRequest, when the platform exposes it.
 * @property encryptionInfoBase64Url Raw Annex C encryption information, when available.
 */
public data class MobileWalletAnnexCRequest(
    public val parsedRequest: MobileWalletAnnexCParsedRequest,
    public val verifiedOrigin: String,
    public val selectedRegistryEntryIds: List<String> = emptyList(),
    public val deviceRequestBase64Url: String? = null,
    public val encryptionInfoBase64Url: String? = null,
)

/**
 * Consent preview for an ISO 18013-7 Annex C presentation.
 *
 * @property requestId Opaque identifier binding submission to this preview.
 * @property verifiedOrigin Authenticated requesting origin.
 * @property parsedRequest Parsed mdoc request.
 * @property credentialOptions Matching wallet credentials.
 * @property readerTrust Reader authentication and application-trust state.
 */
public data class MobileWalletAnnexCPreview(
    public val requestId: String,
    public val verifiedOrigin: String,
    public val parsedRequest: MobileWalletAnnexCParsedRequest,
    public val credentialOptions: List<MobileWalletPresentationCredentialOption>,
    public val readerTrust: MobileWalletReaderTrust,
)

/**
 * Raw post-consent Annex C data required to sign and encrypt a response.
 *
 * @property requestId Opaque identifier returned by the preview.
 * @property verifiedOrigin Authenticated requesting origin.
 * @property deviceRequestBase64Url Raw DeviceRequest obtained after consent.
 * @property encryptionInfoBase64Url Raw Annex C encryption information obtained after consent.
 * @property selectedCredentialOptions One selected credential per requested document.
 */
public data class MobileWalletAnnexCSubmission(
    public val requestId: String,
    public val verifiedOrigin: String,
    public val deviceRequestBase64Url: String,
    public val encryptionInfoBase64Url: String,
    public val selectedCredentialOptions: List<MobileWalletPresentationCredentialSelection>,
)

/** Application trust policy for a cryptographically verified Annex C reader certificate chain. */
public fun interface MobileWalletReaderTrustEvaluator {
    /** Evaluates the validated reader certificate chain against the application's trust policy. */
    public suspend fun evaluate(readerCertificateChainDer: List<ByteArray>): MobileWalletReaderTrust
}

/** Secure default: a valid signature alone does not establish that a reader is trusted. */
public object UnconfiguredMobileWalletReaderTrustEvaluator : MobileWalletReaderTrustEvaluator {
    /** Reports the reader as unverified because no application trust policy was configured. */
    override suspend fun evaluate(readerCertificateChainDer: List<ByteArray>): MobileWalletReaderTrust =
        MobileWalletReaderTrust.Unverified("Reader signature is valid, but no reader trust policy is configured")
}
