import Foundation
import WalletSDK

/// A selected record. Missing or ambiguous snapshot data remains an unavailable binding.
public struct ReviewCredentialStatus: Equatable {
    public let credentialID: String
    public let title: String
    public let issuer: String?
    public let storeIndex: Int?
    public let presentation: CredentialStatusPresentation?

    public init(credentialID: String, title: String, issuer: String?, context: CredentialStatusUIContext) {
        self.credentialID = credentialID; self.title = title; self.issuer = issuer
        storeIndex = context.storeIndices[credentialID]
        presentation = context.values[credentialID]?.presentation
    }
}

/// The host supplies the request identity; the key also binds the displayed review and choices.
public struct SharingSubmissionKey: Equatable {
    private let requestIdentity: AnyHashable
    private let review: SharingReviewModel
    private let selection: SharingSelection
    public init(requestIdentity: AnyHashable, review: SharingReviewModel, selection: SharingSelection) {
        self.requestIdentity = requestIdentity; self.review = review; self.selection = selection
    }
}

public extension CredentialStatusUIContext {
    func selectedStatuses(review: SharingReviewModel, selection: SharingSelection) -> [ReviewCredentialStatus] {
        var seen = Set<String>()
        return review.credentialOptions.filter { selection.credentials.contains($0.selection) && seen.insert($0.credentialID).inserted }
            .map {
                let summary = CredentialDisplayNormalizer.details(for: $0).cardSummary
                return ReviewCredentialStatus(credentialID: $0.credentialID, title: summary.title,
                    issuer: summary.issuer, context: self)
            }
    }

    func reviewedRevisions(for credentials: [ReviewCredentialStatus]) -> [String: String]? {
        guard available, credentials.allSatisfy({ $0.storeIndex != nil && $0.presentation?.advisoryRevision.isEmpty == false }) else { return nil }
        return Dictionary(credentials.map { ($0.credentialID, $0.presentation!.advisoryRevision) }, uniquingKeysWith: { first, _ in first })
    }
}
