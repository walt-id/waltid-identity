package id.walt.wallet2.mobile

import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.StateFlow
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Instant

/**
 * Versioned mdoc interoperability boundary selected for one proximity session.
 *
 * @property id Stable identifier suitable for configuration and diagnostics.
 */
public enum class ProximityProfile(public val id: String) {
    /** ISO/IEC 18013-5:2021 compatibility boundary. */
    Iso1801352021("iso-18013-5:2021"),

    /** ISO/IEC 18013-5 edition-2 DIS implementation boundary. */
    Iso180135Edition2Dis2026("iso-18013-5:ed2-dis-2026"),

    /** EUDI ARF 3.0 plus the pinned August 2026 FCAF restrictions. */
    EudiArf3Fcaf202608("eudi-arf:3.0+fcaf:2026-08"),
}

/** BLE roles a holder may prepare for one in-person presentation. */
public enum class ProximityBleRoles {
    CentralClient,
    PeripheralServer,
    Dual,
}

/** Bearer selection policy kept separate from the app-visible session state. */
public enum class ProximityBleBearerPolicy {
    GattOnly,
    PreferL2cap,
}

/**
 * Complete BLE bearer configuration; it cannot exist unless BLE retrieval is selected.
 *
 * @property roles Holder GATT roles prepared for the session.
 * @property bearerPolicy GATT/L2CAP selection policy applied within the selected roles.
 */
public data class ProximityBleConfiguration(
    public val roles: ProximityBleRoles = ProximityBleRoles.Dual,
    public val bearerPolicy: ProximityBleBearerPolicy =
        ProximityBleBearerPolicy.PreferL2cap,
)

/**
 * Complete conventional NFC retrieval length contract.
 *
 * @property maximumCommandDataLength Maximum command-data bytes accepted by the holder.
 * @property maximumResponseDataLength Maximum response-data bytes returned by the holder.
 */
public data class ProximityNfcRetrievalConfiguration(
    public val maximumCommandDataLength: Int = 65_535,
    public val maximumResponseDataLength: Int = 65_536,
) {
    init {
        require(maximumCommandDataLength in 255..65_535)
        require(maximumResponseDataLength in 256..65_536)
    }
}

/**
 * Nonempty conventional retrieval plan used by QR or NFC handover.
 * @property bluetoothLowEnergy Optional BLE role and bearer policy.
 * @property nfc Optional conventional NFC command/response contract.
 * @property wifiAware Whether to offer Wi-Fi Aware with mandatory NCS-SK-128 security.
 */
public data class ProximityRetrievalOptions(
    public val bluetoothLowEnergy: ProximityBleConfiguration? = ProximityBleConfiguration(),
    public val nfc: ProximityNfcRetrievalConfiguration? = null,
    public val wifiAware: Boolean = false,
) {
    init { require(bluetoothLowEnergy != null || nfc != null || wifiAware) { "A retrieval plan must contain a bearer" } }
}

/** Conventional NFC Forum handover selection; provisional NFCv2 has its own session variant. */
public enum class ProximityNfcHandover {
    /** Holder-selected retrieval methods. */
    Static,
    /** Reader-selected retrieval method. */
    Negotiated,
}

/** Owns engagement and compatible retrieval together for one single-use session. */
public sealed interface ProximitySessionConfiguration {
    /**
     * QR engagement with a nonempty conventional retrieval plan.
     * @property retrieval Bearers advertised by the QR engagement.
     */
    public data class Qr(
        public val retrieval: ProximityRetrievalOptions =
            ProximityRetrievalOptions(),
    ) : ProximitySessionConfiguration

    /**
     * Conventional NFC handover with an optional, separately selected QR fallback.
     * @property handover NFC Forum handover mode.
     * @property retrieval Bearers offered through NFC handover.
     * @property qrFallback Nonempty QR plan. Shared bearers use the same BLE policy and conventional NFC length limits.
     */
    public data class ConventionalNfc(
        public val handover: ProximityNfcHandover,
        public val retrieval: ProximityRetrievalOptions,
        public val qrFallback: ProximityRetrievalOptions? = null,
    ) : ProximitySessionConfiguration {
        init {
            requireSharedBlePolicy(retrieval.bluetoothLowEnergy, qrFallback?.bluetoothLowEnergy)
            require(retrieval.nfc == null || qrFallback?.nfc == null || retrieval.nfc == qrFallback.nfc) {
                "QR and NFC handover must use the same conventional NFC retrieval length limits"
            }
        }
    }

    /**
     * Provisional NFCv2 engagement and its mandatory same-channel retrieval.
     * @property maximumCommandDataLength Maximum data accepted by the NFCv2 application.
     * @property bluetoothLowEnergy Optional NFCv2 hybrid BLE bearer.
     * @property wifiAware Whether to offer a hybrid Wi-Fi Aware bearer with mandatory NCS-SK-128 security.
     * @property qrFallback Nonempty conventional plan advertised by QR, when selected.
     */
    public data class ProvisionalNfcV2(
        public val maximumCommandDataLength: Int = 65_536,
        public val bluetoothLowEnergy: ProximityBleConfiguration? = null,
        public val qrFallback: ProximityRetrievalOptions? = null,
        public val wifiAware: Boolean = false,
    ) : ProximitySessionConfiguration {
        init {
            require(maximumCommandDataLength in 1..65_536)
            requireSharedBlePolicy(bluetoothLowEnergy, qrFallback?.bluetoothLowEnergy)
        }
    }
}

// One session probes a BLE role/bearer policy once and may prepare distinct endpoints for its routes.
private fun requireSharedBlePolicy(
    nfc: ProximityBleConfiguration?, qr: ProximityBleConfiguration?,
) { require(nfc == null || qr == null || nfc == qr) { "QR and NFC must use the same BLE role and bearer policy" } }

internal val ProximitySessionConfiguration.qrRetrieval: ProximityRetrievalOptions?
    get() = when (this) {
        is ProximitySessionConfiguration.Qr -> retrieval
        is ProximitySessionConfiguration.ConventionalNfc -> qrFallback
        is ProximitySessionConfiguration.ProvisionalNfcV2 -> qrFallback
    }

internal val ProximitySessionConfiguration.nfcRetrieval: ProximityRetrievalOptions?
    get() = (this as? ProximitySessionConfiguration.ConventionalNfc)?.retrieval

internal val ProximitySessionConfiguration.nfcBle: ProximityBleConfiguration?
    get() = when (this) {
        is ProximitySessionConfiguration.Qr -> null
        is ProximitySessionConfiguration.ConventionalNfc -> retrieval.bluetoothLowEnergy
        is ProximitySessionConfiguration.ProvisionalNfcV2 -> bluetoothLowEnergy
    }

internal val ProximitySessionConfiguration.nfcWifiAware: Boolean
    get() = when (this) {
        is ProximitySessionConfiguration.Qr -> false
        is ProximitySessionConfiguration.ConventionalNfc -> retrieval.wifiAware
        is ProximitySessionConfiguration.ProvisionalNfcV2 -> wifiAware
    }

internal val ProximitySessionConfiguration.wifiAwareSelected: Boolean
    get() = nfcWifiAware || qrRetrieval?.wifiAware == true

internal val ProximitySessionConfiguration.bleConfiguration: ProximityBleConfiguration?
    get() = nfcBle ?: qrRetrieval?.bluetoothLowEnergy

/** Holder authentication frozen for a reviewed document response. */
public enum class ProximityDeviceAuthenticationMethod {
    Signature,
    Mac,
}

/** Explicit allowlist and preference applied before an immutable review is constructed. */
public enum class ProximityDeviceAuthenticationPolicy(
    internal val preferenceOrder: List<ProximityDeviceAuthenticationMethod>,
) {
    /** Require device signature; credentials whose keys cannot sign are unavailable. */
    SignatureOnly(listOf(ProximityDeviceAuthenticationMethod.Signature)),

    /** Require device MAC; credentials whose keys cannot agree a MAC key are unavailable. */
    MacOnly(listOf(ProximityDeviceAuthenticationMethod.Mac)),

    /** Prefer signature and fall back to MAC only before constructing the immutable review. */
    PreferSignature(
        listOf(
            ProximityDeviceAuthenticationMethod.Signature,
            ProximityDeviceAuthenticationMethod.Mac,
        ),
    ),

    /** Prefer MAC and fall back to signature only before constructing the immutable review. */
    PreferMac(
        listOf(
            ProximityDeviceAuthenticationMethod.Mac,
            ProximityDeviceAuthenticationMethod.Signature,
        ),
    ),
}

/** Policy applied after reader authentication and trust facts have been evaluated. */
public enum class ProximityReaderPolicy {
    /** Anonymous or cryptographically valid but untrusted readers may reach explicit holder consent. */
    AllowAnonymousOrUntrusted,

    /** A cryptographically valid and trusted reader is required before any disclosure preview is shown. */
    RequireTrusted,
}

/**
 * Immutable configuration for one single-use proximity session.
 *
 * @property profile Protocol and application-profile boundary to enforce.
 * @property session Engagement and compatible retrieval plans owned by this session.
 * @property readerPolicy Trust threshold applied before disclosure review.
 * @property deviceAuthenticationPolicy Allowed and preferred holder-authentication methods.
 * @property readerTrustEvaluator Application-owned reader trust boundary.
 * @property credentialStatusEvaluator Application-owned credential status boundary.
 * @property applicationProfiles Ordered application-profile registry for request extensions.
 * @property maximumMessageBytes Maximum accepted encoded proximity message size.
 */
public data class ProximityConfiguration(
    public val profile: ProximityProfile =
        ProximityProfile.Iso180135Edition2Dis2026,
    public val session: ProximitySessionConfiguration = ProximitySessionConfiguration.Qr(),
    public val readerPolicy: ProximityReaderPolicy =
        ProximityReaderPolicy.AllowAnonymousOrUntrusted,
    public val deviceAuthenticationPolicy: ProximityDeviceAuthenticationPolicy =
        ProximityDeviceAuthenticationPolicy.SignatureOnly,
    public val readerTrustEvaluator: ProximityReaderTrustEvaluator =
        UnconfiguredProximityReaderTrustEvaluator,
    public val credentialStatusEvaluator: ProximityCredentialStatusEvaluator =
        UnconfiguredProximityCredentialStatusEvaluator,
    public val applicationProfiles: ProximityApplicationProfileRegistry =
        ProximityApplicationProfileRegistry.Empty,
    public val maximumMessageBytes: Int = 1_048_576,
    /** Explicit holder-approval behavior, independent of engagement and retrieval methods. */
    public val approval: ProximityApproval = ProximityApproval.AskEachTime,
) {
    init {
        require(maximumMessageBytes in 1..16_777_216) {
            "Maximum proximity message size must be between 1 byte and 16 MiB"
        }
        require(
            profile != ProximityProfile.EudiArf3Fcaf202608 ||
                readerPolicy == ProximityReaderPolicy.RequireTrusted
        ) { "The selected EUDI profile requires an authenticated and trusted reader" }
        require(
            profile != ProximityProfile.EudiArf3Fcaf202608 ||
                deviceAuthenticationPolicy == ProximityDeviceAuthenticationPolicy.SignatureOnly
        ) { "The selected EUDI profile requires device-signature authentication" }
        require(profile != ProximityProfile.Iso1801352021 || session !is ProximitySessionConfiguration.ProvisionalNfcV2) {
            "NFC Engagement v2 is not part of the ISO/IEC 18013-5:2021 compatibility profile"
        }

    }
}

/**
 * Stable, non-sensitive failure exposed by the Wallet SDK.
 *
 * @property category Layer-stable failure category.
 * @property code Stable machine-readable error code.
 * @property message Display-safe diagnostic message.
 * @property recovery Whether recovery uses the prerequisite loop or requires a new session.
 */
public data class ProximityError(
    public val category: ProximityErrorCategory,
    public val code: String,
    public val message: String,
    public val recovery: ProximityRecovery,
) {
    /** Host actions for this safe error code; terminal failures require a fresh session afterward. */
    public val remediationActions: List<ProximityRemediationAction>
        get() = code.toRemediationActions()

    init {
        require(code.isNotBlank()) { "A proximity error code must not be blank" }
        require(message.isNotBlank()) { "A proximity error message must not be blank" }
    }
}

/** Recovery action supported by the phase that reported a failure. */
public enum class ProximityRecovery {
    /** No retry is suggested for this result. */
    None,
    /** Remediate and recheck prerequisites in this still-active session. */
    RetryPrerequisites,
    /** This session is terminal; create a fresh session to try again. */
    StartNewSession,
}

/** Layer-stable error category; raw dependency and platform exceptions are never exposed. */
public enum class ProximityErrorCategory {
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
public enum class ProximityRemediationAction {
    RequestBluetoothPermission,
    RequestNearbyWifiPermission,
    RequestLocalNetworkPermission,
    OpenApplicationSettings,
    EnableBluetooth,
    EnableWifi,
    EnableNfc,
    UseSupportedDevice,
    Retry,
}

/**
 * One transport's independent support dimensions.
 *
 * @property implemented Whether this SDK build implements the method.
 * @property profilePermitted Whether the selected profile permits the method.
 * @property selected Whether the session configuration selected the method.
 */
public data class ProximityTransportCapability(
    public val implemented: Boolean,
    public val profilePermitted: Boolean,
    /** Independent runtime observation; an unselected route need not have been probed. */
    public val runtime: ProximityRuntimeObservation,
    public val selected: Boolean,
) {
    /** Whether a session may prepare this selected transport now. */
    public val mayStart: Boolean
        get() = implemented && profilePermitted && runtime is ProximityRuntimeObservation.Available && selected

    /** Whether runtime availability was positively observed. */
    public val runtimeAvailable: Boolean get() = runtime is ProximityRuntimeObservation.Available
    /** Observed runtime failure, if a check was performed and failed. */
    public val unavailable: ProximityError?
        get() = (runtime as? ProximityRuntimeObservation.Unavailable)?.error
    /** Host actions from the runtime observation. */
    public val remediationActions: List<ProximityRemediationAction>
        get() = (runtime as? ProximityRuntimeObservation.Unavailable)?.remediationActions.orEmpty()
}

/** Runtime evidence is independent of whether the host selected a method. */
public sealed interface ProximityRuntimeObservation {
    /** No runtime probe has been performed for this method. */
    public data object NotChecked : ProximityRuntimeObservation
    /** The runtime probe succeeded. */
    public data object Available : ProximityRuntimeObservation
    /**
     * The runtime probe failed with an explicit host recovery path.
     * @property error Observed failure with its recovery semantics.
     * @property remediationActions Available host actions for this observation.
     */
    public data class Unavailable(
        public val error: ProximityError,
        public val remediationActions: List<ProximityRemediationAction> = emptyList(),
    ) : ProximityRuntimeObservation {
        init { require(remediationActions.distinct().size == remediationActions.size) }
    }
}

/**
 * Side-effect-free prerequisite snapshot. No radio resource or session material has been created.
 *
 * @property profile Profile against which the capabilities were evaluated.
 * @property qrEngagement QR engagement capability.
 * @property nfcEngagement NFC engagement capability.
 * @property bluetoothLowEnergy BLE device-retrieval capability.
 * @property nfcRetrieval Conventional NFC device-retrieval capability.
 * @property nfcV2Retrieval Provisional NFCv2 same-channel device-retrieval capability.
 * @property wifiAwareRetrieval Wi-Fi Aware device-retrieval capability.
 */
public data class ProximityCapabilities(
    public val profile: ProximityProfile,
    /** Selected plans used to relate independent transport observations to viable routes. */
    public val session: ProximitySessionConfiguration,
    public val qrEngagement: ProximityTransportCapability,
    public val nfcEngagement: ProximityTransportCapability,
    public val bluetoothLowEnergy: ProximityTransportCapability,
    public val nfcRetrieval: ProximityTransportCapability,
    public val nfcV2Retrieval: ProximityTransportCapability,
    public val wifiAwareRetrieval: ProximityTransportCapability,
) {
    init {
        require(
            qrEngagement.selected == (session.qrRetrieval != null) &&
                nfcEngagement.selected == (session !is ProximitySessionConfiguration.Qr) &&
                bluetoothLowEnergy.selected == (session.bleConfiguration != null) &&
                nfcRetrieval.selected == (session.nfcRetrieval?.nfc != null || session.qrRetrieval?.nfc != null) &&
                nfcV2Retrieval.selected == (session is ProximitySessionConfiguration.ProvisionalNfcV2) &&
                wifiAwareRetrieval.selected == session.wifiAwareSelected
        ) { "Capability selection must match the owning session retrieval plans" }
        require(!nfcV2Retrieval.mayStart || nfcEngagement.mayStart) {
            "NFCv2 same-channel retrieval cannot start without NFC engagement"
        }
    }

    /** Whether the selected QR plan has an available engagement and retrieval bearer. */
    public val qrMayStart: Boolean get() = qrEngagement.mayStart && planMayStart(session.qrRetrieval)

    /** Whether the selected NFC plan has an available engagement and retrieval bearer. */
    public val nfcMayStart: Boolean get() = nfcEngagement.mayStart && when (session) {
        is ProximitySessionConfiguration.Qr -> false
        is ProximitySessionConfiguration.ConventionalNfc -> planMayStart(session.retrieval)
        is ProximitySessionConfiguration.ProvisionalNfcV2 -> nfcV2Retrieval.mayStart
    }

    /** Whether at least one complete selected route can start. */
    public val mayStart: Boolean get() = qrMayStart || nfcMayStart

    private fun planMayStart(plan: ProximityRetrievalOptions?): Boolean = plan != null && (
        (plan.bluetoothLowEnergy != null && bluetoothLowEnergy.mayStart) ||
            (plan.nfc != null && nfcRetrieval.mayStart) ||
            (plan.wifiAware && wifiAwareRetrieval.mayStart)
        )

    /** Distinct host remediations for selected unavailable methods. */
    public val remediationActions: List<ProximityRemediationAction>
        get() = listOf(
            qrEngagement,
            nfcEngagement,
            bluetoothLowEnergy,
            nfcRetrieval,
            nfcV2Retrieval,
            wifiAwareRetrieval,
        ).filter { it.selected }.flatMap { it.remediationActions }.distinct()
}

/** Scope covered by one reader-authentication statement. */
public sealed interface ProximityReaderAuthenticationScope {
    /**
     * A verified or attempted statement for exactly one document request.
     * @property index Nonnegative zero-based document request index.
     */
    public data class Document(public val index: Int) : ProximityReaderAuthenticationScope {
        init { require(index >= 0) }
    }

    /** A statement covering the complete device request. */
    public data object WholeRequest : ProximityReaderAuthenticationScope
}

/** Document index derived from the scope, without an independent nullable constructor argument. */
internal val ProximityReaderAuthenticationScope.documentRequestIndex: Int?
    get() = (this as? ProximityReaderAuthenticationScope.Document)?.index

/** Cryptographic validity of reader authentication, kept separate from trust. */
public enum class ProximityReaderAuthenticationValidity {
    Absent,
    Malformed,
    Invalid,
    Valid,
}

/** Scope-aware summary for the reviewed documents; raw statement evidence remains available. */
public enum class ProximityReaderAuthenticationSummary {
    /** No reader-authentication statement was supplied. */
    Absent,
    /** At least one supplied statement could not be parsed. */
    Malformed,
    /** At least one supplied statement failed cryptographic verification. */
    Invalid,
    /** At least one verified reader statement was revoked. */
    Revoked,
    /** Some reviewed documents lack valid authentication coverage. */
    Partial,
    /** Every document is authenticated, but trusted coverage is incomplete. */
    ValidButUntrusted,
    /** Every document is covered by trusted document or whole-request authentication. */
    Trusted,
}

/** Trust outcome after valid reader authentication. */
public enum class ProximityReaderTrustState {
    NotEvaluated,
    ValidButUntrusted,
    Revoked,
    Trusted,
}

/** Result of validating the reader certificate path against explicitly configured trust material. */
public enum class ProximityReaderCertificatePathState {
    NotEvaluated,
    UnknownAuthority,
    Invalid,
    Valid,
}

/** Reader-certificate revocation fact, kept independent from path validity and product trust. */
public enum class ProximityReaderRevocationState {
    NotChecked,
    Good,
    Revoked,
    Indeterminate,
}

/** Optional RICAL evidence fact. A match never establishes product trust by itself. */
public enum class ProximityRicalState {
    NotEvaluated,
    Unavailable,
    Invalid,
    NoMatchingAuthority,
    Matched,
}

/**
 * Exact verified reader evidence supplied to an application-owned trust policy.
 *
 * @property scope Request scope covered by the verified statement.
 */
public data class ProximityReaderEvidence(
    public val scope: ProximityReaderAuthenticationScope,
    /** Zero-based statement index within the authentication scope. */
    public val authenticationIndex: Int = 0,
    /** DER certificates in leaf-first order, encoded as unpadded Base64URL. */
    public val certificateChainDerBase64Url: List<String>,
) {
    init {
        require(authenticationIndex >= 0)
        require(certificateChainDerBase64Url.isNotEmpty()) {
            "Verified reader evidence requires a certificate chain"
        }
        require(certificateChainDerBase64Url.all(String::isNonEmptyBase64Url)) {
            "Reader certificates must be non-empty unpadded Base64URL values"
        }
    }
}

/**
 * Trust decision supplied by the hosting wallet application.
 *
 * @property state Product trust outcome.
 * @property certificatePath Result of path validation against configured trust material.
 * @property revocation Independently evaluated certificate revocation fact.
 * @property rical Optional RICAL evidence fact.
 * @property displayName Display-safe reader name established by the trust policy.
 * @property reason Display-safe explanation of the decision.
 */
public data class ProximityReaderTrustDecision(
    public val state: ProximityReaderTrustState,
    public val certificatePath: ProximityReaderCertificatePathState =
        ProximityReaderCertificatePathState.NotEvaluated,
    public val revocation: ProximityReaderRevocationState =
        ProximityReaderRevocationState.NotChecked,
    public val rical: ProximityRicalState = ProximityRicalState.NotEvaluated,
    public val displayName: String? = null,
    public val reason: String? = null,
) {
    init {
        require(displayName == null || displayName.isNotBlank())
        require(reason == null || reason.isNotBlank())
        validateReaderTrustFacts(state, certificatePath, revocation, rical)
    }
}

/** Explicit trust boundary for verified reader certificate evidence. */
public fun interface ProximityReaderTrustEvaluator {
    /** Evaluates product trust from already verified reader certificate [evidence]. */
    public suspend fun evaluate(
        evidence: ProximityReaderEvidence,
    ): ProximityReaderTrustDecision
}

/** Default policy: validity is reported, but no reader certificate becomes trusted implicitly. */
public object UnconfiguredProximityReaderTrustEvaluator :
    ProximityReaderTrustEvaluator {
    /** Returns valid-but-untrusted because no application trust policy is configured. */
    override suspend fun evaluate(
        evidence: ProximityReaderEvidence,
    ): ProximityReaderTrustDecision = ProximityReaderTrustDecision(
        state = ProximityReaderTrustState.ValidButUntrusted,
        reason = "No reader trust policy is configured",
    )
}

/**
 * Display-safe reader authentication and trust fact for holder consent.
 *
 * @property scope Request scope covered by this authentication statement.
 * @property validity Cryptographic validity, independent of product trust.
 * @property trust Product trust outcome after valid authentication.
 * @property certificatePath Certificate-path validation result.
 * @property revocation Certificate revocation fact.
 * @property rical Optional RICAL evidence fact.
 * @property displayName Display-safe authenticated reader name, when established.
 * @property reason Display-safe explanation of validity or trust.
 */
public data class ProximityReaderAuthentication(
    public val scope: ProximityReaderAuthenticationScope,
    /** Zero-based statement index within the authentication scope. */
    public val authenticationIndex: Int = 0,
    /** Only valid authentication carries evaluated trust facts. */
    public val outcome: ProximityReaderAuthenticationOutcome,
) {
    init { require(authenticationIndex >= 0) }

    /** Display validity derived from the outcome. */
    public val validity: ProximityReaderAuthenticationValidity
        get() = when (outcome) {
            ProximityReaderAuthenticationOutcome.Absent -> ProximityReaderAuthenticationValidity.Absent
            is ProximityReaderAuthenticationOutcome.Malformed -> ProximityReaderAuthenticationValidity.Malformed
            is ProximityReaderAuthenticationOutcome.Invalid -> ProximityReaderAuthenticationValidity.Invalid
            is ProximityReaderAuthenticationOutcome.Valid -> ProximityReaderAuthenticationValidity.Valid
        }

    private val decision: ProximityReaderTrustDecision?
        get() = (outcome as? ProximityReaderAuthenticationOutcome.Valid)?.trust

    /** Display trust state; authentication failure has no evaluated trust decision. */
    public val trust: ProximityReaderTrustState
        get() = decision?.state ?: ProximityReaderTrustState.NotEvaluated
    /** Independently evaluated certificate-path fact. */
    public val certificatePath: ProximityReaderCertificatePathState
        get() = decision?.certificatePath ?: ProximityReaderCertificatePathState.NotEvaluated
    /** Independently evaluated revocation fact. */
    public val revocation: ProximityReaderRevocationState
        get() = decision?.revocation ?: ProximityReaderRevocationState.NotChecked
    /** Independently evaluated RICAL fact. */
    public val rical: ProximityRicalState
        get() = decision?.rical ?: ProximityRicalState.NotEvaluated
    /** Reader name established by the trust policy. */
    public val displayName: String? get() = decision?.displayName
    /** Display-safe authentication or trust explanation. */
    public val reason: String?
        get() = when (outcome) {
            ProximityReaderAuthenticationOutcome.Absent -> null
            is ProximityReaderAuthenticationOutcome.Malformed -> outcome.reason
            is ProximityReaderAuthenticationOutcome.Invalid -> outcome.reason
            is ProximityReaderAuthenticationOutcome.Valid -> outcome.trust.reason
        }
}

/** Authentication result whose trust payload exists only after successful verification. */
public sealed interface ProximityReaderAuthenticationOutcome {
    /** No authentication statement was supplied. */
    public data object Absent : ProximityReaderAuthenticationOutcome
    /**
     * The statement could not be parsed.
     * @property reason Display-safe parsing failure.
     */
    public data class Malformed(public val reason: String) : ProximityReaderAuthenticationOutcome {
        init { require(reason.isNotBlank()) }
    }
    /**
     * Cryptographic authentication failed.
     * @property reason Display-safe verification failure.
     */
    public data class Invalid(public val reason: String) : ProximityReaderAuthenticationOutcome {
        init { require(reason.isNotBlank()) }
    }
    /**
     * Verified authentication with independent application trust facts.
     * @property trust Evaluated application trust, or the explicit not-evaluated default.
     */
    public data class Valid(public val trust: ProximityReaderTrustDecision) :
        ProximityReaderAuthenticationOutcome
}

private fun validateReaderTrustFacts(
    state: ProximityReaderTrustState,
    certificatePath: ProximityReaderCertificatePathState,
    revocation: ProximityReaderRevocationState,
    rical: ProximityRicalState,
) {
    require(state != ProximityReaderTrustState.NotEvaluated) {
        "Valid reader authentication requires an evaluated trust state"
    }
    require(state != ProximityReaderTrustState.Revoked ||
        revocation == ProximityReaderRevocationState.Revoked) {
        "A revoked trust decision requires a revoked certificate result"
    }
    require(revocation != ProximityReaderRevocationState.Revoked ||
        state == ProximityReaderTrustState.Revoked) {
        "A revoked certificate result requires a revoked trust decision"
    }
    require(state != ProximityReaderTrustState.Trusted ||
        certificatePath == ProximityReaderCertificatePathState.Valid) {
        "A trusted reader requires a valid certificate path"
    }
    require(state != ProximityReaderTrustState.Revoked ||
        certificatePath == ProximityReaderCertificatePathState.Valid) {
        "A revoked reader requires a valid certificate path"
    }
    require(certificatePath != ProximityReaderCertificatePathState.UnknownAuthority ||
        state == ProximityReaderTrustState.ValidButUntrusted) {
        "An unknown reader authority must remain valid but untrusted"
    }
    require(certificatePath != ProximityReaderCertificatePathState.Invalid ||
        state == ProximityReaderTrustState.ValidButUntrusted) {
        "An invalid reader path must remain valid but untrusted"
    }
    require(certificatePath != ProximityReaderCertificatePathState.Invalid ||
        revocation == ProximityReaderRevocationState.NotChecked) {
        "Revocation cannot be evaluated for an invalid reader path"
    }
    require(state != ProximityReaderTrustState.Trusted ||
        revocation != ProximityReaderRevocationState.Indeterminate) {
        "A reader with indeterminate revocation status cannot be trusted"
    }
    require(rical != ProximityRicalState.Matched ||
        certificatePath == ProximityReaderCertificatePathState.Valid) {
        "A matching RICAL authority requires a valid reader path"
    }
}

/** Current status of a credential at the explicit application status boundary. */
public enum class ProximityCredentialStatus {
    Valid,
    Revoked,
    Indeterminate,
}

/**
 * Metadata-only status input; raw credential values are not handed to network providers.
 *
 * @property credentialId Stable wallet-local credential identifier.
 * @property docType Credential mdoc document type.
 * @property issuer Display-safe issuer identifier when available.
 * @property validFrom Start of the locally verified MSO validity interval.
 * @property validUntil End of the locally verified MSO validity interval.
 */
public data class ProximityCredentialStatusInput(
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
public fun interface ProximityCredentialStatusEvaluator {
    /** Evaluates the current status of [credential] without receiving raw credential values. */
    public suspend fun evaluate(
        credential: ProximityCredentialStatusInput,
    ): ProximityCredentialStatus
}

/** Default status policy relies on the locally verified MSO validity interval only. */
public object UnconfiguredProximityCredentialStatusEvaluator :
    ProximityCredentialStatusEvaluator {
    /** Accepts the credential after the SDK has verified its local MSO validity interval. */
    override suspend fun evaluate(
        credential: ProximityCredentialStatusInput,
    ): ProximityCredentialStatus = ProximityCredentialStatus.Valid
}

/**
 * One candidate made available to an application-profile adapter.
 *
 * @property credentialId Stable wallet-local credential identifier.
 * @property docType Credential mdoc document type.
 * @property label Display-safe credential label when available.
 */
public data class ProximityApplicationCredential(
    public val credentialId: String,
    public val docType: String,
    public val label: String?,
) {
    init {
        require(credentialId.isNotBlank() && docType.isNotBlank())
    }
}

/**
 * Exact request and compatible candidates supplied to a versioned application profile.
 *
 * @property credentials Compatible metadata-only credential candidates.
 * @property requestedDocuments Dependency-free parsed document request facts.
 * @property readerAuthentication Display-safe authentication and trust facts for the request.
 */
public data class ProximityApplicationProfileInput(
    /** Exact DeviceRequest bytes, encoded as unpadded Base64URL. */
    public val deviceRequestBase64Url: String,
    public val credentials: List<ProximityApplicationCredential>,
    public val requestedDocuments: List<ProximityApplicationDocumentRequest>,
    public val readerAuthentication: List<ProximityReaderAuthentication>,
) {
    init {
        require(deviceRequestBase64Url.isNotBlank())
        require(credentials.distinctBy(ProximityApplicationCredential::credentialId).size == credentials.size)
        require(requestedDocuments.isNotEmpty())
        require(requestedDocuments.distinctBy { it.requestIndex }.size == requestedDocuments.size)
        val requestIndices = requestedDocuments.map { it.requestIndex }.toSet()
        require(readerAuthentication.all { authentication ->
            authentication.scope.documentRequestIndex == null || authentication.scope.documentRequestIndex in requestIndices
        }) { "Reader authentication refers to a document outside this request" }
        require(readerAuthentication.distinctBy { it.scope to it.authenticationIndex }.size ==
            readerAuthentication.size) { "Reader-authentication scopes must be unique" }
    }
}

/**
 * Dependency-free parsed request facts supplied to application-profile adapters.
 *
 * @property requestIndex Zero-based document request index.
 * @property docType Requested mdoc document type.
 * @property requestedElements Requested issuer-signed elements and retention intent.
 */
public data class ProximityApplicationDocumentRequest(
    public val requestIndex: Int,
    public val docType: String,
    public val requestedElements: List<ProximityRequestedElement>,
) {
    init {
        require(requestIndex >= 0 && docType.isNotBlank() && requestedElements.isNotEmpty())
        require(requestedElements.distinctBy { it.namespace to it.elementIdentifier }.size == requestedElements.size)
    }
}

/**
 * One locally validated, display-safe application authorization value.
 *
 * @property id Stable detail identifier within the application profile.
 * @property label Display-safe detail label.
 * @property value Display-safe detail value.
 */
public data class ProximityApplicationAuthorizationDetail(
    public val id: String,
    public val label: String,
    public val value: String,
) {
    init {
        require(id.isNotBlank() && label.isNotBlank() && value.isNotBlank())
    }
}

/**
 * Generic device-signed value proposed by a recognized application profile.
 *
 * @property credentialId Credential to which the device-signed value is bound.
 * @property namespace Device namespace containing the value.
 * @property elementIdentifier Element identifier within [namespace].
 */
public data class ProximityDeviceSignedElement(
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

/**
 * Validated output of one recognized, versioned wallet application profile.
 *
 * @property profileId Identifier of the profile that produced the authorization.
 * @property displayTitle Display-safe title for holder review.
 * @property details Display-safe authorization details.
 * @property compatibleCredentialIds Credentials for which this authorization remains valid.
 * @property deviceSignedElements Profile-proposed device-signed values, bound to compatible credentials.
 */
public data class ProximityApplicationAuthorization(
    public val profileId: String,
    public val displayTitle: String,
    public val details: List<ProximityApplicationAuthorizationDetail>,
    public val compatibleCredentialIds: Set<String>,
    public val deviceSignedElements: List<ProximityDeviceSignedElement> = emptyList(),
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
public sealed interface ProximityApplicationProfileResult {
    /** This profile does not recognize the request. */
    public data object NotRecognized : ProximityApplicationProfileResult

    /** This profile recognized the request and produced a locally validated result. */
    public data class Recognized(
        /** Validated authorization produced by the profile. */
        public val authorization: ProximityApplicationAuthorization,
    ) : ProximityApplicationProfileResult

    /**
     * This profile recognized the request but rejected invalid or unsupported application data.
     * [reason] must be safe to expose to the wallet UI.
     */
    public data class Rejected(
        /** Display-safe rejection reason. */
        public val reason: String,
    ) : ProximityApplicationProfileResult {
        init { require(reason.isNotBlank()) }
    }
}

/** Versioned wallet-owned interpreter for application-specific request data. */
public interface ProximityApplicationProfile {
    /** Stable, versioned profile identifier. */
    public val id: String

    /** Recognizes and validates application semantics in the exact [input]. */
    public suspend fun evaluate(
        input: ProximityApplicationProfileInput,
    ): ProximityApplicationProfileResult
}

/** Ordered registry requiring at most one profile to recognize a request. */
public class ProximityApplicationProfileRegistry(
    profiles: List<ProximityApplicationProfile>,
) {
    internal val profiles: List<ProximityApplicationProfile> = profiles.map { profile ->
        val capturedId = profile.id
        object : ProximityApplicationProfile {
            override val id: String = capturedId
            override suspend fun evaluate(input: ProximityApplicationProfileInput):
                ProximityApplicationProfileResult = profile.evaluate(input)
        }
    }

    init {
        require(this.profiles.none { it.id.isBlank() })
        require(this.profiles.distinctBy { it.id }.size == this.profiles.size) {
            "Application profile identifiers must be unique"
        }
    }

    /** Standard registry instances. */
    public companion object {
        /** Registry that recognizes no application-specific request semantics. */
        public val Empty: ProximityApplicationProfileRegistry =
            ProximityApplicationProfileRegistry(emptyList())
    }
}

/**
 * One requested or alternative data element shown during consent.
 *
 * @property namespace Issuer-signed namespace containing the element.
 * @property elementIdentifier Element identifier within [namespace].
 * @property intentToRetain Reader-declared retention intent.
 * @property satisfiesRequestedElements Requested elements satisfied by this disclosed alternative.
 */
public data class ProximityRequestedElement(
    public val namespace: String,
    public val elementIdentifier: String,
    public val intentToRetain: Boolean,
    public val satisfiesRequestedElements: List<ProximityElementReference> = emptyList(),
) {
    init {
        require(namespace.isNotBlank() && elementIdentifier.isNotBlank())
        require(satisfiesRequestedElements.distinct().size == satisfiesRequestedElements.size)
    }
}

/**
 * Dependency-free namespace and element identifier.
 *
 * @property namespace Namespace containing the element.
 * @property elementIdentifier Element identifier within [namespace].
 */
public data class ProximityElementReference(
    public val namespace: String,
    public val elementIdentifier: String,
) {
    init { require(namespace.isNotBlank() && elementIdentifier.isNotBlank()) }
}

/**
 * Eligible wallet credential projected without raw credential or key material.
 *
 * @property credentialId Stable wallet-local credential identifier.
 * @property label Display-safe credential label when available.
 * @property issuer Display-safe issuer identifier when available.
 * @property validUntil End of the locally verified MSO validity interval.
 * @property deviceAuthentication Holder authentication frozen for this option.
 */
public data class ProximityCredentialOption(
    public val credentialId: String,
    public val label: String?,
    public val issuer: String?,
    public val validUntil: Instant,
    public val deviceAuthentication: ProximityDeviceAuthenticationMethod,
    /** Exact requested or alternative elements this credential would disclose. */
    public val requestedElements: List<ProximityRequestedElement>,
) {
    init {
        require(credentialId.isNotBlank())
        require(requestedElements.isNotEmpty())
        require(requestedElements.distinctBy { it.namespace to it.elementIdentifier }.size == requestedElements.size)
    }
}

/**
 * One satisfiable document request in an immutable review snapshot.
 *
 * @property requestIndex Zero-based document request index.
 * @property docType Requested mdoc document type.
 * @property credentialOptions Eligible credentials and their exact disclosure choices.
 */
public data class ProximityDocumentReview(
    public val requestIndex: Int,
    public val docType: String,
    public val credentialOptions: List<ProximityCredentialOption>,
    /** Profile-defined data required if this document is shared, such as the requested mDL portrait. */
    public val requiredElements: Set<ProximityElementReference> = emptySet(),
) {
    init {
        require(requestIndex >= 0 && docType.isNotBlank())
        require(credentialOptions.isNotEmpty())
        require(credentialOptions.distinctBy { it.credentialId }.size == credentialOptions.size)
    }
}

/**
 * Reader-asserted purpose hint associated with the selected use case.
 *
 * @property type Purpose-hint type defined by the selected profile.
 * @property code Purpose-hint code defined by the selected profile.
 * @property readerAsserted Whether the value came from the reader request.
 */
public data class ProximityPurposeHint(
    public val type: String,
    public val code: Int,
    public val readerAsserted: Boolean = true,
) {
    init { require(type.isNotBlank()) }
}

/**
 * Selected edition-2 use case projected for review.
 *
 * @property index Zero-based use-case index in the request.
 * @property mandatory Whether the reader marked the use case mandatory.
 * @property documentRequestIndices Document requests governed by the use case.
 * @property purposeHints Reader-asserted purpose hints associated with the use case.
 */
public data class ProximityUseCase(
    public val index: Int,
    public val mandatory: Boolean,
    public val documentRequestIndices: List<Int>,
    public val purposeHints: List<ProximityPurposeHint>,
) {
    init {
        require(index >= 0)
        require(documentRequestIndices.isNotEmpty() && documentRequestIndices.all { it >= 0 })
        require(documentRequestIndices.distinct().size == documentRequestIndices.size)
    }
}

/**
 * Immutable holder-consent snapshot bound to one exchange and exact request.
 *
 * @property exchange One-based request exchange number within the session.
 * @property documents Satisfiable document requests and eligible credential choices.
 * @property readerAuthentication Reader authentication, certificate, revocation, RICAL, and trust facts.
 * @property useCases Edition-2 use cases selected by the request.
 * @property applicationAuthorizations Validated application-profile authorizations.
 */
public data class ProximityReview(
    /** Opaque identity unique to this review across session instances. */
    public val reviewId: ProximityReviewId,
    public val exchange: Int,
    public val documents: List<ProximityDocumentReview>,
    public val readerAuthentication: List<ProximityReaderAuthentication>,
    public val useCases: List<ProximityUseCase>,
    public val applicationAuthorizations: List<ProximityApplicationAuthorization>,
) {
    /**
     * Coverage and trust summary computed in the shared SDK for all reviewed documents.
     * Invalid, malformed and revoked statements remain visible even alongside trusted coverage.
     */
    public val readerAuthenticationSummary: ProximityReaderAuthenticationSummary
        get() {
            if (readerAuthentication.any { it.validity == ProximityReaderAuthenticationValidity.Malformed }) {
                return ProximityReaderAuthenticationSummary.Malformed
            }
            if (readerAuthentication.any { it.validity == ProximityReaderAuthenticationValidity.Invalid }) {
                return ProximityReaderAuthenticationSummary.Invalid
            }
            if (readerAuthentication.any { it.trust == ProximityReaderTrustState.Revoked }) {
                return ProximityReaderAuthenticationSummary.Revoked
            }
            val valid = readerAuthentication.filter { it.validity == ProximityReaderAuthenticationValidity.Valid }
            if (valid.isEmpty()) return ProximityReaderAuthenticationSummary.Absent
            val coverage = documents.map { document ->
                valid.filter { authentication ->
                    authentication.scope == ProximityReaderAuthenticationScope.WholeRequest ||
                        authentication.scope.documentRequestIndex == document.requestIndex
                }.map { it.trust }.let { trust ->
                    when {
                        ProximityReaderTrustState.Trusted in trust -> ProximityReaderAuthenticationSummary.Trusted
                        ProximityReaderTrustState.ValidButUntrusted in trust -> ProximityReaderAuthenticationSummary.ValidButUntrusted
                        else -> ProximityReaderAuthenticationSummary.Partial
                    }
                }
            }
            return when {
                ProximityReaderAuthenticationSummary.Partial in coverage -> ProximityReaderAuthenticationSummary.Partial
                ProximityReaderAuthenticationSummary.ValidButUntrusted in coverage -> ProximityReaderAuthenticationSummary.ValidButUntrusted
                else -> ProximityReaderAuthenticationSummary.Trusted
            }
        }

    init {
        require(exchange > 0 && documents.isNotEmpty())
        require(documents.distinctBy { it.requestIndex }.size == documents.size)
        val requestIndices = documents.map { it.requestIndex }.toSet()
        require(readerAuthentication.all { authentication ->
            authentication.scope.documentRequestIndex == null || authentication.scope.documentRequestIndex in requestIndices
        }) { "Reader authentication refers to a document outside this review" }
        require(readerAuthentication.distinctBy {
            it.scope to it.authenticationIndex
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

/**
 * Credential and disclosure choice for exactly one reviewed document request.
 *
 * @property requestIndex Reviewed document request being answered.
 * @property credentialId Reviewed credential selected for the response.
 * @property disclosedElements Non-empty subset of reviewed elements approved for disclosure.
 */
public data class ProximityDocumentSubmission(
    public val requestIndex: Int,
    public val credentialId: String,
    public val disclosedElements: Set<ProximityElementReference>,
) {
    init {
        require(requestIndex >= 0 && credentialId.isNotBlank())
        require(disclosedElements.isNotEmpty()) { "A document submission must disclose at least one reviewed element" }
    }
}

/**
 * Complete holder choice for the current review; it is rebound and revalidated before response generation.
 *
 * @property documents One credential and disclosure choice per answered document request.
 * @property continueAfterResponse Whether to remain connected for another request after a successful response.
 */
public data class ProximitySubmission(
    public val documents: List<ProximityDocumentSubmission>,
    public val continueAfterResponse: Boolean = false,
) {
    init {
        require(documents.isNotEmpty())
        require(documents.distinctBy { it.requestIndex }.size == documents.size)
    }
}

/** Opaque identity issued by the wallet for one consent review. */
public data class ProximityReviewId(
    /** Stable representation used by the Swift bridge and host state restoration. */
    public val value: String,
) {
    init { Uuid.parse(value) }
}

/** User or host action accepted by a proximity session. */
public sealed interface ProximityAction {
    /** Approves the current immutable review with the exact [submission]. */
    public data class Approve(
        /** Identity of the review the holder approved. */
        public val reviewId: ProximityReviewId,
        /** Holder-approved credential and disclosure choices. */
        public val submission: ProximitySubmission,
    ) :
        ProximityAction

    /** Declines the current review and terminates the session without disclosure. */
    public data class Decline(
        /** Identity of the review the holder declined. */
        public val reviewId: ProximityReviewId,
    ) : ProximityAction

    /** Cancels the active session and releases its resources. */
    public data object Cancel : ProximityAction

    /** Rechecks side-effect-free prerequisites after host remediation. */
    public data object RetryPrerequisites : ProximityAction

    /** Reports the privacy-safe outcome of a requested host remediation. */
    public data class ReportRemediation(
        /** Remediation action whose outcome is being reported. */
        public val action: ProximityRemediationAction,
        /** Privacy-safe outcome reported by the host. */
        public val result: ProximityHostActionResult,
    ) : ProximityAction
}

/** Privacy-safe outcome of a system surface performed by the host application. */
public enum class ProximityHostActionResult {
    Completed,
    Cancelled,
    Failed,
}

/** Prepared engagement presented by the host UI. */
public sealed interface ProximityEngagement {
    /** ISO mdoc device-engagement URI for QR rendering. */
    public data class Qr(
        /** Complete `mdoc:` URI that the host must encode without transformation. */
        public val payload: String,
    ) : ProximityEngagement {
        init { require(payload.startsWith("mdoc:")) }
    }

    /** NFC engagement prepared by the platform transport. */
    public data object Nfc : ProximityEngagement
}

/**
 * One protected-key operation required by a frozen approved document response.
 *
 * @property requestIndex Reviewed document request requiring authorization.
 * @property credentialId Credential whose protected holder key will be used.
 * @property deviceAuthentication Frozen signature or MAC operation.
 */
public data class ProximityHolderAuthorizationRequest(
    public val requestIndex: Int,
    public val credentialId: String,
    public val deviceAuthentication: ProximityDeviceAuthenticationMethod,
) {
    init {
        require(requestIndex >= 0 && credentialId.isNotBlank())
    }
}

/**
 * Exact holder-key authorization context for a frozen approved submission.
 *
 * @property exchange Request exchange whose response is frozen.
 * @property requests Protected-key operations required by the approved response.
 */
public data class ProximityHolderAuthorization(
    /** Identity of the consumed review whose accepted choices require key authorization. */
    public val reviewId: ProximityReviewId,
    public val exchange: Int,
    public val requests: List<ProximityHolderAuthorizationRequest>,
) {
    init {
        require(exchange > 0 && requests.isNotEmpty())
        require(requests.distinctBy { it.requestIndex }.size == requests.size)
    }
}

/** Deterministic result of dispatching an action. */
public sealed interface ProximityActionResult {
    /** The action was legal for the current state and was accepted exactly once. */
    public data object Accepted : ProximityActionResult

    /** The action was illegal, stale, or invalid and had no side effects. */
    public data class Rejected(
        /** Stable reason the action was rejected. */
        public val error: ProximityError,
    ) :
        ProximityActionResult
}

/** Public Wallet SDK session state; each variant carries only data valid for that phase. */
public sealed interface ProximityState {
    /** Prerequisites are being evaluated or require host remediation. */
    public data class CheckingPrerequisites(
        /** Latest side-effect-free prerequisite snapshot. */
        public val capabilities: ProximityCapabilities,
    ) : ProximityState

    /** Session material and selected transports are being prepared. */
    public data class Preparing(
        /** Profile frozen for this single-use session. */
        public val profile: ProximityProfile,
    ) : ProximityState

    /** Engagement data is ready for presentation to the reader. */
    public data class EngagementReady(
        /** Prepared engagement methods the host may present. */
        public val engagements: List<ProximityEngagement>,
    ) : ProximityState {
        init { require(engagements.isNotEmpty() && engagements.distinctBy { it::class }.size == engagements.size) }
    }

    /** The reader has consumed engagement data and transport connection is in progress. */
    public data class Connecting(
        /** Engagement methods that initiated the connection attempt. */
        public val engagements: List<ProximityEngagement>,
    ) : ProximityState {
        init { require(engagements.isNotEmpty() && engagements.distinctBy { it::class }.size == engagements.size) }
    }

    /** Transport is connected and awaiting the next device request. */
    public data class AwaitingRequest(
        /** One-based exchange number expected next. */
        public val exchange: Int,
    ) : ProximityState {
        init { require(exchange > 0) }
    }

    /** An immutable request snapshot requires explicit holder consent. */
    public data class ReviewRequired(
        /** Exact review snapshot to render and approve or decline. */
        public val review: ProximityReview,
        /** Explains why an earlier prepared approval could not be used. */
        public val reason: ProximityReviewReason = ProximityReviewReason.RequestReceived,
    ) :
        ProximityState

    /** Connection ended without disclosure. Review this verified request before explicitly preparing a new connection. */
    public data class PreparationRequired(
        /** Verified request to review and explicitly approve before reconnecting. */
        public val plan: ProximitySharingPlan,
        /** Whether this is the initial request or a change from the earlier prepared approval. */
        public val reason: ProximityReviewReason = ProximityReviewReason.RequestReceived,
    ) : ProximityState

    /** The approved response is awaiting protected holder-key authorization. */
    public data class AuthorizingHolderKey(
        /** Exact protected-key operations frozen by holder consent. */
        public val authorization: ProximityHolderAuthorization,
    ) : ProximityState

    /** The approved and authorized response is being sent. */
    public data class SendingResponse(
        /** One-based exchange number being answered. */
        public val exchange: Int,
    ) : ProximityState {
        init { require(exchange > 0) }
    }

    /** One response completed and the session remains open for another request. */
    public data class AwaitingNextRequest(
        /** Number of exchanges completed successfully. */
        public val completedExchanges: Int,
    ) : ProximityState {
        init { require(completedExchanges > 0) }
    }

    /** Session termination is being sent after the final exchange. */
    public data class Terminating(
        /** One-based exchange number after which termination occurs. */
        public val exchange: Int,
    ) : ProximityState {
        init { require(exchange > 0) }
    }

    /**
     * The session ended without sharing data for its final request.
     * Earlier exchanges may already have sent holder-approved data.
     */
    public data class NoData(
        /** One-based final request exchange number. */
        public val exchange: Int,
    ) : ProximityState {
        init { require(exchange > 0) }
    }

    /** Session reached a normal terminal state after disclosure or holder decline. */
    public data class Completed(
        /** Number of requests handled before termination. */
        public val exchanges: Int,
        /** Whether the holder declined the final reviewed request. */
        public val declined: Boolean,
        /** Last locally completed response; does not assert that the reader verified or accepted it. */
        public val receipt: ProximitySharingReceipt? = null,
    ) :
        ProximityState {
        init { require(exchanges > 0) }
    }

    /** Session was cancelled locally and all owned resources were released. */
    public data object Cancelled : ProximityState

    /** Session terminated because of a stable Wallet SDK failure. */
    public data class Failed(
        /** Display-safe terminal failure. */
        public val error: ProximityError,
    ) : ProximityState
}

/** Legal host actions derived exclusively from the current session state. */
public val ProximityState.legalActions: Set<ProximityActionType>
    get() = when (this) {
        is ProximityState.CheckingPrerequisites -> setOf(
            ProximityActionType.RetryPrerequisites,
            ProximityActionType.ReportRemediation,
            ProximityActionType.Cancel,
        )
        is ProximityState.ReviewRequired -> setOf(
            ProximityActionType.Approve,
            ProximityActionType.Decline,
            ProximityActionType.Cancel,
        )
        is ProximityState.Preparing,
        is ProximityState.EngagementReady,
        is ProximityState.Connecting,
        is ProximityState.AwaitingRequest,
        is ProximityState.AuthorizingHolderKey,
        is ProximityState.SendingResponse,
        is ProximityState.AwaitingNextRequest -> setOf(ProximityActionType.Cancel)
        is ProximityState.Terminating,
        is ProximityState.PreparationRequired,
        is ProximityState.Completed,
        is ProximityState.NoData,
        ProximityState.Cancelled,
        is ProximityState.Failed -> emptySet()
    }

/** Action kinds used for state-derived UI affordances without constructing an action payload. */
public enum class ProximityActionType {
    Approve,
    Decline,
    Cancel,
    RetryPrerequisites,
    ReportRemediation,
}

/** Engagement that actually won the reader connection. */
public enum class ProximityEngagementMethod { Qr, Nfc }

/** Bearer actually carrying the connected session. */
public enum class ProximityTransport { BluetoothLowEnergy, Nfc, WifiAware }

/**
 * Actual connected route, independent of the methods configured or advertised.
 * @property engagement Engagement that won the reader connection.
 * @property transport Bearer carrying the connected session.
 */
public data class ProximityConnectedRoute(
    public val engagement: ProximityEngagementMethod,
    public val transport: ProximityTransport,
)

/** Single-use, wallet-owned proximity presentation session. */
public interface ProximitySession {
    /** Winning route once connected; remains available through review and termination. */
    public val connectedRoute: ProximityConnectedRoute? get() = null

    /** Most recent request eligible for explicit preparation. Never authorizes disclosure by itself. */
    public val sharingPlan: ProximitySharingPlan? get() = null

    /** Hot state stream whose variants define the only legal phase data and actions. */
    public val state: StateFlow<ProximityState>

    /** Dispatches one state-bound action. Illegal or stale actions are rejected without side effects. */
    public suspend fun dispatch(action: ProximityAction): ProximityActionResult

    /** Idempotently cancels an active session and releases all session-owned resources. */
    public suspend fun close()
}
