package id.walt.wallet2.mobile.proximity

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

    private fun planMayStart(plan: ProximityRetrievalOptions?): Boolean = plan != null && (
        (plan.bluetoothLowEnergy != null && bluetoothLowEnergy.mayStart) ||
            (plan.nfc != null && nfcRetrieval.mayStart) ||
            (plan.wifiAware && wifiAwareRetrieval.mayStart)
        )
}
