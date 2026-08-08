package id.walt.wallet2.mobile

/**
 * Protocol identifiers understood by platform Digital Credentials APIs.
 *
 * [OPENID4VP_SIGNED] and [OPENID4VP_MULTISIGNED] are declared so that capability metadata can
 * report them as unsupported, and so that a request naming one is recognised rather than treated as
 * an unknown value. Neither is accepted for presentation.
 */
public object MobileWalletDigitalCredentialProtocols {
    /** Unsigned OpenID4VP Digital Credentials protocol identifier. */
    public const val OPENID4VP_UNSIGNED: String = "openid4vp-v1-unsigned"
    /** Signed OpenID4VP Digital Credentials protocol identifier. Not supported. */
    public const val OPENID4VP_SIGNED: String = "openid4vp-v1-signed"
    /** Multi-signed OpenID4VP Digital Credentials protocol identifier. Not supported. */
    public const val OPENID4VP_MULTISIGNED: String = "openid4vp-v1-multisigned"
    /** ISO 18013-7 Annex C mobile-document protocol identifier. */
    public const val ISO_MDOC_ANNEX_C: String = "org-iso-mdoc"
}

/**
 * Credential formats exposed through the platform Digital Credentials integration.
 *
 * [identifier] is the credential format identifier this entry represents. Capability metadata is
 * reported to host applications through it rather than through the enum-entry name, so renaming an
 * entry cannot silently change what the wallet advertises.
 *
 * @property identifier Stable credential format identifier.
 */
public enum class MobileWalletDigitalCredentialFormat(public val identifier: String) {
    MDOC("mso_mdoc"),
    SD_JWT_VC("dc+sd-jwt"),
}

/**
 * Authentication applied to a Digital Credentials request.
 *
 * @property identifier Stable identifier reported in capability metadata.
 */
public enum class MobileWalletDigitalCredentialRequestProtection(public val identifier: String) {
    UNSIGNED("unsigned"),
    SIGNED("signed"),
    MULTISIGNED("multisigned"),
    READER_AUTHENTICATED("reader_authenticated"),
}

/**
 * Protection applied to the response returned to the requesting platform.
 *
 * @property identifier Stable identifier reported in capability metadata.
 */
public enum class MobileWalletDigitalCredentialResponseProtection(public val identifier: String) {
    UNENCRYPTED("unencrypted"),
    JWE("jwe"),
    HPKE("hpke"),
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
 * Matcher-visible credential metadata supplied to a platform credential registry.
 *
 * This is not the credential: no issuer-signed payload, proof, or key material is included. The
 * claim values it does carry are the user's personal data, because the platform matcher runs out of
 * process and cannot ask the wallet for a value it was not given.
 *
 * @property registryEntryId Stable platform registry entry identifier.
 * @property credentialId Wallet-local credential identifier.
 * @property format Credential format represented by the entry.
 * @property type Credential type or mdoc document type.
 * @property fields Matcher-visible credential fields, which may contain personal data.
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
     * so a credential the wallet no longer holds stops being offered. Implementations are not
     * required to be atomic - a platform may take several operations to publish one desired state,
     * and an interrupted call can leave the registry holding neither the old nor the new set. What
     * they must do is report the outcome instead of throwing, since the wallet state this projects
     * has already been committed.
     */
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
 * Verifier and transaction metadata extracted from an OpenID4VP request received through a
 * Digital Credentials API.
 *
 * Unlike [MobileWalletPresentationRequestInfo], [clientId] is nullable: the OpenID4VP DC API
 * requires unsigned requests to omit and ignore `client_id`. The authenticated platform origin
 * is exposed separately by [MobileWalletDigitalCredentialPreview.verifiedOrigin] and is the
 * authoritative requester identity for those requests.
 *
 * @property clientId Authenticated signed-request client identifier, or null for unsigned requests.
 * @property verifierMetadata Typed verifier metadata supplied by the request, when available.
 * @property nonce Required OpenID4VP nonce value supplied by the verifier.
 * @property responseMode Serialized OpenID4VP DC API response mode, when provided.
 * @property transactionData Decoded transaction data items requested by the verifier.
 */
public data class MobileWalletDigitalCredentialRequestInfo(
    public val clientId: String?,
    public val verifierMetadata: MobileWalletVerifierMetadata?,
    public val nonce: String,
    public val responseMode: String?,
    public val transactionData: List<MobileWalletTransactionDataItem> = emptyList(),
) {
    init {
        require(clientId == null || clientId.isNotBlank()) {
            "A Digital Credentials request client ID must not be blank."
        }
        require(nonce.isNotBlank()) { "A Digital Credentials request nonce must not be blank." }
    }
}

/**
 * Consent preview retained by the SDK until [MobileWallet.submitDigitalCredentialPresentation].
 *
 * @property requestId Opaque identifier binding the later submission to this preview.
 * @property protocol Platform protocol identifier.
 * @property verifiedOrigin Authenticated requesting origin.
 * @property request Parsed presentation request metadata.
 * @property credentialOptions Matching wallet credentials.
 * @property credentialRequirements Required credential-query combinations.
 * @property readerTrust Reader authentication and application-trust state.
 */
public data class MobileWalletDigitalCredentialPreview(
    public val requestId: String,
    public val protocol: String,
    public val verifiedOrigin: String,
    public val request: MobileWalletDigitalCredentialRequestInfo,
    public val credentialOptions: List<MobileWalletPresentationCredentialOption>,
    public val credentialRequirements: List<MobileWalletPresentationCredentialRequirement>,
    public val readerTrust: MobileWalletReaderTrust,
)

/**
 * Reader authentication state. Only [MobileWalletReaderTrust.Trusted] means a reader was identified,
 * and reaching it requires both a valid signature and an accepting application trust policy.
 *
 * A request whose reader authentication fails cryptographic verification never produces a state at
 * all: it is rejected, and no preview is returned. So every state here describes a request that is
 * still processable, and the four non-trusted states say something different about why the reader is
 * not identified. Distinguishing them matters because they call for different consent copy: an
 * absent signature is a reader that declined to identify itself, while a valid signature no policy
 * accepts is a reader the wallet simply cannot vouch for.
 */
public sealed interface MobileWalletReaderTrust {
    /** The protocol carries no reader authentication, as with the OpenID4VP Digital Credentials API. */
    public data object NotApplicable : MobileWalletReaderTrust

    /** The request supports reader authentication but carried none, so the reader is anonymous. */
    public data object NotAuthenticated : MobileWalletReaderTrust

    /**
     * Reader authentication has not been checked yet because the platform withholds the raw request
     * until the user consents.
     *
     * Apple's IdentityDocumentServices exposes only a parsed request before consent. The signature
     * is verified, and a bad one rejects the request, before any credential data is released - but
     * that happens at submission, so a consent dialog cannot yet name the reader.
     */
    public data object PendingRawRequest : MobileWalletReaderTrust

    /**
     * The reader's signature is cryptographically valid, but no application trust policy accepts it.
     *
     * This is not a verification failure: the chain verified and the signature checked out. It means
     * the wallet has no basis for telling the user who the reader is - which is also the state the
     * default [UnconfiguredMobileWalletReaderTrustEvaluator] always reports.
     *
     * @property reason Reason the trust policy did not accept the reader.
     */
    public data class Untrusted(public val reason: String) : MobileWalletReaderTrustDecision

    /**
     * Reader authentication is cryptographically valid and an application trust policy accepted it.
     *
     * @property certificateSubject Subject from the trusted reader certificate.
     */
    public data class Trusted(public val certificateSubject: String) : MobileWalletReaderTrustDecision
}

/**
 * The two outcomes an application trust policy may return.
 *
 * Narrower than [MobileWalletReaderTrust] on purpose: a policy is only ever consulted for a reader
 * whose signature already verified, so it cannot report that authentication was absent or deferred.
 */
public sealed interface MobileWalletReaderTrustDecision : MobileWalletReaderTrust

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

/**
 * Application trust policy for a cryptographically verified Annex C reader certificate chain.
 *
 * The wallet has already verified the chain's internal signatures and the reader's COSE signature
 * over the session-bound payload before calling this; a request that failed either is rejected and
 * never reaches a policy. What remains is the question this answers: does the application recognise
 * this reader as one it is willing to name to the user? The wallet has no basis for deciding that,
 * which is why an unconfigured wallet answers [MobileWalletReaderTrust.Untrusted] rather than
 * treating a valid signature as identification.
 */
public fun interface MobileWalletReaderTrustEvaluator {
    /** Evaluates the cryptographically verified reader certificate chain against the trust policy. */
    public suspend fun evaluate(readerCertificateChainDer: List<ByteArray>): MobileWalletReaderTrustDecision
}

/** Secure default: a valid signature alone does not establish that a reader is trusted. */
public object UnconfiguredMobileWalletReaderTrustEvaluator : MobileWalletReaderTrustEvaluator {
    /** Reports the verified reader as untrusted because no application trust policy was configured. */
    override suspend fun evaluate(readerCertificateChainDer: List<ByteArray>): MobileWalletReaderTrustDecision =
        MobileWalletReaderTrust.Untrusted(
            "Reader authentication is cryptographically valid, but no reader trust policy is configured"
        )
}
