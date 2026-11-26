import Foundation

/// Versioned mdoc interoperability boundary selected for one proximity session.
public enum ProximityProfile: String, Sendable, CaseIterable, Equatable {
    /// ISO/IEC 18013-5:2021 behavior.
    case iso1801352021
    /// ISO/IEC 18013-5 second-edition DIS behavior.
    case iso180135Edition2DIS2026
    /// EUDI ARF 3.0 / Common Acceptance Framework profile behavior.
    case eudiARF3FCAF202608
}

/// BLE roles the holder prepares for one session.
public enum ProximityBLERoles: Sendable, Hashable {
    /// Connect to a reader that advertises the GATT service.
    case centralClient
    /// Advertise a GATT service for a reader to connect to.
    case peripheralServer
    /// Prepare both supported BLE roles and advertise both retrieval options.
    case dual
}

/// BLE bearer selection policy. This is intended for integration and debug configuration, not normal UI.
public enum ProximityBLEBearerPolicy: Sendable, Hashable {
    /// Use the interoperable GATT bearer only.
    case gattOnly
    /// Prefer L2CAP when negotiated and otherwise use GATT.
    case preferL2CAP
}

/// Complete BLE bearer configuration; it exists only when BLE retrieval is selected.
public struct ProximityBLEConfiguration: Sendable, Hashable {
    /// BLE roles prepared for the session.
    public let roles: ProximityBLERoles
    /// BLE bearer-selection policy.
    public let bearerPolicy: ProximityBLEBearerPolicy

    /// Creates a complete BLE retrieval configuration.
    /// - Parameters:
    ///   - roles: BLE roles to prepare for the session.
    ///   - bearerPolicy: Policy used to select the BLE bearer.
    public init(
        roles: ProximityBLERoles = .dual,
        bearerPolicy: ProximityBLEBearerPolicy = .preferL2CAP
    ) {
        self.roles = roles
        self.bearerPolicy = bearerPolicy
    }
}

/// Complete conventional NFC retrieval length contract.
public struct ProximityNFCRetrievalConfiguration: Sendable, Hashable {
    /// Maximum command-data length accepted from the reader.
    public let maximumCommandDataLength: Int
    /// Maximum response-data length returned to the reader.
    public let maximumResponseDataLength: Int

    /// Creates a validated conventional NFC retrieval configuration.
    /// - Parameters:
    ///   - maximumCommandDataLength: Maximum command-data length accepted from the reader.
    ///   - maximumResponseDataLength: Maximum response-data length returned to the reader.
    public init(
        maximumCommandDataLength: Int = 65_535,
        maximumResponseDataLength: Int = 65_536
    ) {
        precondition((255...65_535).contains(maximumCommandDataLength))
        precondition((256...65_536).contains(maximumResponseDataLength))
        self.maximumCommandDataLength = maximumCommandDataLength
        self.maximumResponseDataLength = maximumResponseDataLength
    }
}

/// A nonempty retrieval plan used by QR, Static Handover, or Negotiated Handover.
public struct ProximityRetrievalOptions: Sendable, Hashable {
    /// Optional BLE role and bearer policy.
    public let bluetoothLowEnergy: ProximityBLEConfiguration?
    /// Optional conventional NFC command/response contract.
    public let nfc: ProximityNFCRetrievalConfiguration?
    /// Whether to offer Wi-Fi Aware with mandatory NCS-SK-128 security.
    public let wifiAware: Bool

    /// Creates a nonempty conventional retrieval configuration.
    /// - Parameters:
    ///   - bluetoothLowEnergy: Optional BLE role and bearer configuration.
    ///   - nfc: Optional conventional NFC command/response configuration.
    ///   - wifiAware: Whether to offer Wi-Fi Aware retrieval.
    public init(
        bluetoothLowEnergy: ProximityBLEConfiguration? = .init(),
        nfc: ProximityNFCRetrievalConfiguration? = nil,
        wifiAware: Bool = false
    ) {
        precondition(bluetoothLowEnergy != nil || nfc != nil || wifiAware)
        self.bluetoothLowEnergy = bluetoothLowEnergy
        self.nfc = nfc
        self.wifiAware = wifiAware
    }
}

/// Conventional NFC Forum handover mode.
public enum ProximityNFCHandover: Sendable, Hashable {
    /// Holder-selected retrieval methods.
    case staticHandover
    /// Reader-selected retrieval method.
    case negotiatedHandover
}

/// Conventional NFC handover and its compatible retrieval plans.
public struct ProximityNFCSessionConfiguration: Sendable, Hashable {
    /// NFC Forum handover mode.
    public let handover: ProximityNFCHandover
    /// Nonempty retrieval plan offered through NFC.
    public let retrieval: ProximityRetrievalOptions
    /// Optional nonempty retrieval plan offered through QR.
    public let qrFallback: ProximityRetrievalOptions?

    /// Creates a conventional NFC session with an optional QR fallback.
    /// - Parameters:
    ///   - handover: NFC Forum handover mode.
    ///   - retrieval: Bearers offered through NFC.
    ///   - qrFallback: QR bearers, using the same BLE policy and NFC length limits when shared.
    public init(
        handover: ProximityNFCHandover,
        retrieval: ProximityRetrievalOptions,
        qrFallback: ProximityRetrievalOptions? = nil
    ) {
        requireSharedBLEPolicy(retrieval.bluetoothLowEnergy, qrFallback?.bluetoothLowEnergy)
        precondition(retrieval.nfc == nil || qrFallback?.nfc == nil || retrieval.nfc == qrFallback?.nfc,
                     "QR and NFC handover must use the same conventional NFC retrieval length limits")
        self.handover = handover
        self.retrieval = retrieval
        self.qrFallback = qrFallback
    }
}

/// Provisional NFCv2 engagement and its mandatory same-channel retrieval.
public struct ProximityNFCV2SessionConfiguration: Sendable, Hashable {
    /// Maximum command-data length accepted by the provisional holder application.
    public let maximumCommandDataLength: Int
    /// Optional NFCv2 alternate BLE bearer.
    public let bluetoothLowEnergy: ProximityBLEConfiguration?
    /// Whether to offer an alternate Wi-Fi Aware bearer with mandatory NCS-SK-128 security.
    public let wifiAware: Bool
    /// Optional nonempty retrieval plan offered through QR.
    public let qrFallback: ProximityRetrievalOptions?

    /// Creates an NFCv2 session with optional alternate BLE and QR retrieval.
    /// - Parameters:
    ///   - maximumCommandDataLength: Validated NFCv2 command-data limit.
    ///   - bluetoothLowEnergy: Optional alternate BLE bearer.
    ///   - qrFallback: Bearers offered through QR, when selected.
    ///   - wifiAware: Whether to offer an alternate Wi-Fi Aware bearer.
    public init(
        maximumCommandDataLength: Int = 65_536,
        bluetoothLowEnergy: ProximityBLEConfiguration? = nil,
        qrFallback: ProximityRetrievalOptions? = nil,
        wifiAware: Bool = false
    ) {
        precondition((1...65_536).contains(maximumCommandDataLength))
        requireSharedBLEPolicy(bluetoothLowEnergy, qrFallback?.bluetoothLowEnergy)
        self.maximumCommandDataLength = maximumCommandDataLength
        self.bluetoothLowEnergy = bluetoothLowEnergy
        self.qrFallback = qrFallback
        self.wifiAware = wifiAware
    }
}

/// Owns engagement and compatible retrieval together for one single-use session.
public enum ProximitySessionConfiguration: Sendable, Hashable {
    /// QR engagement with a nonempty conventional retrieval plan.
    case qr(ProximityRetrievalOptions = .init())
    /// Conventional NFC handover with an optional QR fallback.
    case nfc(ProximityNFCSessionConfiguration)
    /// Provisional NFCv2 engagement with its same-channel retrieval and optional paths.
    case provisionalNFCV2(ProximityNFCV2SessionConfiguration = .init())
}

extension ProximitySessionConfiguration {
    var qrRetrieval: ProximityRetrievalOptions? {
        switch self {
        case let .qr(retrieval): return retrieval
        case let .nfc(configuration): return configuration.qrFallback
        case let .provisionalNFCV2(configuration): return configuration.qrFallback
        }
    }

    var usesProvisionalNFCV2: Bool {
        if case .provisionalNFCV2 = self { true } else { false }
    }
}

private func requireSharedBLEPolicy(
    _ nfc: ProximityBLEConfiguration?, _ qr: ProximityBLEConfiguration?
) {
    precondition(nfc == nil || qr == nil || nfc == qr, "QR and NFC must use the same BLE policy")
}
