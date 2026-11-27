import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

final class KMPProximityPresentationSessionBridge:
    ProximitySessionBridge,
    @unchecked Sendable {
    init(session: any WalletCore.ProximitySession, nfcHost: IOSNfcHostPlatformAdapter) {
        self.session = session
        self.nfcHost = nfcHost
    }
    var connectedRoute: ProximityConnectedRoute? {
        guard let route = session.connectedRoute else { return nil }
        let engagement: ProximityEngagementMethod
        switch route.engagement {
        case .qr: engagement = .qr
        case .nfc: engagement = .nfc
        }
        let transport: ProximityTransport
        switch route.transport {
        case .bluetoothLowEnergy: transport = .bluetoothLowEnergy
        case .nfc: transport = .nfc
        case .wifiAware: transport = .wifiAware
        }
        return .init(engagement: engagement, transport: transport)
    }

    var sharingPlan: ProximitySharingPlan? { try? session.sharingPlan?.toSwiftSharingPlan() }

    var systemPresentationActive: Bool {
        guard let projected = try? session.state.value.toSwiftState(),
              projected.legalActions.contains(.cancel) else { return false }
        return nfcHost.isPresenting
    }

    func presentNfc() async {
        guard let projected = try? session.state.value.toSwiftState(),
              case .engagementReady(let engagements) = projected,
              engagements.contains(where: { if case .nfc = $0 { return true }; return false }) else { return }
        await nfcHost.present()
    }

    var states: AsyncStream<ProximityState> {
        AsyncStream { continuation in
            let task = Task { [session] in
                let flow = SkieSwiftFlow<any WalletCore.ProximityState>(
                    SkieKotlinFlow(session.state)
                )
                for await state in flow {
                    do {
                        continuation.yield(try state.toSwiftState())
                    } catch {
                        continuation.yield(
                            .failed(
                                ProximityError(
                                    category: .internalFailure,
                                    code: "invalid_sdk_state",
                                    message: "The wallet returned an invalid proximity session state",
                                    recovery: .none
                                )
                            )
                        )
                        break
                    }
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func dispatchStatusReviewed(_ action: ProximityAction, reviewedStatus: [String: String]) async throws -> ProximityActionResult {
        try await session.dispatchStatusReviewed(action: action.toKMPAction(), reviewedStatus: reviewedStatus).toSwiftResult()
    }

    func dispatch(
        _ action: ProximityAction
    ) async throws -> ProximityActionResult {
        try await session.dispatch(action: action.toKMPAction()).toSwiftResult()
    }

    func close() async {
        try? await session.close()
    }

    private let session: any WalletCore.ProximitySession

    private let nfcHost: IOSNfcHostPlatformAdapter
}
#endif
