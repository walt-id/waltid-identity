import Foundation

/// Authorization for due-only status maintenance on a managed local wallet.
public enum CredentialStatusMaintenancePreference: String, Sendable {
    /// Background maintenance is off and the stored grant is retired.
    case off = "OFF"
    /// Consent is retained while host policy suspends maintenance.
    case paused = "PAUSED"
    /// The wallet has an active maintenance grant.
    case enabled = "ENABLED"
}

/// The durable preference and its current opaque authorization. A paused enrollment has no binding.
public struct CredentialStatusMaintenanceEnrollment: Sendable {
    /// The preference committed to the managed wallet.
    public let preference: CredentialStatusMaintenancePreference
    /// The opaque grant, available only while maintenance is enabled.
    public let binding: String?
}

/// An aggregate result. Check failures and original observation times remain available in the local wallet snapshot.
public enum CredentialStatusMaintenanceResult: Equatable, Sendable {
    /// The pass ended with counts of completed attempts, unchanged entries, and entries left for a later pass.
    case finished(checked: Int, unchanged: Int, remaining: Int, hasNewNotice: Bool = false)
    /// The pass must wait for accessible storage or another execution window.
    case deferred
    /// The supplied binding could not authorize an existing wallet.
    case ineligible
}

/// A wallet-local change to show after unlock. Acknowledgement is scoped to this version.
public struct PendingCredentialStatusNotice: Sendable, Equatable {
    public let credentialID: String
    public let version: String
    public let kind: CredentialStatusIssue
    public let checkedAt: Date
    public let historical: Bool
}
