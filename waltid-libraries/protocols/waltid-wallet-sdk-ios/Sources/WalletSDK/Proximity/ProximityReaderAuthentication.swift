import Foundation

/// Portion of the device request covered by reader authentication.
public enum ProximityReaderAuthenticationScope: Sendable, Equatable {
    /// Authentication covers one document request.
    case document(ProximityDocumentRequestIndex)
    /// Authentication covers the whole device request.
    case wholeRequest

    /// Creates a document scope after validating its zero-based index.
    /// - Parameter index: Nonnegative document-request index.
    public static func document(index: Int) -> Self { .document(ProximityDocumentRequestIndex(index)) }

    /// Document index carried by this scope, if it is document-scoped.
    public var documentRequestIndex: Int? {
        if case let .document(index) = self { index.value } else { nil }
    }
}

/// Validated zero-based index used by document-scoped reader authentication.
public struct ProximityDocumentRequestIndex: Sendable, Equatable {
    /// Nonnegative document-request index.
    public let value: Int
    /// Creates a validated index.
    /// - Parameter value: Nonnegative document-request index.
    public init(_ value: Int) { precondition(value >= 0); self.value = value }
}

/// Structural and cryptographic validity of reader authentication.
public enum ProximityReaderAuthenticationValidity: Sendable, Equatable {
    /// No reader authentication was supplied.
    case absent
    /// Reader authentication could not be decoded.
    case malformed
    /// Reader authentication was decoded but did not verify.
    case invalid
    /// Reader authentication verified cryptographically.
    case valid
}

/// Application-owned trust result for a cryptographically valid reader.
public enum ProximityReaderTrustState: Sendable, Equatable {
    /// No application trust evaluator ran.
    case notEvaluated
    /// The reader authentication is valid but the application does not trust it.
    case validButUntrusted
    /// The reader certificate was revoked.
    case revoked
    /// The application accepts the reader as trusted.
    case trusted
}

/// Certificate-path validation result kept separate from the trust decision.
public enum ProximityReaderCertificatePathState: Sendable, Equatable {
    /// Certificate-path validation was not performed.
    case notEvaluated
    /// The reader path is structurally valid, but no configured authority matches it.
    case unknownAuthority
    /// The certificate path is invalid.
    case invalid
    /// The certificate path is valid.
    case valid
}

/// Reader-certificate revocation result kept separate from the trust decision.
public enum ProximityReaderRevocationState: Sendable, Equatable {
    /// Revocation was not checked.
    case notChecked
    /// The certificate is known not to be revoked.
    case good
    /// The certificate is revoked.
    case revoked
    /// Revocation status could not be established.
    case indeterminate
}

/// Optional RICAL evidence. A match is evidence and never establishes product trust by itself.
public enum ProximityRICALState: Sendable, Equatable {
    /// RICAL evaluation was not performed.
    case notEvaluated
    /// No RICAL source was available.
    case unavailable
    /// The available RICAL data was invalid.
    case invalid
    /// No authority matched the reader certificate.
    case noMatchingAuthority
    /// A RICAL authority matched the reader certificate.
    case matched
}

/// Exact verified reader evidence passed to an application-owned trust policy.
public struct ProximityReaderEvidence: Sendable, Equatable {
    /// Portion of the request authenticated by this evidence.
    public let scope: ProximityReaderAuthenticationScope
    /// Zero-based statement index within the authentication scope.
    public let authenticationIndex: Int
    /// DER certificates in leaf-first order.
    public let certificateChainDER: [Data]

    /// Creates verified reader evidence for application trust evaluation.
    /// - Parameters:
    ///   - scope: Portion of the request covered by the authentication.
    ///   - certificateChainDER: Nonempty DER certificate chain in leaf-first order.
    ///   - authenticationIndex: Statement index within the authentication scope.
    public init(
        scope: ProximityReaderAuthenticationScope,
        certificateChainDER: [Data],
        authenticationIndex: Int = 0
    ) {
        precondition(
            !certificateChainDER.isEmpty && certificateChainDER.allSatisfy { !$0.isEmpty },
            "Verified reader evidence requires nonempty certificates"
        )
        precondition(authenticationIndex >= 0)
        self.scope = scope
        self.authenticationIndex = authenticationIndex
        self.certificateChainDER = certificateChainDER
    }
}

/// Application trust decision with path, revocation, and RICAL facts kept separate.
public struct ProximityReaderTrustDecision: Sendable, Equatable {
    /// Final application trust state.
    public let state: ProximityReaderTrustState
    /// Independently reported certificate-path result.
    public let certificatePath: ProximityReaderCertificatePathState
    /// Independently reported revocation result.
    public let revocation: ProximityReaderRevocationState
    /// Independently reported RICAL evidence result.
    public let rical: ProximityRICALState
    /// Display-safe reader name supplied by the application.
    public let displayName: String?
    /// Display-safe explanation supplied by the application.
    public let reason: String?

    /// Creates a coherent application-owned reader-trust decision.
    /// - Throws: `WalletError.invalidInput` if the values violate the model contract.
    /// - Parameters:
    ///   - state: Final trust state.
    ///   - certificatePath: Certificate-path result.
    ///   - revocation: Revocation result.
    ///   - rical: RICAL evidence result.
    ///   - displayName: Optional display-safe reader name.
    ///   - reason: Optional display-safe explanation.
    public init(
        state: ProximityReaderTrustState,
        certificatePath: ProximityReaderCertificatePathState = .notEvaluated,
        revocation: ProximityReaderRevocationState = .notChecked,
        rical: ProximityRICALState = .notEvaluated,
        displayName: String? = nil,
        reason: String? = nil
    ) throws {
        try requireProximityInput(state != .notEvaluated, "A trust evaluator must return an evaluated state")
        try requireProximityInput(state != .revoked || revocation == .revoked)
        try requireProximityInput(revocation != .revoked || state == .revoked)
        try requireProximityInput(state != .trusted || certificatePath == .valid)
        try requireProximityInput(state != .revoked || certificatePath == .valid)
        try requireProximityInput(certificatePath != .unknownAuthority || state == .validButUntrusted)
        try requireProximityInput(certificatePath != .invalid || state == .validButUntrusted)
        try requireProximityInput(certificatePath != .invalid || revocation == .notChecked)
        try requireProximityInput(state != .trusted || revocation != .indeterminate)
        try requireProximityInput(rical != .matched || certificatePath == .valid)
        try requireProximityInput(displayName == nil || isProximityNonBlank(displayName!))
        try requireProximityInput(reason == nil || isProximityNonBlank(reason!))
        self.state = state
        self.certificatePath = certificatePath
        self.revocation = revocation
        self.rical = rical
        self.displayName = displayName
        self.reason = reason
    }
}

/// Explicit Swift-owned trust boundary. The SDK performs no network lookup implicitly.
public protocol ProximityReaderTrustEvaluator: Sendable {
    /// Evaluates exact verified reader evidence without an implicit SDK lookup.
    /// - Parameter evidence: Verified reader evidence for one authentication scope.
    /// - Returns: The application's coherent trust decision.
    func evaluate(_ evidence: ProximityReaderEvidence) async throws -> ProximityReaderTrustDecision
}

/// Review-safe reader-authentication result for one request scope.
public struct ProximityReaderAuthentication: Sendable, Equatable {
    /// Portion of the request covered by the result.
    public let scope: ProximityReaderAuthenticationScope
    /// Only verified authentication carries evaluated trust facts.
    public let outcome: ProximityReaderAuthenticationOutcome
    /// Zero-based statement index within the scope.
    public let authenticationIndex: Int
    /// Structural and cryptographic validity derived from the outcome.
    public var validity: ProximityReaderAuthenticationValidity {
        switch outcome {
        case .absent: .absent
        case .malformed: .malformed
        case .invalid: .invalid
        case .valid: .valid
        }
    }
    /// Application trust state when evaluated.
    public var trust: ProximityReaderTrustState { decision?.state ?? .notEvaluated }
    /// Independently evaluated certificate-path fact.
    public var certificatePath: ProximityReaderCertificatePathState { decision?.certificatePath ?? .notEvaluated }
    /// Independently evaluated revocation fact.
    public var revocation: ProximityReaderRevocationState { decision?.revocation ?? .notChecked }
    /// Independently evaluated RICAL fact.
    public var rical: ProximityRICALState { decision?.rical ?? .notEvaluated }
    /// Reader name established by trust policy.
    public var displayName: String? { decision?.displayName }
    /// Display-safe authentication or trust explanation.
    public var reason: String? {
        switch outcome {
        case .absent: nil
        case let .malformed(reason), let .invalid(reason): reason
        case let .valid(trust): trust.reason
        }
    }
    private var decision: ProximityReaderTrustDecision? {
        if case let .valid(value) = outcome { value } else { nil }
    }
}

/// Authentication outcome with trust facts confined to verified authentication.
public enum ProximityReaderAuthenticationOutcome: Sendable, Equatable {
    /// No authentication statement was provided.
    case absent
    /// Authentication could not be parsed.
    case malformed(reason: String)
    /// Cryptographic verification failed.
    case invalid(reason: String)
    /// Verified authentication and independently evaluated trust facts.
    case valid(ProximityReaderTrustDecision)
}

/// Request-wide coverage and trust computed by the shared wallet SDK.
public enum ProximityReaderAuthenticationSummary: Sendable, Equatable {
    /// No reader authentication was supplied.
    case absent
    /// At least one authentication is malformed.
    case malformed
    /// At least one authentication is invalid.
    case invalid
    /// At least one authenticated reader is revoked.
    case revoked
    /// Valid authentication does not cover every requested document.
    case partial
    /// Coverage is complete, but at least one document lacks trusted authentication.
    case validButUntrusted
    /// Every document is covered by trusted authentication.
    case trusted
}
