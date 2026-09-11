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
    /// The reader sent a request that requires holder review.
    case requestReceived
    /// The reader or request differs from the exact scope approved before connecting.
    case preparedSharingChanged
}

/// When the holder approved the last locally completed response.
public enum ProximityApprovalTiming: Sendable, Equatable {
    /// The holder reviewed and approved the request while connected to the reader.
    case duringConnection
    /// The holder approved a collected request before reconnecting to the reader.
    case beforeConnection
}

/// Display-only receipt; it neither authorizes reuse nor asserts reader-side verification.
public struct ProximitySharingReceipt: Sendable, Equatable {
    /// Reader request reviewed for the locally completed response.
    public let review: ProximityReview
    /// Holder-approved credential and field choices for that response.
    public let submission: ProximitySubmission
    /// Whether approval occurred before or during the connection.
    public let approvalTiming: ProximityApprovalTiming
    /// Local response-completion time, not a reader verification timestamp.
    public let completedAt: Date
}

protocol ProximitySharingPlanBridge: Sendable {
    var isExpired: Bool { get }
    func approve(_ submission: ProximitySubmission) throws -> ProximityPreparationResult
}

/// A recent authenticated request. Retaining this plan does not authorize any disclosure.
public struct ProximitySharingPlan: Sendable, Equatable {
    /// Authenticated recipient, requested data, retention and purpose to show before approval.
    public let review: ProximityReview
    /// Display deadline for the collected request; the wallet enforces expiry using monotonic time.
    public let expiresAt: Date
    /// SHA-256 fingerprint identifying the exact reader certificate associated with the request.
    public let readerCertificateSHA256: String
    let bridge: any ProximitySharingPlanBridge

    /// Whether the wallet considers the request too old to prepare for sharing.
    public var isExpired: Bool { bridge.isExpired }

    /// Call after showing and deliberately approving these exact choices. No radio or key operation starts here.
    /// - Parameter submission: Exact credential and field choices deliberately approved by the holder.
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
    /// The reviewed selection was accepted for one subsequent connection attempt.
    case prepared(ProximityPreparedSharing)
    /// The plan or selection could not be approved; the error describes the reason and recovery action.
    case rejected(ProximityError)
}

protocol ProximityPreparedSharingBridge: Sendable {
    var remainingSeconds: Int { get }
    func revoke() async
}

/// Opaque approval for one wallet, one connection attempt and a 60-second preparation window.
public final class ProximityPreparedSharing: Sendable {
    /// Original reader request to display alongside the holder's exact selection.
    public let review: ProximityReview
    /// Exact credential and field choices authorized by this one-use approval.
    public let submission: ProximitySubmission
    /// Display deadline for the approval; wall-clock changes cannot extend its validity.
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
