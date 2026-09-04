import Foundation
@_spi(KmpHostBridge) import WalletSDK
@preconcurrency import sharedUI

/// Adapts the shared Swift CardSession implementation to Compose's independently linked KMP types.
///
/// Kotlin/Native exports a distinct Swift protocol identity in each framework. This class owns no
/// NFC state or behavior: it only converts generated `sharedUI` values at that nominal boundary.
final class ComposeNfcHostPlatformAdapter:
    sharedUI.Waltid_mdoc_proximity_mobileNfcHostPlatformAdapter,
    @unchecked Sendable {
    private let bridge = IOSNfcHostBridge()

    func capability() async throws -> any sharedUI.Waltid_mdoc_proximity_mobileNfcHostAvailability {
        switch await bridge.capability() {
        case .available:
            return sharedUI.WalletDemoIosKt.composeNfcHostAvailable()
        case let .unavailable(reason):
            return unavailable(reason)
        }
    }

    func prepare(
        router: sharedUI.Waltid_mdoc_proximity_mobileNfcHostApduRouter,
        sessionScope _: any sharedUI.Kotlinx_coroutines_coreCoroutineScope
    ) async throws -> any sharedUI.Waltid_mdoc_proximity_mobileNfcHostPreparation {
        let routerBridge = ComposeNfcHostRouter(router: router)
        switch await bridge.prepare(
            process: { command in
                try await routerBridge.process(command)
            },
            deactivate: { reason in
                await routerBridge.deactivate(reason)
            }
        ) {
        case let .ready(session):
            return sharedUI.WalletDemoIosKt.composeNfcHostReady(
                session: ComposePreparedNfcHostSession(bridgeSession: session)
            )
        case let .unavailable(reason):
            return sharedUI.WalletDemoIosKt.composeNfcHostUnavailablePreparation(
                code: reason.code,
                message: reason.message
            )
        }
    }

    private func unavailable(
        _ reason: IOSNfcHostBridgeUnavailable
    ) -> any sharedUI.Waltid_mdoc_proximity_mobileNfcHostAvailability {
        sharedUI.WalletDemoIosKt.composeNfcHostUnavailable(
            code: reason.code,
            message: reason.message
        )
    }
}

/// Serializes access to the generated Kotlin router at the Swift concurrency boundary.
private actor ComposeNfcHostRouter {
    private let router: sharedUI.Waltid_mdoc_proximity_mobileNfcHostApduRouter

    init(router: sharedUI.Waltid_mdoc_proximity_mobileNfcHostApduRouter) {
        self.router = router
    }

    func process(_ command: Data) async throws -> Data {
        try await router.process(
            encodedCommand: command.composeKotlinByteArray()
        ).doCopy().composeData()
    }

    func deactivate(_ reason: IOSNfcHostBridgeCloseReason) async {
        try? await router.deactivate(reason: reason.sharedUICloseReason)
    }
}

private final class ComposePreparedNfcHostSession:
    sharedUI.Waltid_mdoc_proximity_mobilePreparedNfcHostSession,
    @unchecked Sendable {
    private let bridgeSession: IOSNfcHostBridgeSession

    init(bridgeSession: IOSNfcHostBridgeSession) {
        self.bridgeSession = bridgeSession
    }

    func close(reason: sharedUI.Waltid_mdoc_proximityProximityCloseReason) async throws {
        await bridgeSession.close(reason: reason.nfcHostBridgeReason)
    }
}

private extension sharedUI.Waltid_mdoc_proximityProximityCloseReason {
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
        default:
            return .protocolError
        }
    }
}

private extension IOSNfcHostBridgeCloseReason {
    var sharedUICloseReason: sharedUI.Waltid_mdoc_proximityProximityCloseReason {
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
    func composeKotlinByteArray() -> sharedUI.KotlinByteArray {
        let result = sharedUI.KotlinByteArray(size: Int32(count))
        for (index, byte) in enumerated() {
            result.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
        return result
    }
}

private extension sharedUI.KotlinByteArray {
    func composeData() -> Data {
        Data((0..<Int(size)).map { UInt8(bitPattern: get(index: Int32($0))) })
    }
}
