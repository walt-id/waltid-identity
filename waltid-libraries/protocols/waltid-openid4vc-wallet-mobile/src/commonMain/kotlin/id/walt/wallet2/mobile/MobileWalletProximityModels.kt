package id.walt.wallet2.mobile

import kotlinx.coroutines.flow.StateFlow
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
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

/** Holder-to-reader engagement methods selected for a session. */
public enum class MobileWalletProximityEngagementMethod {
    Qr,
    Nfc,
}

/** Device-retrieval transports selected for a session. */
public enum class MobileWalletProximityRetrievalMethod {
    BluetoothLowEnergy,
    Nfc,
    WifiAware,
}

/** Holder authentication frozen for a reviewed document response. */
public enum class MobileWalletProximityDeviceAuthenticationMethod {
    Signature,
    Mac,
}

/** Explicit allowlist and preference applied before an immutable review is constructed. */
public enum class MobileWalletProximityDeviceAuthenticationPolicy(
    internal val preferenceOrder: List<MobileWalletProximityDeviceAuthenticationMethod>,
) {
    /** Require device signature; credentials whose keys cannot sign are unavailable. */
    SignatureOnly(listOf(MobileWalletProximityDeviceAuthenticationMethod.Signature)),

    /** Require device MAC; credentials whose keys cannot agree a MAC key are unavailable. */
    MacOnly(listOf(MobileWalletProximityDeviceAuthenticationMethod.Mac)),

    /** Prefer signature and fall back to MAC only before constructing the immutable review. */
    PreferSignature(
        listOf(
            MobileWalletProximityDeviceAuthenticationMethod.Signature,
            MobileWalletProximityDeviceAuthenticationMethod.Mac,
        ),
    ),

    /** Prefer MAC and fall back to signature only before constructing the immutable review. */
    PreferMac(
        listOf(
            MobileWalletProximityDeviceAuthenticationMethod.Mac,
            MobileWalletProximityDeviceAuthenticationMethod.Signature,
        ),
    ),
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
    public val engagementMethods: Set<MobileWalletProximityEngagementMethod> =
        setOf(MobileWalletProximityEngagementMethod.Qr),
    public val retrievalMethods: Set<MobileWalletProximityRetrievalMethod> =
        setOf(MobileWalletProximityRetrievalMethod.BluetoothLowEnergy),
    public val readerPolicy: MobileWalletProximityReaderPolicy =
        MobileWalletProximityReaderPolicy.AllowAnonymousOrUntrusted,
    public val deviceAuthenticationPolicy: MobileWalletProximityDeviceAuthenticationPolicy =
        MobileWalletProximityDeviceAuthenticationPolicy.SignatureOnly,
    public val readerTrustEvaluator: MobileWalletProximityReaderTrustEvaluator =
        UnconfiguredMobileWalletProximityReaderTrustEvaluator,
    public val credentialStatusEvaluator: MobileWalletProximityCredentialStatusEvaluator =
        UnconfiguredMobileWalletProximityCredentialStatusEvaluator,
    public val applicationProfiles: MobileWalletProximityApplicationProfileRegistry =
        MobileWalletProximityApplicationProfileRegistry.Empty,
    public val maximumMessageBytes: Int = 1_048_576,
) {
    init {
        require(engagementMethods.isNotEmpty()) { "At least one engagement method must be selected" }
        require(retrievalMethods.isNotEmpty()) { "At least one retrieval method must be selected" }
        require(maximumMessageBytes in 1..16_777_216) {
            "Maximum proximity message size must be between 1 byte and 16 MiB"
        }
        require(
            profile != MobileWalletProximityProfile.EudiArf3Fcaf202608 ||
                readerPolicy == MobileWalletProximityReaderPolicy.RequireTrusted
        ) { "The selected EUDI profile requires an authenticated and trusted reader" }
        require(
            profile != MobileWalletProximityProfile.EudiArf3Fcaf202608 ||
                deviceAuthenticationPolicy == MobileWalletProximityDeviceAuthenticationPolicy.SignatureOnly
        ) { "The selected EUDI profile requires device-signature authentication" }
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

/** Normalized host remediation suggested by a side-effect-free prerequisite check. */
public enum class MobileWalletProximityRemediationAction {
    RequestBluetoothPermission,
    OpenApplicationSettings,
    EnableBluetooth,
    UseSupportedDevice,
    Retry,
}

/** One transport's four independent support dimensions. */
public data class MobileWalletProximityTransportCapability(
    public val implemented: Boolean,
    public val profilePermitted: Boolean,
    public val runtimeAvailable: Boolean,
    public val selected: Boolean,
    public val unavailable: MobileWalletProximityError? = null,
    public val remediationActions: List<MobileWalletProximityRemediationAction> = emptyList(),
) {
    init {
        require(unavailable == null || !runtimeAvailable) {
            "A runtime-available transport cannot carry an unavailable reason"
        }
        require(runtimeAvailable || unavailable != null) {
            "A runtime-unavailable transport requires a stable unavailable reason"
        }
        require(!runtimeAvailable || remediationActions.isEmpty()) {
            "A runtime-available transport cannot require remediation"
        }
        require(remediationActions.distinct().size == remediationActions.size)
    }

    /** Whether a session may prepare this selected transport now. */
    public val mayStart: Boolean
        get() = implemented && profilePermitted && runtimeAvailable && selected
}

/** Side-effect-free prerequisite snapshot. No radio resource or session material has been created. */
public data class MobileWalletProximityCapabilities(
    public val profile: MobileWalletProximityProfile,
    public val qrEngagement: MobileWalletProximityTransportCapability,
    public val nfcEngagement: MobileWalletProximityTransportCapability,
    public val bluetoothLowEnergy: MobileWalletProximityTransportCapability,
    public val nfcRetrieval: MobileWalletProximityTransportCapability,
    public val wifiAwareRetrieval: MobileWalletProximityTransportCapability,
) {
    init {
        require(qrEngagement.selected || nfcEngagement.selected) {
            "At least one engagement capability must be selected"
        }
        require(bluetoothLowEnergy.selected || nfcRetrieval.selected || wifiAwareRetrieval.selected) {
            "At least one retrieval capability must be selected"
        }
    }

    /** Whether at least one selected engagement and one selected retrieval method can start. */
    public val mayStart: Boolean
        get() = listOf(qrEngagement, nfcEngagement).any { it.mayStart } &&
            listOf(bluetoothLowEnergy, nfcRetrieval, wifiAwareRetrieval).any { it.mayStart }

    /** Distinct host remediations for selected unavailable methods. */
    public val remediationActions: List<MobileWalletProximityRemediationAction>
        get() = listOf(
            qrEngagement,
            nfcEngagement,
            bluetoothLowEnergy,
            nfcRetrieval,
            wifiAwareRetrieval,
        ).filter { it.selected }.flatMap { it.remediationActions }.distinct()
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

/** Result of validating the reader certificate path against explicitly configured trust material. */
public enum class MobileWalletProximityReaderCertificatePathState {
    NotEvaluated,
    Invalid,
    Valid,
}

/** Reader-certificate revocation fact, kept independent from path validity and product trust. */
public enum class MobileWalletProximityReaderRevocationState {
    NotChecked,
    Good,
    Revoked,
    Indeterminate,
}

/** Optional RICAL evidence fact. A match never establishes product trust by itself. */
public enum class MobileWalletProximityRicalState {
    NotEvaluated,
    Unavailable,
    Invalid,
    NoMatchingAuthority,
    Matched,
}

/** Exact verified reader evidence supplied to an application-owned trust policy. */
public data class MobileWalletProximityReaderEvidence(
    public val scope: MobileWalletProximityReaderAuthenticationScope,
    public val documentRequestIndex: Int? = null,
    /** Zero-based statement index within the authentication scope. */
    public val authenticationIndex: Int = 0,
    /** DER certificates in leaf-first order, encoded as unpadded Base64URL. */
    public val certificateChainDerBase64Url: List<String>,
) {
    init {
        require(
            (scope == MobileWalletProximityReaderAuthenticationScope.Document) ==
                (documentRequestIndex != null)
        ) { "Document-scoped evidence requires exactly one document request index" }
        require(documentRequestIndex == null || documentRequestIndex >= 0)
        require(authenticationIndex >= 0)
        require(certificateChainDerBase64Url.isNotEmpty()) {
            "Verified reader evidence requires a certificate chain"
        }
        require(certificateChainDerBase64Url.all(String::isNonEmptyBase64Url)) {
            "Reader certificates must be non-empty unpadded Base64URL values"
        }
    }
}

/** Trust decision supplied by the hosting wallet application. */
public data class MobileWalletProximityReaderTrustDecision(
    public val state: MobileWalletProximityReaderTrustState,
    public val certificatePath: MobileWalletProximityReaderCertificatePathState =
        MobileWalletProximityReaderCertificatePathState.NotEvaluated,
    public val revocation: MobileWalletProximityReaderRevocationState =
        MobileWalletProximityReaderRevocationState.NotChecked,
    public val rical: MobileWalletProximityRicalState = MobileWalletProximityRicalState.NotEvaluated,
    public val displayName: String? = null,
    public val reason: String? = null,
) {
    init {
        require(state != MobileWalletProximityReaderTrustState.NotEvaluated) {
            "A trust evaluator must return an evaluated trust state"
        }
        require(displayName == null || displayName.isNotBlank())
        require(reason == null || reason.isNotBlank())
        require(state != MobileWalletProximityReaderTrustState.Revoked ||
            revocation == MobileWalletProximityReaderRevocationState.Revoked) {
            "A revoked trust decision requires a revoked certificate result"
        }
        require(revocation != MobileWalletProximityReaderRevocationState.Revoked ||
            state == MobileWalletProximityReaderTrustState.Revoked) {
            "A revoked certificate result requires a revoked trust decision"
        }
        require(state != MobileWalletProximityReaderTrustState.Trusted ||
            certificatePath == MobileWalletProximityReaderCertificatePathState.Valid) {
            "A trusted reader requires a valid certificate path"
        }
        require(state != MobileWalletProximityReaderTrustState.Trusted ||
            revocation != MobileWalletProximityReaderRevocationState.Indeterminate) {
            "A reader with indeterminate revocation status cannot be trusted"
        }
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
    /** Zero-based statement index within the authentication scope. */
    public val authenticationIndex: Int = 0,
    public val validity: MobileWalletProximityReaderAuthenticationValidity,
    public val trust: MobileWalletProximityReaderTrustState,
    public val certificatePath: MobileWalletProximityReaderCertificatePathState =
        MobileWalletProximityReaderCertificatePathState.NotEvaluated,
    public val revocation: MobileWalletProximityReaderRevocationState =
        MobileWalletProximityReaderRevocationState.NotChecked,
    public val rical: MobileWalletProximityRicalState = MobileWalletProximityRicalState.NotEvaluated,
    public val displayName: String? = null,
    public val reason: String? = null,
) {
    init {
        require(
            (scope == MobileWalletProximityReaderAuthenticationScope.Document) ==
                (documentRequestIndex != null)
        ) { "Document-scoped authentication requires exactly one document request index" }
        require(documentRequestIndex == null || documentRequestIndex >= 0)
        require(authenticationIndex >= 0)
        require(
            validity == MobileWalletProximityReaderAuthenticationValidity.Valid ||
                trust == MobileWalletProximityReaderTrustState.NotEvaluated
        ) { "Trust cannot be evaluated before reader authentication is valid" }
        require(validity == MobileWalletProximityReaderAuthenticationValidity.Valid ||
            certificatePath == MobileWalletProximityReaderCertificatePathState.NotEvaluated) {
            "A certificate path cannot be evaluated before reader authentication is valid"
        }
        require(validity == MobileWalletProximityReaderAuthenticationValidity.Valid ||
            revocation == MobileWalletProximityReaderRevocationState.NotChecked) {
            "Revocation cannot be evaluated before reader authentication is valid"
        }
        require(validity == MobileWalletProximityReaderAuthenticationValidity.Valid ||
            rical == MobileWalletProximityRicalState.NotEvaluated) {
            "RICAL cannot be evaluated before reader authentication is valid"
        }
        require(
            trust != MobileWalletProximityReaderTrustState.Revoked ||
                revocation == MobileWalletProximityReaderRevocationState.Revoked
        )
        require(
            revocation != MobileWalletProximityReaderRevocationState.Revoked ||
                trust == MobileWalletProximityReaderTrustState.Revoked
        )
        require(
            trust != MobileWalletProximityReaderTrustState.Trusted ||
                certificatePath == MobileWalletProximityReaderCertificatePathState.Valid
        )
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
    public val requestedDocuments: List<MobileWalletProximityApplicationDocumentRequest>,
    public val readerAuthentication: List<MobileWalletProximityReaderAuthentication>,
) {
    init {
        require(deviceRequestBase64Url.isNotBlank())
        require(credentials.distinctBy(MobileWalletProximityApplicationCredential::credentialId).size == credentials.size)
        require(requestedDocuments.isNotEmpty())
        require(requestedDocuments.distinctBy { it.requestIndex }.size == requestedDocuments.size)
        val requestIndices = requestedDocuments.map { it.requestIndex }.toSet()
        require(readerAuthentication.all { authentication ->
            authentication.documentRequestIndex == null || authentication.documentRequestIndex in requestIndices
        }) { "Reader authentication refers to a document outside this request" }
        require(readerAuthentication.distinctBy { Triple(it.scope, it.documentRequestIndex, it.authenticationIndex) }.size ==
            readerAuthentication.size) { "Reader-authentication scopes must be unique" }
    }
}

/** Dependency-free parsed request facts supplied to application-profile adapters. */
public data class MobileWalletProximityApplicationDocumentRequest(
    public val requestIndex: Int,
    public val docType: String,
    public val requestedElements: List<MobileWalletProximityRequestedElement>,
) {
    init {
        require(requestIndex >= 0 && docType.isNotBlank() && requestedElements.isNotEmpty())
        require(requestedElements.distinctBy { it.namespace to it.elementIdentifier }.size == requestedElements.size)
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
        require(valueCborBase64Url.isNonEmptyBase64Url()) {
            "A device-signed value must be non-empty unpadded Base64URL"
        }
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
        require(resultBindingDigestBase64Url.isSha256Base64Url()) {
            "Application-profile binding must be an unpadded Base64URL SHA-256 digest"
        }
        require(deviceSignedElements.all { it.credentialId in compatibleCredentialIds })
        require(
            deviceSignedElements.distinctBy { Triple(it.credentialId, it.namespace, it.elementIdentifier) }.size ==
                deviceSignedElements.size
        )
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun String.isSha256Base64Url(): Boolean =
    isNotBlank() && !contains('=') && runCatching {
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(this).size == 32
    }.getOrDefault(false)

@OptIn(ExperimentalEncodingApi::class)
private fun String.isNonEmptyBase64Url(): Boolean =
    isNotBlank() && !contains('=') && runCatching {
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(this).isNotEmpty()
    }.getOrDefault(false)

/** Result of asking one application profile to recognize and validate the exact request. */
public sealed interface MobileWalletProximityApplicationProfileResult {
    /** This profile does not recognize the request. */
    public data object NotRecognized : MobileWalletProximityApplicationProfileResult

    /** This profile recognized the request and produced a locally validated result. */
    public data class Recognized(
        public val authorization: MobileWalletProximityApplicationAuthorization,
    ) : MobileWalletProximityApplicationProfileResult

    /**
     * This profile recognized the request but rejected invalid or unsupported application data.
     * [reason] must be safe to expose to the wallet UI.
     */
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
    public val deviceAuthentication: MobileWalletProximityDeviceAuthenticationMethod,
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
        val requestIndices = documents.map { it.requestIndex }.toSet()
        require(readerAuthentication.all { authentication ->
            authentication.documentRequestIndex == null || authentication.documentRequestIndex in requestIndices
        }) { "Reader authentication refers to a document outside this review" }
        require(readerAuthentication.distinctBy {
            Triple(it.scope, it.documentRequestIndex, it.authenticationIndex)
        }.size == readerAuthentication.size) { "Reader-authentication statements must be unique" }
        require(useCases.distinctBy { it.index }.size == useCases.size)
        require(useCases.all { useCase -> useCase.documentRequestIndices.all { it in requestIndices } }) {
            "A selected use case refers to a document outside this review"
        }
        require(applicationAuthorizations.distinctBy { it.profileId }.size == applicationAuthorizations.size)
        val credentialIds = documents.flatMap { it.credentialOptions }.map { it.credentialId }.toSet()
        require(applicationAuthorizations.all { it.compatibleCredentialIds.all(credentialIds::contains) }) {
            "An application authorization refers to a credential outside this review"
        }
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
    public data object RetryPrerequisites : MobileWalletProximityAction
    public data class ReportRemediation(
        public val action: MobileWalletProximityRemediationAction,
        public val result: MobileWalletProximityHostActionResult,
    ) : MobileWalletProximityAction
}

/** Privacy-safe outcome of a system surface performed by the host application. */
public enum class MobileWalletProximityHostActionResult {
    Completed,
    Cancelled,
    Failed,
}

/** Prepared engagement presented by the host UI. */
public sealed interface MobileWalletProximityEngagement {
    public data class Qr(public val payload: String) : MobileWalletProximityEngagement {
        init { require(payload.startsWith("mdoc:")) }
    }

    public data object Nfc : MobileWalletProximityEngagement
}

/** One protected-key operation required by a frozen approved document response. */
public data class MobileWalletProximityHolderAuthorizationRequest(
    public val requestIndex: Int,
    public val credentialId: String,
    public val deviceAuthentication: MobileWalletProximityDeviceAuthenticationMethod,
) {
    init {
        require(requestIndex >= 0 && credentialId.isNotBlank())
    }
}

/** Exact holder-key authorization context for a frozen approved submission. */
public data class MobileWalletProximityHolderAuthorization(
    public val exchange: Int,
    public val requests: List<MobileWalletProximityHolderAuthorizationRequest>,
) {
    init {
        require(exchange > 0 && requests.isNotEmpty())
        require(requests.distinctBy { it.requestIndex }.size == requests.size)
    }
}

/** Deterministic result of dispatching an action. */
public sealed interface MobileWalletProximityActionResult {
    public data object Accepted : MobileWalletProximityActionResult
    public data class Rejected(public val error: MobileWalletProximityError) :
        MobileWalletProximityActionResult
}

/** Public Wallet SDK session state; each variant carries only data valid for that phase. */
public sealed interface MobileWalletProximityState {
    public data class CheckingPrerequisites(
        public val capabilities: MobileWalletProximityCapabilities,
    ) : MobileWalletProximityState
    public data class Preparing(public val profile: MobileWalletProximityProfile) : MobileWalletProximityState
    public data class EngagementReady(
        public val engagements: List<MobileWalletProximityEngagement>,
    ) : MobileWalletProximityState {
        init { require(engagements.isNotEmpty() && engagements.distinctBy { it::class }.size == engagements.size) }
    }
    public data class Connecting(
        public val engagements: List<MobileWalletProximityEngagement>,
    ) : MobileWalletProximityState {
        init { require(engagements.isNotEmpty() && engagements.distinctBy { it::class }.size == engagements.size) }
    }
    public data class AwaitingRequest(public val exchange: Int) : MobileWalletProximityState {
        init { require(exchange > 0) }
    }
    public data class ReviewRequired(public val review: MobileWalletProximityReview) :
        MobileWalletProximityState
    public data class AuthorizingHolderKey(
        public val authorization: MobileWalletProximityHolderAuthorization,
    ) : MobileWalletProximityState
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
        is MobileWalletProximityState.CheckingPrerequisites -> setOf(
            MobileWalletProximityActionType.RetryPrerequisites,
            MobileWalletProximityActionType.ReportRemediation,
            MobileWalletProximityActionType.Cancel,
        )
        is MobileWalletProximityState.ReviewRequired -> setOf(
            MobileWalletProximityActionType.Approve,
            MobileWalletProximityActionType.Decline,
            MobileWalletProximityActionType.Cancel,
        )
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
    RetryPrerequisites,
    ReportRemediation,
}

/** Single-use, wallet-owned proximity presentation session. */
public interface MobileWalletProximitySession {
    public val state: StateFlow<MobileWalletProximityState>

    /** Dispatches one state-bound action. Illegal or stale actions are rejected without side effects. */
    public suspend fun dispatch(action: MobileWalletProximityAction): MobileWalletProximityActionResult

    /** Idempotently cancels an active session and releases all session-owned resources. */
    public suspend fun close()
}
