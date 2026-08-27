package id.walt.wallet2.mobile

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/** Versioned mdoc interoperability boundary selected for one proximity session. */
public enum class MobileWalletProximityProfile(public val id: String) {
    /** Provisional ISO/IEC 18013-5:2021 compatibility boundary. */
    Iso1801352021("iso-18013-5:2021"),

    /** ISO/IEC 18013-5 edition-2 DIS implementation boundary. */
    Iso180135Edition2Dis2026("iso-18013-5:ed2-dis-2026"),

    /** EUDI ARF 3.0 plus the pinned August 2026 FCAF restrictions. */
    EudiArf3Fcaf202608("eudi-arf:3.0+fcaf:2026-08"),
}

/** BLE roles a holder may prepare for one in-person presentation. */
public enum class MobileWalletProximityBleRoles {
    CentralClient,
    PeripheralServer,
    Dual,
}

/** Bearer selection policy kept separate from the app-visible session state. */
public enum class MobileWalletProximityBleBearerPolicy {
    GattOnly,
    PreferL2cap,
}

/** Policy applied after reader authentication and trust facts have been evaluated. */
public enum class MobileWalletProximityReaderPolicy {
    /** Anonymous or cryptographically valid but untrusted readers may reach explicit holder consent. */
    AllowAnonymousOrUntrusted,

    /** A cryptographically valid and trusted reader is required before any disclosure preview is shown. */
    RequireTrusted,
}

/** Immutable configuration for one single-use proximity session. */
public data class MobileWalletProximityConfiguration(
    public val profile: MobileWalletProximityProfile =
        MobileWalletProximityProfile.Iso180135Edition2Dis2026,
    public val bleRoles: MobileWalletProximityBleRoles = MobileWalletProximityBleRoles.Dual,
    public val bearerPolicy: MobileWalletProximityBleBearerPolicy =
        MobileWalletProximityBleBearerPolicy.PreferL2cap,
    public val readerPolicy: MobileWalletProximityReaderPolicy =
        MobileWalletProximityReaderPolicy.AllowAnonymousOrUntrusted,
    public val readerTrustEvaluator: MobileWalletProximityReaderTrustEvaluator =
        UnconfiguredMobileWalletProximityReaderTrustEvaluator,
    public val credentialStatusEvaluator: MobileWalletProximityCredentialStatusEvaluator =
        UnconfiguredMobileWalletProximityCredentialStatusEvaluator,
    public val applicationProfiles: MobileWalletProximityApplicationProfileRegistry =
        MobileWalletProximityApplicationProfileRegistry.Empty,
    public val maximumMessageBytes: Int = 1_048_576,
) {
    init {
        require(maximumMessageBytes in 1..16_777_216) {
            "Maximum proximity message size must be between 1 byte and 16 MiB"
        }
        require(
            profile != MobileWalletProximityProfile.EudiArf3Fcaf202608 ||
                readerPolicy == MobileWalletProximityReaderPolicy.RequireTrusted
        ) { "The selected EUDI profile requires an authenticated and trusted reader" }
    }
}

/** Stable, non-sensitive failure exposed by the Wallet SDK. */
public data class MobileWalletProximityError(
    public val category: MobileWalletProximityErrorCategory,
    public val code: String,
    public val message: String,
    public val recoverable: Boolean,
) {
    init {
        require(code.isNotBlank()) { "A proximity error code must not be blank" }
        require(message.isNotBlank()) { "A proximity error message must not be blank" }
    }
}

/** Layer-stable error category; raw dependency and platform exceptions are never exposed. */
public enum class MobileWalletProximityErrorCategory {
    Capability,
    Engagement,
    Transport,
    Protocol,
    ReaderAuthentication,
    Trust,
    Credential,
    HolderKey,
    ApplicationProfile,
    StaleSubmission,
    Policy,
    Internal,
}

/** One transport's four independent support dimensions. */
public data class MobileWalletProximityTransportCapability(
    public val implemented: Boolean,
    public val profilePermitted: Boolean,
    public val runtimeAvailable: Boolean,
    public val selected: Boolean,
    public val unavailable: MobileWalletProximityError? = null,
) {
    init {
        require(!selected || implemented && profilePermitted) {
            "A selected transport must be implemented and profile-permitted"
        }
        require(unavailable == null || !runtimeAvailable) {
            "A runtime-available transport cannot carry an unavailable reason"
        }
    }

    /** Whether a session may prepare this selected transport now. */
    public val mayStart: Boolean
        get() = implemented && profilePermitted && runtimeAvailable && selected
}

/** Side-effect-free prerequisite snapshot. No radio resource or session material has been created. */
public data class MobileWalletProximityCapabilities(
    public val profile: MobileWalletProximityProfile,
    public val bluetoothLowEnergy: MobileWalletProximityTransportCapability,
) {
    /** Whether all selected prerequisites currently allow a session to start. */
    public val mayStart: Boolean
        get() = bluetoothLowEnergy.mayStart
}

/** Scope covered by one reader-authentication statement. */
public enum class MobileWalletProximityReaderAuthenticationScope {
    Document,
    WholeRequest,
}

/** Cryptographic validity of reader authentication, kept separate from trust. */
public enum class MobileWalletProximityReaderAuthenticationValidity {
    Absent,
    Malformed,
    Invalid,
    Valid,
}

/** Trust outcome after valid reader authentication. */
public enum class MobileWalletProximityReaderTrustState {
    NotEvaluated,
    ValidButUntrusted,
    Revoked,
    Trusted,
}

/** Exact verified reader evidence supplied to an application-owned trust policy. */
public data class MobileWalletProximityReaderEvidence(
    public val scope: MobileWalletProximityReaderAuthenticationScope,
    public val documentRequestIndex: Int? = null,
    /** DER certificates in leaf-first order, encoded as unpadded Base64URL. */
    public val certificateChainDerBase64Url: List<String>,
) {
    init {
        require(documentRequestIndex == null || documentRequestIndex >= 0)
        require(certificateChainDerBase64Url.isNotEmpty()) {
            "Verified reader evidence requires a certificate chain"
        }
        require(certificateChainDerBase64Url.none(String::isBlank))
    }
}

/** Trust decision supplied by the hosting wallet application. */
public data class MobileWalletProximityReaderTrustDecision(
    public val state: MobileWalletProximityReaderTrustState,
    public val displayName: String? = null,
    public val reason: String? = null,
) {
    init {
        require(state != MobileWalletProximityReaderTrustState.NotEvaluated) {
            "A trust evaluator must return an evaluated trust state"
        }
        require(displayName == null || displayName.isNotBlank())
        require(reason == null || reason.isNotBlank())
    }
}

/** Explicit trust boundary for verified reader certificate evidence. */
public fun interface MobileWalletProximityReaderTrustEvaluator {
    public suspend fun evaluate(
        evidence: MobileWalletProximityReaderEvidence,
    ): MobileWalletProximityReaderTrustDecision
}

/** Default policy: validity is reported, but no reader certificate becomes trusted implicitly. */
public object UnconfiguredMobileWalletProximityReaderTrustEvaluator :
    MobileWalletProximityReaderTrustEvaluator {
    override suspend fun evaluate(
        evidence: MobileWalletProximityReaderEvidence,
    ): MobileWalletProximityReaderTrustDecision = MobileWalletProximityReaderTrustDecision(
        state = MobileWalletProximityReaderTrustState.ValidButUntrusted,
        reason = "No reader trust policy is configured",
    )
}

/** Display-safe reader authentication and trust fact for holder consent. */
public data class MobileWalletProximityReaderAuthentication(
    public val scope: MobileWalletProximityReaderAuthenticationScope,
    public val documentRequestIndex: Int?,
    public val validity: MobileWalletProximityReaderAuthenticationValidity,
    public val trust: MobileWalletProximityReaderTrustState,
    public val displayName: String? = null,
    public val reason: String? = null,
) {
    init {
        require(documentRequestIndex == null || documentRequestIndex >= 0)
        require(
            validity == MobileWalletProximityReaderAuthenticationValidity.Valid ||
                trust == MobileWalletProximityReaderTrustState.NotEvaluated
        ) { "Trust cannot be evaluated before reader authentication is valid" }
    }
}

/** Current status of a credential at the explicit application status boundary. */
public enum class MobileWalletProximityCredentialStatus {
    Valid,
    Revoked,
    Indeterminate,
}

/** Metadata-only status input; raw credential values are not handed to network providers. */
public data class MobileWalletProximityCredentialStatusInput(
    public val credentialId: String,
    public val docType: String,
    public val issuer: String?,
    public val validFrom: Instant,
    public val validUntil: Instant,
) {
    init {
        require(credentialId.isNotBlank() && docType.isNotBlank())
        require(validUntil >= validFrom)
    }
}

/** Explicit, optionally network-backed status boundary. The SDK itself performs no hidden lookup. */
public fun interface MobileWalletProximityCredentialStatusEvaluator {
    public suspend fun evaluate(
        credential: MobileWalletProximityCredentialStatusInput,
    ): MobileWalletProximityCredentialStatus
}

/** Default status policy relies on the locally verified MSO validity interval only. */
public object UnconfiguredMobileWalletProximityCredentialStatusEvaluator :
    MobileWalletProximityCredentialStatusEvaluator {
    override suspend fun evaluate(
        credential: MobileWalletProximityCredentialStatusInput,
    ): MobileWalletProximityCredentialStatus = MobileWalletProximityCredentialStatus.Valid
}

/** One candidate made available to an application-profile adapter. */
public data class MobileWalletProximityApplicationCredential(
    public val credentialId: String,
    public val docType: String,
    public val label: String?,
) {
    init {
        require(credentialId.isNotBlank() && docType.isNotBlank())
    }
}

/** Exact request and compatible candidates supplied to a versioned application profile. */
public data class MobileWalletProximityApplicationProfileInput(
    /** Exact DeviceRequest bytes, encoded as unpadded Base64URL. */
    public val deviceRequestBase64Url: String,
    public val credentials: List<MobileWalletProximityApplicationCredential>,
) {
    init {
        require(deviceRequestBase64Url.isNotBlank())
        require(credentials.distinctBy(MobileWalletProximityApplicationCredential::credentialId).size == credentials.size)
    }
}

/** One locally validated, display-safe application authorization value. */
public data class MobileWalletProximityApplicationAuthorizationDetail(
    public val id: String,
    public val label: String,
    public val value: String,
) {
    init {
        require(id.isNotBlank() && label.isNotBlank() && value.isNotBlank())
    }
}

/** Generic device-signed value proposed by a recognized application profile. */
public data class MobileWalletProximityDeviceSignedElement(
    public val credentialId: String,
    public val namespace: String,
    public val elementIdentifier: String,
    /** Exact encoded CBOR value as unpadded Base64URL. */
    public val valueCborBase64Url: String,
) {
    init {
        require(credentialId.isNotBlank())
        require(namespace.isNotBlank() && elementIdentifier.isNotBlank())
        require(valueCborBase64Url.isNotBlank())
    }
}

/** Validated output of one recognized, versioned wallet application profile. */
public data class MobileWalletProximityApplicationAuthorization(
    public val profileId: String,
    public val displayTitle: String,
    public val details: List<MobileWalletProximityApplicationAuthorizationDetail>,
    public val compatibleCredentialIds: Set<String>,
    public val deviceSignedElements: List<MobileWalletProximityDeviceSignedElement> = emptyList(),
    /** Profile-owned exact result digest, encoded as unpadded Base64URL SHA-256 bytes. */
    public val resultBindingDigestBase64Url: String,
) {
    init {
        require(profileId.isNotBlank() && displayTitle.isNotBlank())
        require(details.isNotEmpty() && details.distinctBy { it.id }.size == details.size)
        require(compatibleCredentialIds.isNotEmpty() && compatibleCredentialIds.none(String::isBlank))
        require(resultBindingDigestBase64Url.isNotBlank())
        require(deviceSignedElements.all { it.credentialId in compatibleCredentialIds })
        require(
            deviceSignedElements.distinctBy { Triple(it.credentialId, it.namespace, it.elementIdentifier) }.size ==
                deviceSignedElements.size
        )
    }
}

/** Result of asking one application profile to recognize and validate the exact request. */
public sealed interface MobileWalletProximityApplicationProfileResult {
    /** This profile does not recognize the request. */
    public data object NotRecognized : MobileWalletProximityApplicationProfileResult

    /** This profile recognized the request and produced a locally validated result. */
    public data class Recognized(
        public val authorization: MobileWalletProximityApplicationAuthorization,
    ) : MobileWalletProximityApplicationProfileResult

    /** This profile recognized the request but rejected invalid or unsupported application data. */
    public data class Rejected(public val reason: String) : MobileWalletProximityApplicationProfileResult {
        init { require(reason.isNotBlank()) }
    }
}

/** Versioned wallet-owned interpreter for application-specific request data. */
public interface MobileWalletProximityApplicationProfile {
    public val id: String

    public suspend fun evaluate(
        input: MobileWalletProximityApplicationProfileInput,
    ): MobileWalletProximityApplicationProfileResult
}

/** Ordered registry requiring at most one profile to recognize a request. */
public class MobileWalletProximityApplicationProfileRegistry(
    profiles: List<MobileWalletProximityApplicationProfile>,
) {
    internal val profiles: List<MobileWalletProximityApplicationProfile> = profiles.toList()

    init {
        require(this.profiles.none { it.id.isBlank() })
        require(this.profiles.distinctBy { it.id }.size == this.profiles.size) {
            "Application profile identifiers must be unique"
        }
    }

    public companion object {
        /** Registry that recognizes no application-specific request semantics. */
        public val Empty: MobileWalletProximityApplicationProfileRegistry =
            MobileWalletProximityApplicationProfileRegistry(emptyList())
    }
}

/** One requested or alternative data element shown during consent. */
public data class MobileWalletProximityRequestedElement(
    public val namespace: String,
    public val elementIdentifier: String,
    public val intentToRetain: Boolean,
    public val satisfiesRequestedElements: List<MobileWalletProximityElementReference> = emptyList(),
) {
    init {
        require(namespace.isNotBlank() && elementIdentifier.isNotBlank())
        require(satisfiesRequestedElements.distinct().size == satisfiesRequestedElements.size)
    }
}

/** Dependency-free namespace and element identifier. */
public data class MobileWalletProximityElementReference(
    public val namespace: String,
    public val elementIdentifier: String,
) {
    init { require(namespace.isNotBlank() && elementIdentifier.isNotBlank()) }
}

/** Eligible wallet credential projected without raw credential or key material. */
public data class MobileWalletProximityCredentialOption(
    public val credentialId: String,
    public val label: String?,
    public val issuer: String?,
    public val validUntil: Instant,
    /** Exact requested or alternative elements this credential would disclose. */
    public val requestedElements: List<MobileWalletProximityRequestedElement>,
) {
    init {
        require(credentialId.isNotBlank())
        require(requestedElements.isNotEmpty())
        require(requestedElements.distinctBy { it.namespace to it.elementIdentifier }.size == requestedElements.size)
    }
}

/** One satisfiable document request in an immutable review snapshot. */
public data class MobileWalletProximityDocumentReview(
    public val requestIndex: Int,
    public val docType: String,
    public val credentialOptions: List<MobileWalletProximityCredentialOption>,
) {
    init {
        require(requestIndex >= 0 && docType.isNotBlank())
        require(credentialOptions.isNotEmpty())
        require(credentialOptions.distinctBy { it.credentialId }.size == credentialOptions.size)
    }
}

/** Reader-asserted purpose hint associated with the selected use case. */
public data class MobileWalletProximityPurposeHint(
    public val type: String,
    public val code: Int,
    public val readerAsserted: Boolean = true,
) {
    init { require(type.isNotBlank()) }
}

/** Selected edition-2 use case projected for review. */
public data class MobileWalletProximityUseCase(
    public val index: Int,
    public val mandatory: Boolean,
    public val documentRequestIndices: List<Int>,
    public val purposeHints: List<MobileWalletProximityPurposeHint>,
) {
    init {
        require(index >= 0)
        require(documentRequestIndices.isNotEmpty() && documentRequestIndices.all { it >= 0 })
        require(documentRequestIndices.distinct().size == documentRequestIndices.size)
    }
}

/** Immutable holder-consent snapshot bound to one exchange and exact request. */
public data class MobileWalletProximityReview(
    public val exchange: Int,
    public val documents: List<MobileWalletProximityDocumentReview>,
    public val readerAuthentication: List<MobileWalletProximityReaderAuthentication>,
    public val useCases: List<MobileWalletProximityUseCase>,
    public val applicationAuthorizations: List<MobileWalletProximityApplicationAuthorization>,
) {
    init {
        require(exchange > 0 && documents.isNotEmpty())
        require(documents.distinctBy { it.requestIndex }.size == documents.size)
        require(applicationAuthorizations.distinctBy { it.profileId }.size == applicationAuthorizations.size)
    }
}

/** Credential and disclosure choice for exactly one reviewed document request. */
public data class MobileWalletProximityDocumentSubmission(
    public val requestIndex: Int,
    public val credentialId: String,
    public val disclosedElements: Set<MobileWalletProximityElementReference>,
) {
    init {
        require(requestIndex >= 0 && credentialId.isNotBlank())
        require(disclosedElements.isNotEmpty()) { "A document submission must disclose at least one reviewed element" }
    }
}

/** Complete holder choice for the current review; it is rebound and revalidated before response generation. */
public data class MobileWalletProximitySubmission(
    public val documents: List<MobileWalletProximityDocumentSubmission>,
    public val continueAfterResponse: Boolean = false,
) {
    init {
        require(documents.isNotEmpty())
        require(documents.distinctBy { it.requestIndex }.size == documents.size)
    }
}

/** User or host action accepted by a proximity session. */
public sealed interface MobileWalletProximityAction {
    public data class Approve(public val submission: MobileWalletProximitySubmission) :
        MobileWalletProximityAction

    public data object Decline : MobileWalletProximityAction
    public data object Cancel : MobileWalletProximityAction
}

/** Deterministic result of dispatching an action. */
public sealed interface MobileWalletProximityActionResult {
    public data object Accepted : MobileWalletProximityActionResult
    public data class Rejected(public val error: MobileWalletProximityError) :
        MobileWalletProximityActionResult
}

/** Public Wallet SDK session state; each variant carries only data valid for that phase. */
public sealed interface MobileWalletProximityState {
    public data object CheckingPrerequisites : MobileWalletProximityState
    public data class Preparing(public val profile: MobileWalletProximityProfile) : MobileWalletProximityState
    public data class EngagementReady(public val qrPayload: String) : MobileWalletProximityState {
        init { require(qrPayload.startsWith("mdoc:")) }
    }
    public data class Connecting(public val qrPayload: String) : MobileWalletProximityState {
        init { require(qrPayload.startsWith("mdoc:")) }
    }
    public data class AwaitingRequest(public val exchange: Int) : MobileWalletProximityState {
        init { require(exchange > 0) }
    }
    public data class ReviewRequired(public val review: MobileWalletProximityReview) :
        MobileWalletProximityState
    public data class AuthorizingHolderKey(public val exchange: Int) : MobileWalletProximityState {
        init { require(exchange > 0) }
    }
    public data class SendingResponse(public val exchange: Int) : MobileWalletProximityState {
        init { require(exchange > 0) }
    }
    public data class AwaitingNextRequest(public val completedExchanges: Int) : MobileWalletProximityState {
        init { require(completedExchanges > 0) }
    }
    public data class Terminating(public val exchange: Int) : MobileWalletProximityState {
        init { require(exchange > 0) }
    }
    public data class Completed(public val exchanges: Int, public val declined: Boolean) :
        MobileWalletProximityState {
        init { require(exchanges > 0) }
    }
    public data object Cancelled : MobileWalletProximityState
    public data class Failed(public val error: MobileWalletProximityError) : MobileWalletProximityState
}

/** Legal host actions derived exclusively from the current session state. */
public val MobileWalletProximityState.legalActions: Set<MobileWalletProximityActionType>
    get() = when (this) {
        is MobileWalletProximityState.ReviewRequired -> setOf(
            MobileWalletProximityActionType.Approve,
            MobileWalletProximityActionType.Decline,
            MobileWalletProximityActionType.Cancel,
        )
        MobileWalletProximityState.CheckingPrerequisites,
        is MobileWalletProximityState.Preparing,
        is MobileWalletProximityState.EngagementReady,
        is MobileWalletProximityState.Connecting,
        is MobileWalletProximityState.AwaitingRequest,
        is MobileWalletProximityState.AuthorizingHolderKey,
        is MobileWalletProximityState.SendingResponse,
        is MobileWalletProximityState.AwaitingNextRequest -> setOf(MobileWalletProximityActionType.Cancel)
        is MobileWalletProximityState.Terminating,
        is MobileWalletProximityState.Completed,
        MobileWalletProximityState.Cancelled,
        is MobileWalletProximityState.Failed -> emptySet()
    }

/** Action kinds used for state-derived UI affordances without constructing an action payload. */
public enum class MobileWalletProximityActionType {
    Approve,
    Decline,
    Cancel,
}

/** Single-use, wallet-owned proximity presentation session. */
public interface MobileWalletProximitySession {
    public val state: StateFlow<MobileWalletProximityState>

    /** Dispatches one state-bound action. Illegal or stale actions are rejected without side effects. */
    public suspend fun dispatch(action: MobileWalletProximityAction): MobileWalletProximityActionResult

    /** Idempotently cancels an active session and releases all session-owned resources. */
    public suspend fun close()
}
