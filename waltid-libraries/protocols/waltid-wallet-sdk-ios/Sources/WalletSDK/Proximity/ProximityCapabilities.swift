import Foundation

/// Stable error category for host presentation and recovery policy.
public enum ProximityErrorCategory: Sendable, Equatable {
    /// Device or runtime capability is unavailable.
    case capability
    /// Device engagement failed.
    case engagement
    /// Retrieval transport failed.
    case transport
    /// ISO protocol processing failed.
    case protocolFailure
    /// Reader authentication was absent, malformed, or invalid.
    case readerAuthentication
    /// Application reader-trust policy rejected the request.
    case trust
    /// No acceptable credential can satisfy the request.
    case credential
    /// Holder-key resolution or use failed.
    case holderKey
    /// Application-profile processing rejected or failed.
    case applicationProfile
    /// The approved submission no longer matches current request state.
    case staleSubmission
    /// The requested action violates session policy.
    case policy
    /// An unexpected internal failure occurred.
    case internalFailure
}

/// Display-safe, typed proximity failure.
public struct ProximityError: Error, Sendable, Equatable {
    /// Stable failure category.
    public let category: ProximityErrorCategory
    /// Stable machine-readable error code.
    public let code: String
    /// Display-safe error message.
    public let message: String
    /// Recovery supported by the phase that reported this failure.
    public let recovery: ProximityRecovery
    /// Host actions supplied by the SDK for this error.
    public let remediationActions: [ProximityRemediationAction]

    /// Creates a display-safe failure with optional host recovery actions.
    /// - Parameters:
    ///   - category: Stable failure category.
    ///   - code: Stable machine-readable error code.
    ///   - message: Display-safe explanation without raw exception content.
    ///   - recovery: Whether recovery requires a fresh session.
    ///   - remediationActions: Host actions that may restore availability.
    public init(
        category: ProximityErrorCategory, code: String, message: String,
        recovery: ProximityRecovery,
        remediationActions: [ProximityRemediationAction] = []
    ) {
        self.category = category
        self.code = code
        self.message = message
        self.recovery = recovery
        self.remediationActions = remediationActions
    }
}

/// Recovery distinguishes an active prerequisite loop from a terminal session.
public enum ProximityRecovery: Sendable, Equatable {
    /// No retry is suggested.
    case none
    /// Recheck prerequisites in this still-active session after remediation.
    case retryPrerequisites
    /// Create a fresh session to retry after terminal failure.
    case startNewSession
}

/// Host action that may restore a selected proximity capability.
public enum ProximityRemediationAction: Sendable, Hashable {
    /// Request Bluetooth permission using the platform system surface.
    case requestBluetoothPermission
    /// Request Nearby Wi-Fi devices permission using the platform system surface.
    case requestNearbyWifiPermission
    /// Request local-network permission using the platform system surface.
    case requestLocalNetworkPermission
    /// Open application settings using the platform system surface.
    case openApplicationSettings
    /// Ask the user to enable Bluetooth through the platform-owned surface.
    case enableBluetooth
    /// Ask the user to enable Wi-Fi through the platform-owned surface.
    case enableWifi
    /// Ask the user to enable NFC through the platform-owned surface.
    case enableNFC
    /// Explain that the selected capability requires another device.
    case useSupportedDevice
    /// Re-run capability checks without another system surface.
    case retry
}

/// One engagement or retrieval dimension reported independently.
public struct ProximityTransportCapability: Sendable, Equatable {
    /// Whether this SDK build implements the dimension.
    public let implemented: Bool
    /// Whether the selected interoperability profile permits it.
    public let profilePermitted: Bool
    /// Independent result of probing the runtime.
    public let runtime: ProximityRuntimeObservation
    /// Whether session configuration selected it.
    public let selected: Bool
    /// Whether a runtime probe established availability.
    public var runtimeAvailable: Bool { if case .available = runtime { true } else { false } }
    /// Observed runtime failure, if checked and unavailable.
    public var unavailable: ProximityError? {
        if case let .unavailable(error, _) = runtime { error } else { nil }
    }
    /// Ordered actions supplied by the runtime observation.
    public var remediationActions: [ProximityRemediationAction] {
        if case let .unavailable(_, actions) = runtime { actions } else { [] }
    }
    /// Whether this selected dimension may start now.
    public var mayStart: Bool { implemented && profilePermitted && runtimeAvailable && selected }
}

/// Runtime evidence kept independent from session selection.
public enum ProximityRuntimeObservation: Sendable, Equatable {
    /// No runtime probe has run.
    case notChecked
    /// The runtime probe succeeded.
    case available
    /// The runtime probe failed with these possible host actions.
    case unavailable(ProximityError, remediationActions: [ProximityRemediationAction])
}

/// Truthful capability report for every modeled engagement and retrieval dimension.
public struct ProximityCapabilities: Sendable, Equatable {
    /// Profile used to evaluate capability policy.
    public let profile: ProximityProfile
    /// Selected plans relating transport observations to viable routes.
    public let session: ProximitySessionConfiguration
    /// QR device-engagement capability.
    public let qrEngagement: ProximityTransportCapability
    /// NFC device-engagement capability.
    public let nfcEngagement: ProximityTransportCapability
    /// Bluetooth Low Energy retrieval capability.
    public let bluetoothLowEnergy: ProximityTransportCapability
    /// Conventional NFC retrieval capability.
    public let nfcRetrieval: ProximityTransportCapability
    /// Provisional NFCv2 same-channel retrieval capability.
    public let nfcV2Retrieval: ProximityTransportCapability
    /// Wi-Fi Aware retrieval capability.
    public let wifiAwareRetrieval: ProximityTransportCapability
    /// Whether the selected QR plan has an available engagement and bearer.
    public var qrMayStart: Bool { qrEngagement.mayStart && planMayStart(session.qrRetrieval) }
    /// Whether the selected NFC plan has an available engagement and bearer.
    public var nfcMayStart: Bool {
        guard nfcEngagement.mayStart else { return false }
        switch session {
        case .qr: return false
        case let .nfc(configuration): return planMayStart(configuration.retrieval)
        case .provisionalNFCV2: return nfcV2Retrieval.mayStart
        }
    }
    /// Whether at least one complete selected route can start.
    public var mayStart: Bool { qrMayStart || nfcMayStart }

    /// Stable, de-duplicated remediation actions for unavailable selected dimensions.
    public var remediationActions: [ProximityRemediationAction] {
        var seen = Set<ProximityRemediationAction>()
        return [
            qrEngagement,
            nfcEngagement,
            bluetoothLowEnergy,
            nfcRetrieval,
            nfcV2Retrieval,
            wifiAwareRetrieval,
        ]
            .filter(\.selected)
            .flatMap(\.remediationActions)
            .filter { seen.insert($0).inserted }
    }
    private func planMayStart(_ plan: ProximityRetrievalOptions?) -> Bool {
        guard let plan else { return false }
        return (plan.bluetoothLowEnergy != nil && bluetoothLowEnergy.mayStart)
            || (plan.nfc != nil && nfcRetrieval.mayStart)
            || (plan.wifiAware && wifiAwareRetrieval.mayStart)
    }
}
