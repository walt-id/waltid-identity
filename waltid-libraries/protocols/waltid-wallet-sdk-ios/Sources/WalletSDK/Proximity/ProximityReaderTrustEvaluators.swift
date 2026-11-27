import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

/// Swift-native facade for the shared ISO reader-certificate and RICAL trust evaluator.
public final class ProximityConfiguredReaderTrustEvaluator:
    ProximityReaderTrustEvaluator,
    KMPBackedProximityReaderTrustEvaluator,
    @unchecked Sendable {
    public init(configuration: ProximityReaderTrustConfiguration) {
        WalletCoreRuntime.initializeIfNeeded()
        evaluator = WalletCore.ProximityConfiguredReaderTrustEvaluator(
            configuration: configuration.toKMPConfiguration()
        )
    }

    public func evaluate(
        _ evidence: ProximityReaderEvidence
    ) async throws -> ProximityReaderTrustDecision {
        try await evaluator.evaluate(evidence: evidence.toKMPEvidence()).toSwiftDecision()
    }

    private let evaluator: WalletCore.ProximityConfiguredReaderTrustEvaluator

    var kmpReaderTrustEvaluator: any WalletCore.ProximityReaderTrustEvaluator {
        evaluator
    }
}

/// Swift-native access to shared direct complete-CRL verification.
///
/// The application supplies issuer lookup certificates, selects the checked path scope, and owns
/// transport/cache policy. Lookup certificates do not establish trust. Unsupported CRL forms and
/// unavailable status remain indeterminate; verified revocation prevents disclosure.
public final class ProximityCRLRevocationEvaluator: ProximityReaderRevocationEvaluator, @unchecked Sendable {
    /// Creates an explicit CRL evaluator for a reader revocation policy.
    /// - Parameters:
    ///   - issuerCertificatesDER: Public issuer lookup certificates, up to ten certificates.
    ///   - scope: Whether to check only the reader or also its issuing authorities.
    ///   - fetcher: Application transport with explicit timeout, redirect and destination policy.
    /// - Throws: ``WalletError/invalidInput(_:)`` when issuer input is invalid or exceeds bounds.
    public init(
        issuerCertificatesDER: [Data],
        scope: ProximityCRLScope,
        fetcher: any ProximityCRLFetcher
    ) throws {
        guard (1...10).contains(issuerCertificatesDER.count),
              issuerCertificatesDER.allSatisfy({ !$0.isEmpty && $0.count <= 65_536 }) else {
            throw WalletError.invalidInput("CRL issuer certificates exceed the supported bounds.")
        }
        WalletCoreRuntime.initializeIfNeeded()
        let kmpScope: WalletCore.ProximityCrlScope
        switch scope {
        case .readerCertificate: kmpScope = .readerCertificate
        case .validatedPath: kmpScope = .validatedPath
        case .readerCertificateAndIssuingAuthorities: kmpScope = .readerCertificateAndIssuingAuthorities
        }
        do {
            evaluator = try WalletCore.ProximityCrlRevocationEvaluator(
                issuerCertificatesDerBase64Url: issuerCertificatesDER.map { $0.base64URLEncodedString() },
                scope: kmpScope,
                fetcher: KMPProximityCRLFetcherAdapter(fetcher)
            )
        } catch {
            throw WalletError.invalidInput("CRL issuer certificates are invalid or exceed the supported bounds.")
        }
    }

    /// Verifies the configured certificate scope using current complete CRLs.
    /// - Parameter evidence: Authenticated reader evidence for one request scope.
    /// - Returns: Good, revoked or indeterminate status, independently of certificate-path trust.
    public func evaluate(_ evidence: ProximityReaderEvidence) async throws -> ProximityCertificateRevocationResult {
        try Task.checkCancellation()
        let result: any WalletCore.ProximityCertificateRevocationResult
        do {
            result = try await evaluator.evaluate(evidence: evidence.toKMPEvidence())
        } catch {
            try Task.checkCancellation()
            throw WalletError.internalFailure("CRL evaluation could not complete.")
        }
        try Task.checkCancellation()
        switch onEnum(of: result) {
        case .good:
            return .good
        case let .revoked(value):
            return try .revoked(reason: value.reason)
        case let .indeterminate(value):
            return try .indeterminate(reason: value.reason)
        }
    }

    let evaluator: WalletCore.ProximityCrlRevocationEvaluator
}
#endif
