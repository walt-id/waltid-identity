import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension WalletCore.ProximityCapabilities {
    func toSwiftCapabilities() -> ProximityCapabilities {
        ProximityCapabilities(
            profile: profile.toSwiftProfile(),
            session: swiftSession(session),
            qrEngagement: qrEngagement.toSwiftCapability(),
            nfcEngagement: nfcEngagement.toSwiftCapability(),
            bluetoothLowEnergy: bluetoothLowEnergy.toSwiftCapability(),
            nfcRetrieval: nfcRetrieval.toSwiftCapability(),
            nfcV2Retrieval: nfcV2Retrieval.toSwiftCapability(),
            wifiAwareRetrieval: wifiAwareRetrieval.toSwiftCapability()
        )
    }
}

extension WalletCore.ProximityTransportCapability {
    func toSwiftCapability() -> ProximityTransportCapability {
        let observation: ProximityRuntimeObservation
        switch onEnum(of: runtime) {
        case .notChecked: observation = .notChecked
        case .available: observation = .available
        case let .unavailable(value):
            observation = .unavailable(
                value.error.toSwiftError(),
                remediationActions: swiftArray(value.remediationActions, of: WalletCore.ProximityRemediationAction.self)
                    .map { $0.toSwiftAction() }
            )
        }
        return ProximityTransportCapability(
            implemented: implemented,
            profilePermitted: profilePermitted,
            runtime: observation,
            selected: selected
        )
    }
}

extension WalletCore.ProximityRemediationAction {
    func toSwiftAction() -> ProximityRemediationAction {
        switch self {
        case .requestBluetoothPermission: return .requestBluetoothPermission
        case .requestNearbyWifiPermission: return .requestNearbyWifiPermission
        case .requestLocalNetworkPermission: return .requestLocalNetworkPermission
        case .openApplicationSettings: return .openApplicationSettings
        case .enableBluetooth: return .enableBluetooth
        case .enableWifi: return .enableWifi
        case .enableNfc: return .enableNFC
        case .useSupportedDevice: return .useSupportedDevice
        case .retry: return .retry
        }
    }
}

extension WalletCore.ProximityError {
    func toSwiftError() -> ProximityError {
        ProximityError(
            category: category.toSwiftCategory(),
            code: code,
            message: message,
            recovery: recovery.toSwiftRecovery(),
            remediationActions: swiftArray(remediationActions, of: WalletCore.ProximityRemediationAction.self)
                .map { $0.toSwiftAction() }
        )
    }
}

private extension WalletCore.ProximityRecovery {
    func toSwiftRecovery() -> ProximityRecovery {
        switch self {
        case .none: .none
        case .retryPrerequisites: .retryPrerequisites
        case .startNewSession: .startNewSession
        }
    }
}

private extension WalletCore.ProximityErrorCategory {
    func toSwiftCategory() -> ProximityErrorCategory {
        switch self {
        case .capability: return .capability
        case .engagement: return .engagement
        case .transport: return .transport
        case .protocol: return .protocolFailure
        case .readerAuthentication: return .readerAuthentication
        case .trust: return .trust
        case .credential: return .credential
        case .holderKey: return .holderKey
        case .applicationProfile: return .applicationProfile
        case .staleSubmission: return .staleSubmission
        case .policy: return .policy
        case .internal: return .internalFailure
        }
    }
}
#endif
