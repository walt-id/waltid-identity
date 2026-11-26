import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension ProximityProfile {
    func toKMPProfile() -> WalletCore.ProximityProfile {
        switch self {
        case .iso1801352021: return .iso1801352021
        case .iso180135Edition2DIS2026: return .iso180135Edition2Dis2026
        case .eudiARF3FCAF202608: return .eudiArf3Fcaf202608
        }
    }
}

extension WalletCore.ProximityProfile {
    func toSwiftProfile() -> ProximityProfile {
        switch self {
        case .iso1801352021: return .iso1801352021
        case .iso180135Edition2Dis2026: return .iso180135Edition2DIS2026
        case .eudiArf3Fcaf202608: return .eudiARF3FCAF202608
        }
    }
}

extension ProximityBLEBearerPolicy {
    func toKMPPolicy() -> WalletCore.ProximityBleBearerPolicy {
        switch self {
        case .gattOnly: return .gattOnly
        case .preferL2CAP: return .preferL2cap
        }
    }
}

extension ProximityBLEConfiguration {
    func toKMPConfiguration() -> WalletCore.ProximityBleConfiguration {
        WalletCore.ProximityBleConfiguration(
            roles: roles.toKMPRoles(),
            bearerPolicy: bearerPolicy.toKMPPolicy()
        )
    }
}

extension ProximityNFCRetrievalConfiguration {
    func toKMPConfiguration() -> WalletCore.ProximityNfcRetrievalConfiguration {
        WalletCore.ProximityNfcRetrievalConfiguration(
            maximumCommandDataLength: Int32(maximumCommandDataLength),
            maximumResponseDataLength: Int32(maximumResponseDataLength)
        )
    }
}

extension ProximityRetrievalOptions {
    func toKMPConfiguration() -> WalletCore.ProximityRetrievalOptions {
        WalletCore.ProximityRetrievalOptions(
            bluetoothLowEnergy: bluetoothLowEnergy?.toKMPConfiguration(),
            nfc: nfc?.toKMPConfiguration(),
            wifiAware: wifiAware
        )
    }
}

extension ProximitySessionConfiguration {
    func toKMPConfiguration() -> any WalletCore.ProximitySessionConfiguration {
        switch self {
        case let .qr(retrieval):
            return WalletCore.ProximitySessionConfigurationQr(retrieval: retrieval.toKMPConfiguration())
        case let .nfc(configuration):
            return WalletCore.ProximitySessionConfigurationConventionalNfc(
                handover: configuration.handover == .staticHandover ? .static : .negotiated,
                retrieval: configuration.retrieval.toKMPConfiguration(),
                qrFallback: configuration.qrFallback?.toKMPConfiguration()
            )
        case let .provisionalNFCV2(configuration):
            return WalletCore.ProximitySessionConfigurationProvisionalNfcV2(
                maximumCommandDataLength: Int32(configuration.maximumCommandDataLength),
                bluetoothLowEnergy: configuration.bluetoothLowEnergy?.toKMPConfiguration(),
                qrFallback: configuration.qrFallback?.toKMPConfiguration(),
                wifiAware: configuration.wifiAware
            )
        }
    }
}

func swiftSession(_ session: any WalletCore.ProximitySessionConfiguration) throws -> ProximitySessionConfiguration {
    switch onEnum(of: session) {
    case let .qr(value): return try .qr(value.retrieval.toSwiftConfiguration())
    case let .conventionalNfc(value):
        return try .nfc(.init(
            handover: value.handover == .static ? .staticHandover : .negotiatedHandover,
            retrieval: value.retrieval.toSwiftConfiguration(),
            qrFallback: value.qrFallback?.toSwiftConfiguration()
        ))
    case let .provisionalNfcV2(value):
        return try .provisionalNFCV2(.init(
            maximumCommandDataLength: Int(value.maximumCommandDataLength),
            bluetoothLowEnergy: value.bluetoothLowEnergy?.toSwiftConfiguration(),
            qrFallback: value.qrFallback?.toSwiftConfiguration(),
            wifiAware: value.wifiAware
        ))
    }
}

extension ProximityReaderPolicy {
    func toKMPPolicy() -> WalletCore.ProximityReaderPolicy {
        switch self {
        case .allowAnonymousOrUntrusted: return .allowAnonymousOrUntrusted
        case .requireTrusted: return .requireTrusted
        }
    }
}

extension ProximityDeviceAuthenticationPolicy {
    func toKMPPolicy() -> WalletCore.ProximityDeviceAuthenticationPolicy {
        switch self {
        case .signatureOnly: return .signatureOnly
        case .macOnly: return .macOnly
        case .preferSignature: return .preferSignature
        case .preferMAC: return .preferMac
        }
    }
}

extension WalletCore.ProximityReaderAuthenticationScope {
    func toSwiftScope() throws -> ProximityReaderAuthenticationScope {
        switch onEnum(of: self) {
        case let .document(value): return try .document(index: Int(value.index))
        case .wholeRequest: return .wholeRequest
        }
    }
}

extension ProximityReaderAuthenticationScope {
    func toKMPScope() -> WalletCore.ProximityReaderAuthenticationScope {
        switch self {
        case .document(let index): return WalletCore.ProximityReaderAuthenticationScopeDocument(index: index.value)
        case .wholeRequest: return WalletCore.ProximityReaderAuthenticationScopeWholeRequest.shared
        }
    }
}

extension ProximityReaderTrustState {
    func toKMPState() -> WalletCore.ProximityReaderTrustState {
        switch self {
        case .notEvaluated: return .notEvaluated
        case .validButUntrusted: return .validButUntrusted
        case .revoked: return .revoked
        case .trusted: return .trusted
        }
    }
}

extension ProximityReaderCertificatePathState {
    func toKMPState() -> WalletCore.ProximityReaderCertificatePathState {
        switch self {
        case .notEvaluated: return .notEvaluated
        case .unknownAuthority: return .unknownAuthority
        case .invalid: return .invalid
        case .valid: return .valid
        }
    }
}

extension ProximityReaderRevocationState {
    func toKMPState() -> WalletCore.ProximityReaderRevocationState {
        switch self {
        case .notChecked: return .notChecked
        case .good: return .good
        case .revoked: return .revoked
        case .indeterminate: return .indeterminate
        }
    }
}

extension ProximityRICALState {
    func toKMPState() -> WalletCore.ProximityRicalState {
        switch self {
        case .notEvaluated: return .notEvaluated
        case .unavailable: return .unavailable
        case .invalid: return .invalid
        case .noMatchingAuthority: return .noMatchingAuthority
        case .matched: return .matched
        }
    }
}

private extension ProximityBLERoles {
    func toKMPRoles() -> WalletCore.ProximityBleRoles {
        switch self {
        case .centralClient: return .centralClient
        case .peripheralServer: return .peripheralServer
        case .dual: return .dual
        }
    }
}

private extension WalletCore.ProximityBleConfiguration {
    func toSwiftConfiguration() -> ProximityBLEConfiguration {
        ProximityBLEConfiguration(
            roles: roles == .centralClient ? .centralClient : roles == .peripheralServer ? .peripheralServer : .dual,
            bearerPolicy: bearerPolicy == .gattOnly ? .gattOnly : .preferL2CAP
        )
    }
}

private extension WalletCore.ProximityRetrievalOptions {
    func toSwiftConfiguration() throws -> ProximityRetrievalOptions {
        try ProximityRetrievalOptions(
            bluetoothLowEnergy: bluetoothLowEnergy?.toSwiftConfiguration(),
            nfc: nfc.map { try ProximityNFCRetrievalConfiguration(
                maximumCommandDataLength: Int($0.maximumCommandDataLength),
                maximumResponseDataLength: Int($0.maximumResponseDataLength)
            ) },
            wifiAware: wifiAware
        )
    }
}
#endif
