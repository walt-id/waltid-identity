package id.walt.wallet2.mobile

/** Protocol identifiers understood by platform Digital Credentials APIs. */
public object MobileWalletDigitalCredentialProtocols {
    public const val OPENID4VP_UNSIGNED: String = "openid4vp-v1-unsigned"
    public const val OPENID4VP_SIGNED: String = "openid4vp-v1-signed"
    public const val OPENID4VP_MULTISIGNED: String = "openid4vp-v1-multisigned"
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

/** Runtime support for one protocol/format/protection combination. */
public data class MobileWalletDigitalCredentialCapability(
    public val protocol: String,
    public val credentialFormats: List<MobileWalletDigitalCredentialFormat>,
    public val requestProtection: List<MobileWalletDigitalCredentialRequestProtection>,
    public val responseProtection: List<MobileWalletDigitalCredentialResponseProtection>,
    public val supported: Boolean,
    public val unsupportedReason: String? = null,
)

/** Truthful platform capability snapshot, including runtime and registration availability. */
public data class MobileWalletDigitalCredentialCapabilities(
    public val platform: String,
    public val platformAvailable: Boolean,
    public val minimumOsVersion: String,
    public val registrationAvailable: Boolean,
    public val capabilities: List<MobileWalletDigitalCredentialCapability>,
)

/** Minimal, non-secret metadata supplied to a platform credential registry. */
public data class MobileWalletCredentialRegistryRecord(
    public val registryEntryId: String,
    public val credentialId: String,
    public val format: MobileWalletDigitalCredentialFormat,
    public val type: String,
    public val fields: List<MobileWalletCredentialRegistryField>,
    public val displayName: String,
)

/** One matcher-visible field. Values are individual decoded claims, never the raw credential payload. */
public data class MobileWalletCredentialRegistryField(
    public val path: List<String>,
    public val valueJson: String,
    public val selectivelyDisclosable: Boolean,
)

/** Result of replacing the platform registry with the current wallet credential metadata. */
public data class MobileWalletCredentialRegistrationResult(
    public val available: Boolean,
    public val registeredEntryCount: Int,
    public val reason: String? = null,
)

/** Platform adapter for metadata-only registration. Android framework types never cross this boundary. */
public interface MobileWalletCredentialRegistry {
    public val capabilities: MobileWalletDigitalCredentialCapabilities

    /** Replaces the registry atomically. Reusing [registryId] must overwrite stale entries. */
    public suspend fun replace(
        registryId: String,
        records: List<MobileWalletCredentialRegistryRecord>,
    ): MobileWalletCredentialRegistrationResult
}

/** Registry used when the current platform or application has no registration integration. */
public object UnavailableMobileWalletCredentialRegistry : MobileWalletCredentialRegistry {
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

/** Platform-neutral request passed by Android Credential Manager or an Apple provider extension. */
public data class MobileWalletDigitalCredentialRequest(
    public val protocol: String,
    public val dataJson: String,
    public val verifiedOrigin: String,
    public val selectedRegistryEntryIds: List<String> = emptyList(),
)

/** Consent preview retained by the SDK until [MobileWallet.submitDigitalCredentialPresentation]. */
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
    public data object NotApplicable : MobileWalletReaderTrust
    public data class Unverified(public val reason: String) : MobileWalletReaderTrust
    public data class Trusted(public val certificateSubject: String) : MobileWalletReaderTrust
}

/** OS-mediated response. [dataJson] is returned to the platform and is never direct-posted over HTTP. */
public data class MobileWalletDigitalCredentialResponse(
    public val protocol: String,
    public val dataJson: String,
)

/** Explicit user cancellation; adapters must map this to the platform cancellation contract. */
public class MobileWalletDigitalCredentialCancellationException : Exception("Digital credential presentation cancelled")

/** Selected platform registry entry no longer maps to a current wallet credential. */
public class MobileWalletStaleRegistryEntryException(public val registryEntryId: String) :
    IllegalArgumentException("Selected credential registry entry is stale")

/** Parsed request shape Apple exposes before the user grants access to the raw Annex C request. */
public data class MobileWalletAnnexCParsedRequest(
    public val documents: List<MobileWalletAnnexCDocumentRequest>,
)

/** Requested mdoc document and namespace elements. */
public data class MobileWalletAnnexCDocumentRequest(
    public val docType: String,
    public val namespaces: Map<String, List<String>>,
)

/** Two-phase Annex C input. Android supplies raw fields immediately; Apple may defer them. */
public data class MobileWalletAnnexCRequest(
    public val parsedRequest: MobileWalletAnnexCParsedRequest,
    public val verifiedOrigin: String,
    public val selectedRegistryEntryIds: List<String> = emptyList(),
    public val deviceRequestBase64Url: String? = null,
    public val encryptionInfoBase64Url: String? = null,
)

/** Consent preview for an ISO 18013-7 Annex C presentation. */
public data class MobileWalletAnnexCPreview(
    public val requestId: String,
    public val verifiedOrigin: String,
    public val parsedRequest: MobileWalletAnnexCParsedRequest,
    public val credentialOptions: List<MobileWalletPresentationCredentialOption>,
    public val readerTrust: MobileWalletReaderTrust,
)

/** Raw post-consent Annex C data required to sign and encrypt a response. */
public data class MobileWalletAnnexCSubmission(
    public val requestId: String,
    public val verifiedOrigin: String,
    public val deviceRequestBase64Url: String,
    public val encryptionInfoBase64Url: String,
    public val selectedCredentialOptions: List<MobileWalletPresentationCredentialSelection>,
)

/** Application trust policy for a cryptographically verified Annex C reader certificate chain. */
public fun interface MobileWalletReaderTrustEvaluator {
    public suspend fun evaluate(readerCertificateChainDer: List<ByteArray>): MobileWalletReaderTrust
}

/** Secure default: a valid signature alone does not establish that a reader is trusted. */
public object UnconfiguredMobileWalletReaderTrustEvaluator : MobileWalletReaderTrustEvaluator {
    override suspend fun evaluate(readerCertificateChainDer: List<ByteArray>): MobileWalletReaderTrust =
        MobileWalletReaderTrust.Unverified("Reader signature is valid, but no reader trust policy is configured")
}
