import WalletSDK

/// Latest manual batch, kept separate from read observation and individual refresh activity.
public struct CredentialStatusRefreshSummary: Equatable {
    public let completed: Int
    public let unchanged: Int
    public let deferred: Int
    public let checkErrors: Int
    public init(completed: Int, unchanged: Int, deferred: Int, checkErrors: Int) {
        precondition([completed, unchanged, deferred, checkErrors].allSatisfy { $0 >= 0 })
        self.completed = completed; self.unchanged = unchanged; self.deferred = deferred; self.checkErrors = checkErrors
    }
    public init(results: [CredentialStatusRefreshResult]) {
        self.init(completed: results.filter { $0.outcome == .completed }.count,
                  unchanged: results.filter { $0.outcome == .unchanged }.count,
                  deferred: results.filter { $0.outcome == .deferred }.count,
                  checkErrors: results.filter { $0.outcome != .removed && $0.status.hasCheckError }.count)
    }
}
