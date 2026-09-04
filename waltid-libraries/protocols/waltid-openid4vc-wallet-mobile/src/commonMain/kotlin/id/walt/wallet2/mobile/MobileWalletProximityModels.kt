package id.walt.wallet2.mobile

import kotlinx.coroutines.flow.StateFlow
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Instant

/**
 * Versioned mdoc interoperability boundary selected for one proximity session.
 *
 * @property id Stable identifier suitable for configuration and diagnostics.
 */
public enum class MobileWalletProximityProfile(public val id: String) {
    /** ISO/IEC 18013-5:2021 compatibility boundary. */
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

/**
 * Complete BLE bearer configuration; it cannot exist unless BLE retrieval is selected.
 *
 * @property roles Holder GATT roles prepared for the session.
 * @property bearerPolicy GATT/L2CAP selection policy applied within the selected roles.
 */
public data class MobileWalletProximityBleConfiguration(
    public val roles: MobileWalletProximityBleRoles = MobileWalletProximityBleRoles.Dual,
    public val bearerPolicy: MobileWalletProximityBleBearerPolicy =
        MobileWalletProximityBleBearerPolicy.PreferL2cap,
)

/**
 * Complete conventional NFC retrieval length contract.
 *
 * @property maximumCommandDataLength Maximum command-data bytes accepted by the holder.
 * @property maximumResponseDataLength Maximum response-data bytes returned by the holder.
 */
public data class MobileWalletProximityNfcRetrievalConfiguration(
    public val maximumCommandDataLength: Int = 65_535,
    public val maximumResponseDataLength: Int = 65_536,
) {
    init {
        require(maximumCommandDataLength in 255..65_535)
        require(maximumResponseDataLength in 256..65_536)
    }
}

/** Wi-Fi Aware NAN data-path security selected for one proximity session. */
public enum class MobileWalletProximityWifiAwareSecurityPolicy {
    /** Mandatory ISO holder baseline using NAN Cipher Suite NCS-SK-128. */
    NcsSk128,
}

/**
 * Complete Wi-Fi Aware retrieval configuration.
 *
 * @property securityPolicy NAN data-path security required for the prepared transport.
 */
public data class MobileWalletProximityWifiAwareConfiguration(
    public val securityPolicy: MobileWalletProximityWifiAwareSecurityPolicy =
        MobileWalletProximityWifiAwareSecurityPolicy.NcsSk128,
)

/**
 * Retrieval configuration whose variant is tied to the selected NFC engagement family.
 *
 * Conventional retrieval methods and provisional NFCv2 same-channel retrieval are intentionally
 * different variants: NFCv2 must never be represented as conventional NFC merely because both use
 * ISO 7816 APDUs.
 */
public sealed interface MobileWalletProximityRetrievalConfiguration {
    /**
     * One or more conventional retrieval methods used by QR, Static Handover, or Negotiated Handover.
     *
     * @property bluetoothLowEnergy Optional BLE role and bearer policy.
     * @property nfc Optional conventional NFC command/response contract.
     * @property wifiAware Optional Wi-Fi Aware holder-publisher contract.
     */
    public data class Conventional(
        public val bluetoothLowEnergy: MobileWalletProximityBleConfiguration? =
            MobileWalletProximityBleConfiguration(),
        public val nfc: MobileWalletProximityNfcRetrievalConfiguration? = null,
        public val wifiAware: MobileWalletProximityWifiAwareConfiguration? = null,
    ) : MobileWalletProximityRetrievalConfiguration {
        init {
            require(bluetoothLowEnergy != null || nfc != null || wifiAware != null) {
                "Conventional proximity retrieval requires BLE, NFC, Wi-Fi Aware, or a combination"
            }
        }
    }

    /**
     * Provisional NFCv2 same-channel retrieval plus compatible optional paths.
     *
     * The NFCv2 APDU channel is always selected by this variant. BLE, when present, is an alternate
     * bearer used as the NFCv2 hybrid transport. [qrNfc] is conventional NFC retrieval for the QR
     * branch of a combined QR/NFCv2 engagement only; it is never encoded as an NFCv2 method.
     *
     * @property bluetoothLowEnergy Optional NFCv2 alternate BLE bearer and QR BLE bearer.
     * @property qrNfc Optional conventional NFC retrieval for a concurrently prepared QR path.
     * @property wifiAware Optional NFCv2 alternate and QR Wi-Fi Aware bearer.
     */
    public data class ProvisionalNfcV2(
        public val bluetoothLowEnergy: MobileWalletProximityBleConfiguration? = null,
        public val qrNfc: MobileWalletProximityNfcRetrievalConfiguration? = null,
        public val wifiAware: MobileWalletProximityWifiAwareConfiguration? = null,
    ) : MobileWalletProximityRetrievalConfiguration
}

/** NFC engagement wire profile selected exactly once for a session. */
public sealed interface MobileWalletProximityNfcEngagementMode {
    /** NFC Forum Static Handover with holder-selected retrieval methods. */
    public data object Static : MobileWalletProximityNfcEngagementMode

    /** NFC Forum Negotiated Handover with a reader-selected retrieval method. */
    public data object Negotiated : MobileWalletProximityNfcEngagementMode

    /**
     * Provisional second-edition NFC Engagement v2 behavior pinned to the selected source contract.
     *
     * @property maximumCommandDataLength Maximum command-data bytes accepted by the NFCv2 application.
     */
    public data class ProvisionalV2(public val maximumCommandDataLength: Int = 65_536) :
        MobileWalletProximityNfcEngagementMode {
        init {
            require(maximumCommandDataLength in 1..65_536)
        }
    }
}

/** Engagement configuration in which QR/NFC combinations and NFC tuning cannot drift apart. */
public sealed interface MobileWalletProximityEngagementConfiguration {
    /** QR is the only configured engagement path. */
    public data object QrOnly : MobileWalletProximityEngagementConfiguration

    /**
     * NFC is the only configured engagement path.
     *
     * @property mode NFC handover mode exposed for the session.
     */
    public data class NfcOnly(public val mode: MobileWalletProximityNfcEngagementMode) :
        MobileWalletProximityEngagementConfiguration

    /**
     * QR and NFC are prepared as competing engagement paths.
     *
     * @property mode NFC handover mode exposed for the NFC path.
     */
    public data class QrAndNfc(public val mode: MobileWalletProximityNfcEngagementMode) :
        MobileWalletProximityEngagementConfiguration
}

internal val MobileWalletProximityEngagementConfiguration.includesQr: Boolean
    get() = this is MobileWalletProximityEngagementConfiguration.QrOnly ||
        this is MobileWalletProximityEngagementConfiguration.QrAndNfc

internal val MobileWalletProximityEngagementConfiguration.includesNfc: Boolean
    get() = this is MobileWalletProximityEngagementConfiguration.NfcOnly ||
        this is MobileWalletProximityEngagementConfiguration.QrAndNfc

internal val MobileWalletProximityEngagementConfiguration.includesNfcV2: Boolean
    get() = when (this) {
        MobileWalletProximityEngagementConfiguration.QrOnly -> false
        is MobileWalletProximityEngagementConfiguration.NfcOnly ->
            mode is MobileWalletProximityNfcEngagementMode.ProvisionalV2
        is MobileWalletProximityEngagementConfiguration.QrAndNfc ->
            mode is MobileWalletProximityNfcEngagementMode.ProvisionalV2
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

/**
 * Immutable configuration for one single-use proximity session.
 *
 * @property profile Protocol and application-profile boundary to enforce.
 * @property engagement Holder-to-reader engagement configuration selected for the session.
 * @property retrieval Nonempty typed device-retrieval configuration.
 * @property readerPolicy Trust threshold applied before disclosure review.
 * @property deviceAuthenticationPolicy Allowed and preferred holder-authentication methods.
 * @property readerTrustEvaluator Application-owned reader trust boundary.
 * @property credentialStatusEvaluator Application-owned credential status boundary.
 * @property applicationProfiles Ordered application-profile registry for request extensions.
 * @property maximumMessageBytes Maximum accepted encoded proximity message size.
 */
public data class MobileWalletProximityConfiguration(
    public val profile: MobileWalletProximityProfile =
        MobileWalletProximityProfile.Iso180135Edition2Dis2026,
    public val engagement: MobileWalletProximityEngagementConfiguration =
        MobileWalletProximityEngagementConfiguration.QrOnly,
    public val retrieval: MobileWalletProximityRetrievalConfiguration =
        MobileWalletProximityRetrievalConfiguration.Conventional(),
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
        require(profile != MobileWalletProximityProfile.Iso1801352021 || !engagement.includesNfcV2) {
            "NFC Engagement v2 is not part of the ISO/IEC 18013-5:2021 compatibility profile"
        }
        val provisionalNfcV2Retrieval = retrieval as? MobileWalletProximityRetrievalConfiguration.ProvisionalNfcV2
        require(engagement.includesNfcV2 == (provisionalNfcV2Retrieval != null)) {
            "NFCv2 engagement and its distinct retrieval configuration must be selected together"
        }
        if (provisionalNfcV2Retrieval != null) {
            if (engagement.includesQr) {
                require(
                    provisionalNfcV2Retrieval.bluetoothLowEnergy != null ||
                        provisionalNfcV2Retrieval.qrNfc != null
                ) { "A combined QR/NFCv2 session requires a QR-compatible retrieval method" }
            } else {
                require(provisionalNfcV2Retrieval.qrNfc == null) {
                    "QR-only conventional NFC retrieval cannot be configured without a QR engagement path"
                }
            }
        }
    }
}

/**
 * Stable, non-sensitive failure exposed by the Wallet SDK.
 *
 * @property category Layer-stable failure category.
 * @property code Stable machine-readable error code.
 * @property message Display-safe diagnostic message.
 * @property recoverable Whether the host may offer a retry without replacing the session.
 */
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
 * @property runtimeAvailable Whether the current platform state can use the method now.
 * @property selected Whether the session configuration selected the method.
 * @property unavailable Stable reason for runtime unavailability, when applicable.
 * @property remediationActions Host actions that may make the method available.
 */
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
public data class MobileWalletProximityCapabilities(
    public val profile: MobileWalletProximityProfile,
    public val qrEngagement: MobileWalletProximityTransportCapability,
    public val nfcEngagement: MobileWalletProximityTransportCapability,
    public val bluetoothLowEnergy: MobileWalletProximityTransportCapability,
    public val nfcRetrieval: MobileWalletProximityTransportCapability,
    public val nfcV2Retrieval: MobileWalletProximityTransportCapability,
    public val wifiAwareRetrieval: MobileWalletProximityTransportCapability,
) {
    init {
        require(qrEngagement.selected || nfcEngagement.selected) {
            "At least one engagement capability must be selected"
        }
        require(
            bluetoothLowEnergy.selected || nfcRetrieval.selected ||
                nfcV2Retrieval.selected || wifiAwareRetrieval.selected
        ) {
            "At least one retrieval capability must be selected"
        }
        require(!nfcV2Retrieval.selected || nfcEngagement.selected) {
            "NFCv2 same-channel retrieval requires the NFCv2 engagement path"
        }
        require(!nfcV2Retrieval.mayStart || nfcEngagement.mayStart) {
            "NFCv2 same-channel retrieval cannot start without NFC engagement"
        }
    }

    /** Whether at least one selected engagement has a compatible retrieval path that can start. */
    public val mayStart: Boolean
        get() {
            val qrPath = qrEngagement.mayStart &&
                listOf(bluetoothLowEnergy, nfcRetrieval, wifiAwareRetrieval).any { it.mayStart }
            val nfcPath = nfcEngagement.mayStart &&
                listOf(bluetoothLowEnergy, nfcRetrieval, nfcV2Retrieval, wifiAwareRetrieval)
                    .any { it.mayStart }
            return qrPath || nfcPath
        }

    /** Distinct host remediations for selected unavailable methods. */
    public val remediationActions: List<MobileWalletProximityRemediationAction>
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
    UnknownAuthority,
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

/**
 * Exact verified reader evidence supplied to an application-owned trust policy.
 *
 * @property scope Request scope covered by the verified statement.
 * @property documentRequestIndex Zero-based document request index for document-scoped evidence.
 */
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
        require(displayName == null || displayName.isNotBlank())
        require(reason == null || reason.isNotBlank())
        validateReaderTrustFacts(state, certificatePath, revocation, rical)
    }
}

/** Explicit trust boundary for verified reader certificate evidence. */
public fun interface MobileWalletProximityReaderTrustEvaluator {
    /** Evaluates product trust from already verified reader certificate [evidence]. */
    public suspend fun evaluate(
        evidence: MobileWalletProximityReaderEvidence,
    ): MobileWalletProximityReaderTrustDecision
}

/** Default policy: validity is reported, but no reader certificate becomes trusted implicitly. */
public object UnconfiguredMobileWalletProximityReaderTrustEvaluator :
    MobileWalletProximityReaderTrustEvaluator {
    /** Returns valid-but-untrusted because no application trust policy is configured. */
    override suspend fun evaluate(
        evidence: MobileWalletProximityReaderEvidence,
    ): MobileWalletProximityReaderTrustDecision = MobileWalletProximityReaderTrustDecision(
        state = MobileWalletProximityReaderTrustState.ValidButUntrusted,
        reason = "No reader trust policy is configured",
    )
}

/**
 * Display-safe reader authentication and trust fact for holder consent.
 *
 * @property scope Request scope covered by this authentication statement.
 * @property documentRequestIndex Zero-based document request index for document-scoped authentication.
 * @property validity Cryptographic validity, independent of product trust.
 * @property trust Product trust outcome after valid authentication.
 * @property certificatePath Certificate-path validation result.
 * @property revocation Certificate revocation fact.
 * @property rical Optional RICAL evidence fact.
 * @property displayName Display-safe authenticated reader name, when established.
 * @property reason Display-safe explanation of validity or trust.
 */
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
        require(displayName == null || displayName.isNotBlank())
        require(reason == null || reason.isNotBlank())
        if (validity == MobileWalletProximityReaderAuthenticationValidity.Valid) {
            validateReaderTrustFacts(trust, certificatePath, revocation, rical)
        }
    }
}

private fun validateReaderTrustFacts(
    state: MobileWalletProximityReaderTrustState,
    certificatePath: MobileWalletProximityReaderCertificatePathState,
    revocation: MobileWalletProximityReaderRevocationState,
    rical: MobileWalletProximityRicalState,
) {
    require(state != MobileWalletProximityReaderTrustState.NotEvaluated) {
        "Valid reader authentication requires an evaluated trust state"
    }
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
    require(state != MobileWalletProximityReaderTrustState.Revoked ||
        certificatePath == MobileWalletProximityReaderCertificatePathState.Valid) {
        "A revoked reader requires a valid certificate path"
    }
    require(certificatePath != MobileWalletProximityReaderCertificatePathState.UnknownAuthority ||
        state == MobileWalletProximityReaderTrustState.ValidButUntrusted) {
        "An unknown reader authority must remain valid but untrusted"
    }
    require(certificatePath != MobileWalletProximityReaderCertificatePathState.Invalid ||
        state == MobileWalletProximityReaderTrustState.ValidButUntrusted) {
        "An invalid reader path must remain valid but untrusted"
    }
    require(certificatePath != MobileWalletProximityReaderCertificatePathState.Invalid ||
        revocation == MobileWalletProximityReaderRevocationState.NotChecked) {
        "Revocation cannot be evaluated for an invalid reader path"
    }
    require(state != MobileWalletProximityReaderTrustState.Trusted ||
        revocation != MobileWalletProximityReaderRevocationState.Indeterminate) {
        "A reader with indeterminate revocation status cannot be trusted"
    }
    require(rical != MobileWalletProximityRicalState.Matched ||
        certificatePath == MobileWalletProximityReaderCertificatePathState.Valid) {
        "A matching RICAL authority requires a valid reader path"
    }
}

/** Current status of a credential at the explicit application status boundary. */
public enum class MobileWalletProximityCredentialStatus {
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
    /** Evaluates the current status of [credential] without receiving raw credential values. */
    public suspend fun evaluate(
        credential: MobileWalletProximityCredentialStatusInput,
    ): MobileWalletProximityCredentialStatus
}

/** Default status policy relies on the locally verified MSO validity interval only. */
public object UnconfiguredMobileWalletProximityCredentialStatusEvaluator :
    MobileWalletProximityCredentialStatusEvaluator {
    /** Accepts the credential after the SDK has verified its local MSO validity interval. */
    override suspend fun evaluate(
        credential: MobileWalletProximityCredentialStatusInput,
    ): MobileWalletProximityCredentialStatus = MobileWalletProximityCredentialStatus.Valid
}

/**
 * One candidate made available to an application-profile adapter.
 *
 * @property credentialId Stable wallet-local credential identifier.
 * @property docType Credential mdoc document type.
 * @property label Display-safe credential label when available.
 */
public data class MobileWalletProximityApplicationCredential(
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

/**
 * Dependency-free parsed request facts supplied to application-profile adapters.
 *
 * @property requestIndex Zero-based document request index.
 * @property docType Requested mdoc document type.
 * @property requestedElements Requested issuer-signed elements and retention intent.
 */
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

/**
 * One locally validated, display-safe application authorization value.
 *
 * @property id Stable detail identifier within the application profile.
 * @property label Display-safe detail label.
 * @property value Display-safe detail value.
 */
public data class MobileWalletProximityApplicationAuthorizationDetail(
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

/**
 * Validated output of one recognized, versioned wallet application profile.
 *
 * @property profileId Identifier of the profile that produced the authorization.
 * @property displayTitle Display-safe title for holder review.
 * @property details Display-safe authorization details.
 * @property compatibleCredentialIds Credentials for which this authorization remains valid.
 * @property deviceSignedElements Profile-proposed device-signed values, bound to compatible credentials.
 */
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
        /** Validated authorization produced by the profile. */
        public val authorization: MobileWalletProximityApplicationAuthorization,
    ) : MobileWalletProximityApplicationProfileResult

    /**
     * This profile recognized the request but rejected invalid or unsupported application data.
     * [reason] must be safe to expose to the wallet UI.
     */
    public data class Rejected(
        /** Display-safe rejection reason. */
        public val reason: String,
    ) : MobileWalletProximityApplicationProfileResult {
        init { require(reason.isNotBlank()) }
    }
}

/** Versioned wallet-owned interpreter for application-specific request data. */
public interface MobileWalletProximityApplicationProfile {
    /** Stable, versioned profile identifier. */
    public val id: String

    /** Recognizes and validates application semantics in the exact [input]. */
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

    /** Standard registry instances. */
    public companion object {
        /** Registry that recognizes no application-specific request semantics. */
        public val Empty: MobileWalletProximityApplicationProfileRegistry =
            MobileWalletProximityApplicationProfileRegistry(emptyList())
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

/**
 * Dependency-free namespace and element identifier.
 *
 * @property namespace Namespace containing the element.
 * @property elementIdentifier Element identifier within [namespace].
 */
public data class MobileWalletProximityElementReference(
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

/**
 * One satisfiable document request in an immutable review snapshot.
 *
 * @property requestIndex Zero-based document request index.
 * @property docType Requested mdoc document type.
 * @property credentialOptions Eligible credentials and their exact disclosure choices.
 */
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

/**
 * Reader-asserted purpose hint associated with the selected use case.
 *
 * @property type Purpose-hint type defined by the selected profile.
 * @property code Purpose-hint code defined by the selected profile.
 * @property readerAsserted Whether the value came from the reader request.
 */
public data class MobileWalletProximityPurposeHint(
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

/**
 * Immutable holder-consent snapshot bound to one exchange and exact request.
 *
 * @property exchange One-based request exchange number within the session.
 * @property documents Satisfiable document requests and eligible credential choices.
 * @property readerAuthentication Reader authentication, certificate, revocation, RICAL, and trust facts.
 * @property useCases Edition-2 use cases selected by the request.
 * @property applicationAuthorizations Validated application-profile authorizations.
 */
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

/**
 * Credential and disclosure choice for exactly one reviewed document request.
 *
 * @property requestIndex Reviewed document request being answered.
 * @property credentialId Reviewed credential selected for the response.
 * @property disclosedElements Non-empty subset of reviewed elements approved for disclosure.
 */
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

/**
 * Complete holder choice for the current review; it is rebound and revalidated before response generation.
 *
 * @property documents One credential and disclosure choice per answered document request.
 * @property continueAfterResponse Whether to remain connected for another request after a successful response.
 */
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
    /** Approves the current immutable review with the exact [submission]. */
    public data class Approve(
        /** Holder-approved credential and disclosure choices. */
        public val submission: MobileWalletProximitySubmission,
    ) :
        MobileWalletProximityAction

    /** Declines the current review and terminates the session without disclosure. */
    public data object Decline : MobileWalletProximityAction

    /** Cancels the active session and releases its resources. */
    public data object Cancel : MobileWalletProximityAction

    /** Rechecks side-effect-free prerequisites after host remediation. */
    public data object RetryPrerequisites : MobileWalletProximityAction

    /** Reports the privacy-safe outcome of a requested host remediation. */
    public data class ReportRemediation(
        /** Remediation action whose outcome is being reported. */
        public val action: MobileWalletProximityRemediationAction,
        /** Privacy-safe outcome reported by the host. */
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
    /** ISO mdoc device-engagement URI for QR rendering. */
    public data class Qr(
        /** Complete `mdoc:` URI that the host must encode without transformation. */
        public val payload: String,
    ) : MobileWalletProximityEngagement {
        init { require(payload.startsWith("mdoc:")) }
    }

    /** NFC engagement prepared by the platform transport. */
    public data object Nfc : MobileWalletProximityEngagement
}

/**
 * One protected-key operation required by a frozen approved document response.
 *
 * @property requestIndex Reviewed document request requiring authorization.
 * @property credentialId Credential whose protected holder key will be used.
 * @property deviceAuthentication Frozen signature or MAC operation.
 */
public data class MobileWalletProximityHolderAuthorizationRequest(
    public val requestIndex: Int,
    public val credentialId: String,
    public val deviceAuthentication: MobileWalletProximityDeviceAuthenticationMethod,
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
    /** The action was legal for the current state and was accepted exactly once. */
    public data object Accepted : MobileWalletProximityActionResult

    /** The action was illegal, stale, or invalid and had no side effects. */
    public data class Rejected(
        /** Stable reason the action was rejected. */
        public val error: MobileWalletProximityError,
    ) :
        MobileWalletProximityActionResult
}

/** Public Wallet SDK session state; each variant carries only data valid for that phase. */
public sealed interface MobileWalletProximityState {
    /** Prerequisites are being evaluated or require host remediation. */
    public data class CheckingPrerequisites(
        /** Latest side-effect-free prerequisite snapshot. */
        public val capabilities: MobileWalletProximityCapabilities,
    ) : MobileWalletProximityState

    /** Session material and selected transports are being prepared. */
    public data class Preparing(
        /** Profile frozen for this single-use session. */
        public val profile: MobileWalletProximityProfile,
    ) : MobileWalletProximityState

    /** Engagement data is ready for presentation to the reader. */
    public data class EngagementReady(
        /** Prepared engagement methods the host may present. */
        public val engagements: List<MobileWalletProximityEngagement>,
    ) : MobileWalletProximityState {
        init { require(engagements.isNotEmpty() && engagements.distinctBy { it::class }.size == engagements.size) }
    }

    /** The reader has consumed engagement data and transport connection is in progress. */
    public data class Connecting(
        /** Engagement methods that initiated the connection attempt. */
        public val engagements: List<MobileWalletProximityEngagement>,
    ) : MobileWalletProximityState {
        init { require(engagements.isNotEmpty() && engagements.distinctBy { it::class }.size == engagements.size) }
    }

    /** Transport is connected and awaiting the next device request. */
    public data class AwaitingRequest(
        /** One-based exchange number expected next. */
        public val exchange: Int,
    ) : MobileWalletProximityState {
        init { require(exchange > 0) }
    }

    /** An immutable request snapshot requires explicit holder consent. */
    public data class ReviewRequired(
        /** Exact review snapshot to render and approve or decline. */
        public val review: MobileWalletProximityReview,
    ) :
        MobileWalletProximityState

    /** The approved response is awaiting protected holder-key authorization. */
    public data class AuthorizingHolderKey(
        /** Exact protected-key operations frozen by holder consent. */
        public val authorization: MobileWalletProximityHolderAuthorization,
    ) : MobileWalletProximityState

    /** The approved and authorized response is being sent. */
    public data class SendingResponse(
        /** One-based exchange number being answered. */
        public val exchange: Int,
    ) : MobileWalletProximityState {
        init { require(exchange > 0) }
    }

    /** One response completed and the session remains open for another request. */
    public data class AwaitingNextRequest(
        /** Number of exchanges completed successfully. */
        public val completedExchanges: Int,
    ) : MobileWalletProximityState {
        init { require(completedExchanges > 0) }
    }

    /** Session termination is being sent after the final exchange. */
    public data class Terminating(
        /** One-based exchange number after which termination occurs. */
        public val exchange: Int,
    ) : MobileWalletProximityState {
        init { require(exchange > 0) }
    }

    /** Session reached a normal terminal state. */
    public data class Completed(
        /** Number of requests handled before termination. */
        public val exchanges: Int,
        /** Whether the holder declined the final reviewed request. */
        public val declined: Boolean,
    ) :
        MobileWalletProximityState {
        init { require(exchanges > 0) }
    }

    /** Session was cancelled locally and all owned resources were released. */
    public data object Cancelled : MobileWalletProximityState

    /** Session terminated because of a stable Wallet SDK failure. */
    public data class Failed(
        /** Display-safe terminal failure. */
        public val error: MobileWalletProximityError,
    ) : MobileWalletProximityState
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
    /** Hot state stream whose variants define the only legal phase data and actions. */
    public val state: StateFlow<MobileWalletProximityState>

    /** Dispatches one state-bound action. Illegal or stale actions are rejected without side effects. */
    public suspend fun dispatch(action: MobileWalletProximityAction): MobileWalletProximityActionResult

    /** Idempotently cancels an active session and releases all session-owned resources. */
    public suspend fun close()
}
