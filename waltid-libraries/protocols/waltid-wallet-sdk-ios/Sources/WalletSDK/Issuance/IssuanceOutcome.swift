import Foundation

/// Stable category for a sanitized issuance failure.
public enum IssuanceErrorCode: Equatable, Sendable {
    /// The session identifier is unknown, expired, or already consumed.
    case invalidSession

    /// The authorization callback failed session or redirect validation.
    case invalidCallback

    /// Required application input is missing or invalid.
    case invalidInput

    /// The authorization server returned an OAuth authorization error.
    case authorizationFailed

    /// Issuer or authorization server metadata is invalid or unsupported.
    case issuerMetadata

    /// The issuer or authorization server rejected a protocol request.
    case issuerResponse
    /// The issued credential did not match a configured receipt authority.
    case issuerTrust

    /// A transport failure prevented issuer communication.
    case network

    /// A remote request is running or its response was lost; automatic retry is unsafe.
    case remoteOutcomeUncertain

    /// A local save is running or stopped without releasing ownership; automatic takeover is unsafe.
    case storageOutcomeUncertain

    /// Key selection, signing, or proof generation failed.
    case crypto

    /// Credential parsing or local persistence failed.
    case storage

    /// A protocol response was structurally invalid or inconsistent.
    case `protocol`
}

/// Sanitized error returned by a typed issuance transition.
public struct IssuanceFailure: Equatable, Sendable {
    /// Stable error category suitable for application control flow.
    public let code: IssuanceErrorCode

    /// Public error description that excludes protocol secrets.
    public let message: String

    /// Failed target and unattempted targets, when failure occurred during credential processing.
    public let targetFailure: IssuanceTargetFailure?

    /// Whether the caller may submit the same reviewed offer again after this transition.
    public let canRetryOffer: Bool

    /// Creates a failure with the session's explicit offer retry decision.
    ///
    /// - Parameters:
    ///   - code: The category of the failed issuance transition.
    ///   - message: A public description that excludes protocol secrets.
    ///   - canRetryOffer: Whether the caller may submit the same reviewed offer again.
    ///   - targetFailure: The failed credential target and any unattempted targets, when available.
    public init(code: IssuanceErrorCode, message: String, canRetryOffer: Bool = false, targetFailure: IssuanceTargetFailure? = nil) {
        self.code = code
        self.message = message
        self.targetFailure = targetFailure
        self.canRetryOffer = canRetryOffer
    }
}

/// Immediate, deferred, cancelled, or failed result of one issuance transition.
public enum IssuanceOutcome: Equatable, Sendable {
    /// All returned credentials were stored successfully.
    case stored(sessionID: String, credentialIDs: [String])

    /// At least one credential is awaiting issuer processing.
    case deferred(sessionID: String, storedCredentialIDs: [String], credentials: [DeferredCredential])

    /// The user or authorization server cancelled the issuance session.
    case cancelled(sessionID: String)

    /// The transition failed with a sanitized error and any credentials stored before the failure.
    case failed(sessionID: String, error: IssuanceFailure, storedCredentialIDs: [String], deferredCredentials: [DeferredCredential])
}
