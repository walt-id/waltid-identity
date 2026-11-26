import Foundation

/// Host intent accepted by a session only when legal for its current state.
public enum ProximityAction: Sendable, Equatable {
    /// Approve a submission derived from the current frozen review.
    case approve(reviewID: ProximityReviewID, submission: ProximitySubmission)
    /// Decline the current disclosure request without sharing documents.
    case decline(reviewID: ProximityReviewID)
    /// Cancel the session and release its resources.
    case cancel
    /// Re-run prerequisite checks after external conditions may have changed.
    case retryPrerequisites
    /// Report the privacy-safe outcome of a host remediation surface.
    case reportRemediation(ProximityRemediationAction, ProximityHostActionResult)
}

/// Privacy-safe outcome of a system surface performed by the host application.
public enum ProximityHostActionResult: Sendable, Equatable {
    /// The host completed the requested platform action.
    case completed
    /// The user cancelled the platform action.
    case cancelled
    /// The platform action failed without exposing sensitive diagnostics.
    case failed
}

/// Prepared engagement presented by the host UI.
public enum ProximityEngagement: Sendable, Equatable {
    /// QR engagement payload to render locally without transformation.
    case qr(payload: String)
    /// NFC engagement is prepared and awaits a platform interaction.
    case nfc
}

/// One protected-key operation required by a frozen approved document response.
public struct ProximityHolderAuthorizationRequest: Sendable, Equatable, Identifiable {
    /// Stable identity equal to ``requestIndex``.
    public var id: Int { requestIndex }
    /// Zero-based document-request index.
    public let requestIndex: Int
    /// Credential whose protected key is required.
    public let credentialID: String
    /// Device-authentication method frozen during review.
    public let deviceAuthentication: ProximityDeviceAuthenticationMethod
}

/// Exact holder-key authorization context for a frozen approved submission.
public struct ProximityHolderAuthorization: Sendable, Equatable {
    /// Consumed review whose accepted choices require protected-key authorization.
    public let reviewID: ProximityReviewID
    /// One-based exchange number being authorized.
    public let exchange: Int
    /// Per-document protected-key operations required by the frozen response.
    public let requests: [ProximityHolderAuthorizationRequest]
}

/// Result of attempting a host action.
public enum ProximityActionResult: Sendable, Equatable {
    /// The session accepted the action.
    case accepted
    /// The session rejected the action without changing its approved state.
    case rejected(ProximityError)
}

/// Coarse action identity used to drive host controls from state.
public enum ProximityActionType: Sendable, Hashable {
    /// Approve the current review.
    case approve
    /// Decline the current review.
    case decline
    /// Cancel the active session.
    case cancel
    /// Re-run prerequisite checks.
    case retryPrerequisites
    /// Report a host remediation result.
    case reportRemediation
}

/// Display-safe session state projected exhaustively from the KMP source of truth.
public enum ProximityState: Sendable, Equatable {
    /// The session is waiting for selected capabilities to become available.
    case checkingPrerequisites(ProximityCapabilities)
    /// Session-owned cryptographic and transport resources are being prepared.
    case preparing(profile: ProximityProfile)
    /// At least one engagement is ready for the host to present.
    case engagementReady([ProximityEngagement])
    /// A reader is connecting through a prepared engagement.
    case connecting([ProximityEngagement])
    /// The holder is waiting for a device request.
    case awaitingRequest(exchange: Int)
    /// The host must present the frozen review and collect explicit holder intent.
    case reviewRequired(ProximityReview, reason: ProximityReviewReason = .requestReceived)
    /// The connection ended without disclosure. Review and approve before a fresh connection.
    case preparationRequired(ProximitySharingPlan, reason: ProximityReviewReason = .requestReceived)
    /// A protected holder key is authorizing the frozen approved submission.
    case authorizingHolderKey(ProximityHolderAuthorization)
    /// The response for an exchange is being sent.
    case sendingResponse(exchange: Int)
    /// The response was sent and the session awaits another request.
    case awaitingNextRequest(completedExchanges: Int)
    /// The protocol is terminating the transport for an exchange.
    case terminating(exchange: Int)
    /// The session completed normally.
    case completed(exchanges: Int, declined: Bool, receipt: ProximitySharingReceipt? = nil)
    /// Matching could not satisfy the final request; its original requested information is always included.
    /// No credential data was shared for this request. Earlier exchanges may already have shared data.
    case noData(exchange: Int, unsatisfiedRequest: ProximityUnsatisfiedRequest)
    /// The host cancelled the session.
    case cancelled
    /// The session failed with a display-safe typed error.
    case failed(ProximityError)

    /// Actions legal in this exact state.
    public var legalActions: Set<ProximityActionType> {
        switch self {
        case .checkingPrerequisites: [.retryPrerequisites, .reportRemediation, .cancel]
        case .reviewRequired: [.approve, .decline, .cancel]
        case .preparing, .engagementReady, .connecting,
             .awaitingRequest, .authorizingHolderKey, .sendingResponse, .awaitingNextRequest:
            [.cancel]
        case .terminating, .preparationRequired, .completed, .noData, .cancelled, .failed:
            []
        }
    }
}

/// Engagement that actually won the reader connection.
public enum ProximityEngagementMethod: Sendable, Equatable {
    /// The reader scanned a QR engagement.
    case qr
    /// The reader used NFC engagement.
    case nfc
}

/// Bearer of the last received message, or the initial connection before any message.
public enum ProximityTransport: Sendable, Equatable {
    /// Bluetooth Low Energy carries the session.
    case bluetoothLowEnergy
    /// The NFC channel carries the session.
    case nfc
    /// Wi-Fi Aware carries the session.
    case wifiAware
}

/// Actual route retained through review and termination.
public struct ProximityConnectedRoute: Sendable, Equatable {
    /// Engagement that won the reader connection.
    public let engagement: ProximityEngagementMethod
    /// Bearer of the last received message, or the initial connection before any message.
    public let transport: ProximityTransport

    /// Creates a snapshot of the actual connected route.
    /// - Parameters:
    ///   - engagement: Engagement that won the connection.
    ///   - transport: Bearer of the last received message, or the initial connection before any message.
    public init(engagement: ProximityEngagementMethod, transport: ProximityTransport) {
        self.engagement = engagement
        self.transport = transport
    }
}
