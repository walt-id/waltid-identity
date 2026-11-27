import Foundation

/// Actor-safe, single-use native facade over one KMP proximity session.
@available(macOS 10.15, *)
public actor ProximitySession {
    /// Exhaustive state stream whose terminal state is emitted before completion.
    public nonisolated let states: AsyncStream<ProximityState>
    /// Whether this live session currently owns Core NFC's modal emulation UI.
    /// Hosts may preserve the session during the resulting background transition.
    public nonisolated var systemPresentationActive: Bool { bridge.systemPresentationActive }
    /// Winning route once connected, independent of configured and advertised methods.
    public nonisolated var connectedRoute: ProximityConnectedRoute? { bridge.connectedRoute }
    /// Recent authenticated request, usable only after a separate explicit approval.
    public nonisolated var sharingPlan: ProximitySharingPlan? { bridge.sharingPlan }
    /// Opens the iOS NFC sheet after an explicit user choice of a prepared NFC engagement.
    /// Calls outside NFC engagement readiness are ignored; failures arrive through ``states``.
    public func presentNfc() async {
        guard !closed else { return }
        await bridge.presentNfc()
    }
    /// Dispatches one host intent against the current session state.
    /// - Parameter action: Action derived from the current state's ``ProximityState/legalActions``.
    /// - Returns: Whether the session accepted the action.
    /// Dispatches approval using the exact selected cached status warnings shown by the host.
    /// - Parameter reviewedStatus: Selected credential IDs mapped to the semantic revisions actually shown during consent.
    public func dispatchStatusReviewed(_ action: ProximityAction, reviewedStatus: [String: String]) async throws -> ProximityActionResult {
        guard !closed else { return try await dispatch(action) }
        return try await bridge.dispatchStatusReviewed(action, reviewedStatus: reviewedStatus)
    }

    /// Dispatches one host intent against the current session state.
    /// - Parameter action: Action derived from the current state's ``ProximityState/legalActions``.
    /// - Returns: Whether the session accepted the action.
    public func dispatch(_ action: ProximityAction) async throws -> ProximityActionResult {
        guard !closed else {
            return .rejected(
                ProximityError(
                    category: .policy,
                    code: "session_closed",
                    message: "The proximity presentation session is closed",
                    recovery: .none
                )
            )
        }
        return try await bridge.dispatch(action)
    }

    /// Idempotently cancels and releases session-owned resources.
    public func close() async {
        guard !closed else { return }
        closed = true
        await bridge.close()
    }

    init(bridge: any ProximitySessionBridge) {
        self.bridge = bridge
        self.states = AsyncStream { continuation in
            let task = Task {
                for await state in bridge.states {
                    continuation.yield(state)
                    switch state {
                    case .preparationRequired, .completed, .noData, .cancelled, .failed:
                        continuation.finish()
                        return
                    default: break
                    }
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    private let bridge: any ProximitySessionBridge

    private var closed = false
}

@available(macOS 10.15, *)
protocol ProximitySessionBridge: Sendable {
    var connectedRoute: ProximityConnectedRoute? { get }
    var sharingPlan: ProximitySharingPlan? { get }
    var systemPresentationActive: Bool { get }
    var states: AsyncStream<ProximityState> { get }
    func presentNfc() async
    func dispatch(_ action: ProximityAction) async throws -> ProximityActionResult
    func dispatchStatusReviewed(_ action: ProximityAction, reviewedStatus: [String: String]) async throws -> ProximityActionResult
    func close() async
}

@available(macOS 10.15, *)
extension ProximitySessionBridge {
    var connectedRoute: ProximityConnectedRoute? { nil }
    var sharingPlan: ProximitySharingPlan? { nil }
}
