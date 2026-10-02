import Foundation
import WalletSDK

/// Retains the original result while individual pending operations continue.
public struct IssuanceReceipt {
    public let issuer: IssuanceIssuerPreview?
    public let pendingIDs: Set<String>
    public let problem: IssuanceFailure?

    public init(issuer: IssuanceIssuerPreview? = nil, pendingIDs: Set<String> = [], problem: IssuanceFailure? = nil) {
        self.issuer = issuer
        self.pendingIDs = pendingIDs
        self.problem = problem
    }
}

extension DeferredCredential {
    public var canResume: Bool {
        status != .remoteOutcomeUncertain && status != .storageOutcomeUncertain
    }
}

extension IssuanceOutcome {
    public var hasPendingCredentials: Bool {
        switch self {
        case let .deferred(_, _, pending), let .failed(_, _, _, pending): return !pending.isEmpty
        default: return false
        }
    }

    public func withContinuations(_ latest: [DeferredCredential], resumingID: String? = nil) -> IssuanceOutcome {
        func updated(_ items: [DeferredCredential]) -> [DeferredCredential] {
            items.map { item in
                let retained = latest.first(where: { $0.id == item.id })
                var status = retained?.status ?? item.status
                if status == .unresolved, item.id == resumingID, case let .failed(_, error, _, _) = self {
                    if error.code == .remoteOutcomeUncertain { status = .remoteOutcomeUncertain }
                    if error.code == .storageOutcomeUncertain { status = .storageOutcomeUncertain }
                }
                return DeferredCredential(id: item.id, credentialConfigurationID: item.credentialConfigurationID,
                    intervalSeconds: item.intervalSeconds, credentialIdentifier: item.credentialIdentifier,
                    status: status, displayMetadataJSON: retained?.displayMetadataJSON ?? item.displayMetadataJSON)
            }
        }
        switch self {
        case let .deferred(sessionID, storedIDs, pending):
            return .deferred(sessionID: sessionID, storedCredentialIDs: storedIDs, credentials: updated(pending))
        case let .failed(sessionID, error, storedIDs, pending):
            return .failed(sessionID: sessionID, error: error, storedCredentialIDs: storedIDs, deferredCredentials: updated(pending))
        default: return self
        }
    }
}
