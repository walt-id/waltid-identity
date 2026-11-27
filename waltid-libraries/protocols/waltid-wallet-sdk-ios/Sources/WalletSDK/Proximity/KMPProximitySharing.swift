import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension WalletCore.ProximitySharingPlan {
    func toSwiftSharingPlan() throws -> ProximitySharingPlan {
        .init(review: try review.toSwiftReview(), expiresAt: expiresAt.toDate(),
              readerCertificateSHA256: readerCertificateSha256, bridge: KMPSharingPlanBridge(self))
    }
}

extension WalletCore.ProximitySharingReceipt {
    func toSwiftReceipt() throws -> ProximitySharingReceipt {
        .init(review: try review.toSwiftReview(), submission: try submission.toSwiftSubmission(),
              approvalTiming: approvalTiming == .beforeConnection ? .beforeConnection : .duringConnection,
              completedAt: completedAt.toDate())
    }
}

extension ProximitySubmission {
    func toKMPSubmission() -> WalletCore.ProximitySubmission {
        .init(documents: documents.map { document in
            WalletCore.ProximityDocumentSubmission(requestIndex: Int32(document.requestIndex), credentialId: document.credentialID,
                disclosedElements: Set(document.disclosedElements.map {
                    WalletCore.ProximityElementReference(namespace: $0.namespace, elementIdentifier: $0.elementIdentifier)
                }))
        }, continueAfterResponse: continueAfterResponse)
    }
}

extension WalletCore.ProximitySubmission {
    func toSwiftSubmission() throws -> ProximitySubmission {
        try .init(documents: requiredBridgeArray(documents, of: WalletCore.ProximityDocumentSubmission.self).map { document in
            try ProximityDocumentSubmission(requestIndex: Int(document.requestIndex), credentialID: document.credentialId,
                disclosedElements: Set(try requiredBridgeSet(document.disclosedElements, of: WalletCore.ProximityElementReference.self).map {
                    try ProximityElementReference(namespace: $0.namespace, elementIdentifier: $0.elementIdentifier)
                }))
        }, continueAfterResponse: continueAfterResponse)
    }
}

extension ProximityApproval {
    func toKMPApproval() -> any WalletCore.ProximityApproval {
        switch self {
        case .askEachTime: return WalletCore.ProximityApprovalAskEachTime.shared
        case .prepareBeforeSharing: return WalletCore.ProximityApprovalPrepareBeforeSharing.shared
        case .prepared(let sharing):
            guard let bridge = sharing.bridge as? KMPPreparedSharingBridge else {
                preconditionFailure("Prepared sharing must be issued by this wallet SDK")
            }
            return WalletCore.ProximityApprovalPrepared(sharing: bridge.sharing)
        }
    }
}

private final class KMPSharingPlanBridge: ProximitySharingPlanBridge, @unchecked Sendable {
    let plan: WalletCore.ProximitySharingPlan
    init(_ plan: WalletCore.ProximitySharingPlan) { self.plan = plan }
    var isExpired: Bool { plan.isExpired }

    func approveStatusReviewed(_ submission: ProximitySubmission, reviewedStatus: [String: String]) throws -> ProximityPreparationResult {
        switch onEnum(of: plan.approveStatusReviewed(submission: submission.toKMPSubmission(), reviewedStatus: reviewedStatus)) {
        case .prepared(let value): return .prepared(try value.sharing.toSwiftPreparedSharing())
        case .rejected(let value): return .rejected(value.error.toSwiftError())
        }
    }

    func approve(_ submission: ProximitySubmission) throws -> ProximityPreparationResult {
        switch onEnum(of: plan.approve(submission: submission.toKMPSubmission())) {
        case .prepared(let value): return .prepared(try value.sharing.toSwiftPreparedSharing())
        case .rejected(let value): return .rejected(value.error.toSwiftError())
        }
    }
}

private final class KMPPreparedSharingBridge: ProximityPreparedSharingBridge, @unchecked Sendable {
    let sharing: WalletCore.ProximityPreparedSharing
    init(_ sharing: WalletCore.ProximityPreparedSharing) { self.sharing = sharing }
    var remainingSeconds: Int { Int(sharing.remainingSeconds) }
    func revoke() async { try? await sharing.revoke() }
}

private extension WalletCore.ProximityPreparedSharing {
    func toSwiftPreparedSharing() throws -> ProximityPreparedSharing {
        .init(review: try review.toSwiftReview(), submission: try submission.toSwiftSubmission(),
              expiresAt: expiresAt.toDate(), bridge: KMPPreparedSharingBridge(self))
    }
}
#endif
