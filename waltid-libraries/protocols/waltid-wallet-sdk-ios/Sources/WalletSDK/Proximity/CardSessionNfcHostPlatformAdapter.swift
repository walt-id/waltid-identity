#if canImport(CoreNFC) && canImport(WalletCore) && os(iOS)
import Foundation
@preconcurrency import WalletCore

/// Core NFC host-card-emulation boundary used by the shared proximity engine.
///
/// Apple does not expose a public API for reading the app's signed HCE entitlements. The adapter
/// therefore follows the documented fail-closed sequence: `readingAvailable`, `isSupported`, and
/// `isEligible` are all checked before `CardSession` is initialized.
public final class IOSNfcHostPlatformAdapter:
    Waltid_mdoc_proximity_mobileNfcHostPlatformAdapter,
    @unchecked Sendable {
    /// Core NFC owns modal UI until actual dismissal, including during a hybrid exchange.
    public var isUserInteractionBlocked: Bool { bridge.isPresenting }

    /// Creates the Core NFC host adapter used by an iOS KMP wallet host.
    ///
    /// The Swift `Wallet` facade installs this adapter automatically. A host that independently
    /// links another Kotlin framework uses that framework's narrow adapter over the same Swift
    /// CardSession bridge because Kotlin/Native exports distinct nominal protocol identities.
    public convenience init() {
        self.init(bridge: IOSNfcHostBridge())
    }

    public func __capability() async throws -> any Waltid_mdoc_proximity_mobileNfcHostAvailability {
        WalletCoreRuntime.initializeIfNeeded()
        switch await bridge.capability() {
        case .available:
            return Waltid_mdoc_proximity_mobileNfcHostAvailabilityAvailable()
        case let .unavailable(reason):
            return unavailable(reason)
        }
    }

    public func __prepare(
        router: Waltid_mdoc_proximity_mobileNfcHostApduRouter,
        sessionScope _: any Kotlinx_coroutines_coreCoroutineScope
    ) async throws -> any Waltid_mdoc_proximity_mobileNfcHostPreparation {
        let routerBridge = WalletCoreNfcHostRouter(router: router)
        switch await bridge.prepare(
            process: { command in
                try await routerBridge.process(command)
            },
            deactivate: { reason in
                await routerBridge.deactivate(reason)
            }
        ) {
        case let .ready(session):
            return Waltid_mdoc_proximity_mobileNfcHostPreparationReady(
                session: IOSPreparedNfcHostSession(bridgeSession: session)
            )
        case let .unavailable(reason):
            return Waltid_mdoc_proximity_mobileNfcHostPreparationUnavailable(
                availability: unavailable(reason)
            )
        }
    }

    var isPresenting: Bool { bridge.isPresenting }

    func present() async {
        await bridge.present()
    }

    init(environment: any IOSNfcCardSessionEnvironment) {
        self.bridge = IOSNfcHostBridge(environment: environment)
    }

    init(bridge: IOSNfcHostBridge) {
        self.bridge = bridge
    }

    private let bridge: IOSNfcHostBridge

    private func unavailable(
        _ reason: IOSNfcHostBridgeUnavailable
    ) -> Waltid_mdoc_proximity_mobileNfcHostAvailabilityUnavailable {
        Waltid_mdoc_proximity_mobileNfcHostAvailabilityUnavailable(
            code: reason.code,
            message: reason.message
        )
    }
}

final class IOSPreparedNfcHostSession:
    Waltid_mdoc_proximity_mobilePreparedNfcHostSession,
    @unchecked Sendable {
    init(bridgeSession: IOSNfcHostBridgeSession) {
        self.bridgeSession = bridgeSession
    }

    func __close(reason: Waltid_mdoc_proximityProximityCloseReason) async throws {
        await bridgeSession.close(reason: reason.nfcHostBridgeReason)
    }

    private let bridgeSession: IOSNfcHostBridgeSession
}

/// Serializes access to the generated Kotlin router at the Swift concurrency boundary.
private actor WalletCoreNfcHostRouter {
    init(router: Waltid_mdoc_proximity_mobileNfcHostApduRouter) {
        self.router = router
    }

    func process(_ command: Data) async throws -> Data {
        let response = try await router.process(encodedCommand: command.nfcKotlinByteArray())
        return response.toByteArray(startIndex: 0, endIndex: response.size).nfcData()
    }

    func deactivate(_ reason: IOSNfcHostBridgeCloseReason) async {
        try? await router.deactivate(reason: reason.walletCoreReason)
    }

    private let router: Waltid_mdoc_proximity_mobileNfcHostApduRouter
}

private extension Waltid_mdoc_proximityProximityCloseReason {
    var nfcHostBridgeReason: IOSNfcHostBridgeCloseReason {
        switch self {
        case .completed:
            return .completed
        case .handoverCompleted:
            return .handoverCompleted
        case .cancelled:
            return .cancelled
        case .lostRace:
            return .lostRace
        case .timeout:
            return .timeout
        case .peerDisconnected:
            return .peerDisconnected
        case .protocolError:
            return .protocolError
        case .platformUnavailable:
            return .platformUnavailable
        }
    }
}

private extension IOSNfcHostBridgeCloseReason {
    var walletCoreReason: Waltid_mdoc_proximityProximityCloseReason {
        switch self {
        case .completed:
            return .completed
        case .handoverCompleted:
            return .handoverCompleted
        case .cancelled:
            return .cancelled
        case .lostRace:
            return .lostRace
        case .timeout:
            return .timeout
        case .peerDisconnected:
            return .peerDisconnected
        case .protocolError:
            return .protocolError
        case .platformUnavailable:
            return .platformUnavailable
        }
    }
}

private extension Data {
    func nfcKotlinByteArray() -> KotlinByteArray {
        let result = KotlinByteArray(size: Int32(count))
        for (index, byte) in enumerated() {
            result.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
        return result
    }
}

private extension KotlinByteArray {
    func nfcData() -> Data {
        Data((0..<Int(size)).map { UInt8(bitPattern: get(index: Int32($0))) })
    }
}
#endif
