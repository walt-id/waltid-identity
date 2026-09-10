import Foundation

/// Holder approval is independent of the selected proximity transport.
public enum ProximityApproval: Sendable {
    /// Show each request while connected, where the platform permits interaction.
    case askEachTime
    /// Authenticate the reader and collect its request without disclosure, then approve before reconnecting.
    case prepareBeforeSharing
    /// A deliberate, one-use approval issued by this wallet.
    case prepared(ProximityPreparedSharing)
}

/// Explains why the holder is being asked to review a request.
public enum ProximityReviewReason: Sendable, Equatable {
    case requestReceived
    case preparedSharingChanged
}

/// When the holder approved the last locally completed response.
public enum ProximityApprovalTiming: Sendable, Equatable {
    case duringConnection
    case beforeConnection
}

/// Display-only receipt; it neither authorizes reuse nor asserts reader-side verification.
public struct ProximitySharingReceipt: Sendable, Equatable {
    public let review: ProximityReview
    public let submission: ProximitySubmission
    public let approvalTiming: ProximityApprovalTiming
    public let completedAt: Date
}

protocol ProximitySharingPlanBridge: Sendable {
    var isExpired: Bool { get }
    func approve(_ submission: ProximitySubmission) throws -> ProximityPreparationResult
}

/// A recent authenticated request. Retaining this plan does not authorize any disclosure.
public struct ProximitySharingPlan: Sendable, Equatable {
    public let review: ProximityReview
    public let expiresAt: Date
    public let readerCertificateSHA256: String
    let bridge: any ProximitySharingPlanBridge

    public var isExpired: Bool { bridge.isExpired }

    /// Call after showing and deliberately approving these exact choices. No radio or key operation starts here.
    public func approve(_ submission: ProximitySubmission) throws -> ProximityPreparationResult {
        try bridge.approve(submission)
    }

    public static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.review == rhs.review && lhs.expiresAt == rhs.expiresAt &&
            lhs.readerCertificateSHA256 == rhs.readerCertificateSHA256
    }
}

/// Result of explicitly approving a recent request before connection.
public enum ProximityPreparationResult: Sendable {
    case prepared(ProximityPreparedSharing)
    case rejected(ProximityError)
}

protocol ProximityPreparedSharingBridge: Sendable {
    var remainingSeconds: Int { get }
    func revoke() async
}

/// Opaque approval for one wallet, one connection attempt and a 60-second preparation window.
public final class ProximityPreparedSharing: Sendable {
    public let review: ProximityReview
    public let submission: ProximitySubmission
    public let expiresAt: Date
    let bridge: any ProximityPreparedSharingBridge

    init(review: ProximityReview, submission: ProximitySubmission, expiresAt: Date, bridge: any ProximityPreparedSharingBridge) {
        self.review = review
        self.submission = submission
        self.expiresAt = expiresAt
        self.bridge = bridge
    }

    /// Uses the wallet's monotonic deadline; changing the wall clock cannot extend permission.
    public var remainingSeconds: Int { bridge.remainingSeconds }

    /// Cancels future use. An already sent response cannot be withdrawn.
    public func revoke() async { await bridge.revoke() }
}
