import Foundation

/// Explicit application-provisioned Reader CA certificate and optional display label.
public struct ProximityReaderTrustAnchor: Sendable, Equatable {
    /// DER-encoded Reader CA certificate.
    public let certificateDER: Data
    /// Display-safe authority label that does not establish trust by itself.
    public let displayName: String?

    /// Creates an explicitly application-provisioned Reader CA trust anchor.
    /// - Parameters:
    ///   - certificateDER: DER-encoded Reader CA certificate.
    ///   - displayName: Optional display-safe authority label.
    public init(certificateDER: Data, displayName: String? = nil) throws {
        try requireProximityInput(isProximityX509Certificate(certificateDER))
        try requireProximityInput(displayName.map(isProximityNonBlank) ?? true)
        self.certificateDER = certificateDER
        self.displayName = displayName
    }
}

/// Immutable result from an application-owned certificate-status source.
public struct ProximityCertificateRevocationResult: Sendable, Equatable {
    /// The configured source established that the certificate is not revoked.
    public static let good = Self(storage: .good)

    /// The configured source established that the certificate is revoked.
    public static func revoked(reason: String? = nil) throws -> Self {
        try requireProximityInput(reason.map(isProximityNonBlank) ?? true)
        return Self(storage: .revoked(reason: reason))
    }

    /// The configured source could not establish current revocation status.
    public static func indeterminate(reason: String) throws -> Self {
        try requireProximityInput(isProximityNonBlank(reason))
        return Self(storage: .indeterminate(reason: reason))
    }

    enum Storage: Sendable, Equatable {
        case good
        case revoked(reason: String?)
        case indeterminate(reason: String)
    }

    let storage: Storage
}

/// Application boundary for OCSP, CRL, or another reader-certificate status source.
public protocol ProximityReaderRevocationEvaluator: Sendable {
    /// Evaluates current revocation status for a verified reader chain.
    /// - Parameter evidence: Verified reader evidence for one authentication scope.
    /// - Returns: The configured source's immutable certificate-status result.
    func evaluate(_ evidence: ProximityReaderEvidence) async throws -> ProximityCertificateRevocationResult
}

/// Certificates whose complete CRL status the application requires.
public enum ProximityCRLScope: Sendable, Equatable {
    /// Check the reader certificate against its direct issuer's CRL.
    case readerCertificate
    /// Check certificates below the configured anchor through the configured reader-trust evaluator.
    case validatedPath
    /// Also check issuing authorities, including the terminal self-signed authority.
    case readerCertificateAndIssuingAuthorities
}

/// A complete CRL obtained through the application's transport or cache policy.
public enum ProximityCRLFetchResult: Sendable, Equatable {
    /// Complete DER bytes; the SDK verifies signature, scope and freshness.
    /// - Parameter der: Complete DER-encoded certificate revocation list.
    case available(der: Data)
    /// No complete CRL is available within the application's transport policy.
    case unavailable
}

/// Application-owned HTTP transport for explicitly configured CRL evaluation.
public protocol ProximityCRLFetcher: Sendable {
    /// Retrieves a complete DER CRL under application timeout, redirect and destination policy.
    /// - Parameters:
    ///   - url: HTTP or HTTPS distribution-point URL from a reader-path certificate.
    ///   - maximumBytes: Maximum permitted response size before Base64 encoding.
    /// - Returns: Complete DER bytes or an unavailable result.
    func fetch(from url: URL, maximumBytes: Int) async throws -> ProximityCRLFetchResult
}

/// Explicit revocation behavior selected for reader trust.
public enum ProximityReaderRevocationPolicy: Sendable {
    /// Do not perform revocation lookup; the resulting fact remains ``ProximityReaderRevocationState/notChecked``.
    case notChecked
    /// Require the supplied source to return a conclusive result before the reader can be trusted.
    case check(any ProximityReaderRevocationEvaluator)
}

/// Swift-native immutable configuration for the shared standards reader-trust evaluator.
public struct ProximityReaderTrustConfiguration: Sendable {
    /// Explicit application-provisioned Reader CA trust anchors.
    public let trustAnchors: [ProximityReaderTrustAnchor]
    /// Ordered application-configured RICAL provider policies.
    public let ricalProviders: [ProximityRICALConfiguration]
    /// Revocation behavior for reader chains trusted by direct Reader CA anchors.
    public let revocationPolicy: ProximityReaderRevocationPolicy

    /// Optional application-identified IACA direct issuer required on the validated path.
    /// When set, also requires an issuer-alternative-name email or URI contact in the reader certificate.
    /// This restriction does not add trust; the application identifies the issuer's IACA role.
    public let requiredIACAIssuerCertificateDER: Data?

    /// Creates immutable application-owned reader-trust configuration.
    /// - Parameters:
    ///   - trustAnchors: Explicit Reader CA trust anchors.
    ///   - ricalProviders: Ordered RICAL provider policies.
    ///   - revocationPolicy: Revocation behavior for directly anchored reader chains.
    ///   - requiredIACAIssuerCertificateDER: Optional exact IACA direct issuer; also requires issuer contact in the reader certificate.
    public init(
        trustAnchors: [ProximityReaderTrustAnchor] = [],
        ricalProviders: [ProximityRICALConfiguration] = [],
        revocationPolicy: ProximityReaderRevocationPolicy = .notChecked,
        requiredIACAIssuerCertificateDER: Data? = nil
    ) throws {
        try requireProximityInput(!trustAnchors.isEmpty || !ricalProviders.isEmpty)
        try requireProximityInput(Set(trustAnchors.map(\.certificateDER)).count == trustAnchors.count)
        try requireProximityInput(Set(ricalProviders.map(\.providerID)).count == ricalProviders.count)
        self.trustAnchors = trustAnchors
        self.ricalProviders = ricalProviders
        self.revocationPolicy = revocationPolicy
        self.requiredIACAIssuerCertificateDER = requiredIACAIssuerCertificateDER
    }
}
