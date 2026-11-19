/// Authorization for due-only status maintenance on a managed local wallet.
public enum CredentialStatusMaintenancePreference: String, Sendable {
    case off = "OFF"
    case paused = "PAUSED"
    case enabled = "ENABLED"
}

/// The durable preference and its current opaque authorization. A paused enrollment has no binding.
public struct CredentialStatusMaintenanceEnrollment: Sendable {
    public let preference: CredentialStatusMaintenancePreference
    public let binding: String?
}

/// An aggregate result. Check failures and original observation times remain available in the local wallet snapshot.
public enum CredentialStatusMaintenanceResult: Equatable, Sendable {
    case finished(checked: Int, unchanged: Int, remaining: Int)
    case deferred
    case ineligible
}
