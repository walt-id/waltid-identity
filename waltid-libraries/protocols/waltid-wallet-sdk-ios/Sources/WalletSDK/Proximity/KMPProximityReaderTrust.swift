import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension ProximityReaderTrustConfiguration {
    func toKMPConfiguration() -> WalletCore.ProximityReaderTrustConfiguration {
        WalletCore.ProximityReaderTrustConfiguration(
            trustAnchors: trustAnchors.map {
                WalletCore.ProximityReaderTrustAnchor(
                    certificateDerBase64Url: $0.certificateDER.base64URLEncodedString(),
                    displayName: $0.displayName
                )
            },
            ricalProviders: ricalProviders.map { configuration in
                WalletCore.ProximityRicalConfiguration(
                    providerId: configuration.providerID,
                    provider: KMPProximityRICALProviderAdapter(configuration.provider),
                    acceptedTypes: configuration.acceptedTypes,
                    providerTrustAnchors: configuration.providerTrustAnchors.map {
                        WalletCore.ProximityRicalProviderTrustAnchor(
                            certificateDerBase64Url: $0.certificateDER.base64URLEncodedString()
                        )
                    },
                    acceptedSignerCertificatePolicyOids: configuration.acceptedSignerCertificatePolicyOIDs,
                    signerRevocationPolicy: configuration.signerRevocationPolicy.toKMPPolicy(),
                    establishReaderTrust: configuration.establishReaderTrust,
                    constraintEvaluator: configuration.constraintEvaluator.map {
                        KMPProximityRICALConstraintEvaluatorAdapter($0)
                    }
                )
            },
            revocationPolicy: revocationPolicy.toKMPPolicy(),
            requiredIacaIssuerCertificateDerBase64Url: requiredIACAIssuerCertificateDER?.base64URLEncodedString()
        )
    }
}

extension ProximityRICALSignerRevocationPolicy {
    func toKMPPolicy() -> any WalletCore.ProximityRicalSignerRevocationPolicy {
        switch self {
        case .notChecked:
            return WalletCore.ProximityRicalSignerRevocationPolicyNotChecked.shared
        case let .check(evaluator):
            return WalletCore.ProximityRicalSignerRevocationPolicyCheck(
                evaluator: KMPProximityRICALSignerRevocationEvaluatorAdapter(evaluator)
            )
        }
    }
}

extension ProximityReaderRevocationPolicy {
    func toKMPPolicy() -> any WalletCore.ProximityReaderRevocationPolicy {
        switch self {
        case .notChecked:
            return WalletCore.ProximityReaderRevocationPolicyNotChecked.shared
        case let .check(evaluator):
            return WalletCore.ProximityReaderRevocationPolicyCheck(
                evaluator: (evaluator as? ProximityCRLRevocationEvaluator)?.evaluator
                    ?? KMPProximityReaderRevocationEvaluatorAdapter(evaluator)
            )
        }
    }
}

protocol KMPBackedProximityReaderTrustEvaluator {
    var kmpReaderTrustEvaluator: any WalletCore.ProximityReaderTrustEvaluator { get }
}

final class KMPProximityCRLFetcherAdapter: WalletCore.ProximityCrlFetcher, @unchecked Sendable {
    init(_ fetcher: any ProximityCRLFetcher) {
        self.fetcher = fetcher
    }

    func __fetch(url: String, maximumBytes: Int32) async throws -> any WalletCore.ProximityCrlFetchResult {
        guard let destination = URL(string: url) else {
            return WalletCore.ProximityCrlFetchResultUnavailable.shared
        }
        switch try await fetcher.fetch(from: destination, maximumBytes: Int(maximumBytes)) {
        case let .available(data):
            guard !data.isEmpty, data.count <= Int(maximumBytes) else {
                return WalletCore.ProximityCrlFetchResultUnavailable.shared
            }
            return WalletCore.ProximityCrlFetchResultAvailable(crlDerBase64Url: data.base64URLEncodedString())
        case .unavailable:
            return WalletCore.ProximityCrlFetchResultUnavailable.shared
        }
    }

    private let fetcher: any ProximityCRLFetcher
}

private final class KMPProximityReaderRevocationEvaluatorAdapter:
    WalletCore.ProximityReaderRevocationEvaluator,
    @unchecked Sendable {
    init(_ evaluator: any ProximityReaderRevocationEvaluator) {
        self.evaluator = evaluator
    }

    func __evaluate(
        evidence: WalletCore.ProximityReaderEvidence
    ) async throws -> any WalletCore.ProximityCertificateRevocationResult {
        switch try await evaluator.evaluate(evidence.toSwiftEvidence()).storage {
        case .good:
            return WalletCore.ProximityCertificateRevocationResultGood.shared
        case let .revoked(reason):
            return WalletCore.ProximityCertificateRevocationResultRevoked(reason: reason)
        case let .indeterminate(reason):
            return WalletCore.ProximityCertificateRevocationResultIndeterminate(reason: reason)
        }
    }

    private let evaluator: any ProximityReaderRevocationEvaluator
}

private final class KMPProximityRICALSignerRevocationEvaluatorAdapter:
    WalletCore.ProximityRicalSignerRevocationEvaluator,
    @unchecked Sendable {
    init(_ evaluator: any ProximityRICALSignerRevocationEvaluator) {
        self.evaluator = evaluator
    }

    func __evaluate(
        evidence: WalletCore.ProximityRicalSignerEvidence
    ) async throws -> any WalletCore.ProximityCertificateRevocationResult {
        let result = try await evaluator.evaluate(
            try ProximityRICALSignerEvidence(
                providerID: evidence.providerId,
                certificateChainDER: try evidence.certificateChainDerBase64Url.map {
                    try decodedBase64URL($0, context: "RICAL signer certificate evidence")
                }
            )
        )
        switch result.storage {
        case .good:
            return WalletCore.ProximityCertificateRevocationResultGood.shared
        case let .revoked(reason):
            return WalletCore.ProximityCertificateRevocationResultRevoked(reason: reason)
        case let .indeterminate(reason):
            return WalletCore.ProximityCertificateRevocationResultIndeterminate(reason: reason)
        }
    }

    private let evaluator: any ProximityRICALSignerRevocationEvaluator
}

private final class KMPProximityRICALProviderAdapter:
    WalletCore.ProximityRicalProvider,
    @unchecked Sendable {
    init(_ provider: any ProximityRICALProvider) {
        self.provider = provider
    }

    func __current() async throws -> any WalletCore.ProximityRicalProviderResult {
        switch try await provider.current().storage {
        case let .available(signedRICAL):
            return WalletCore.ProximityRicalProviderResultAvailable(
                signedRicalBase64Url: signedRICAL.base64URLEncodedString()
            )
        case let .unavailable(reason):
            return WalletCore.ProximityRicalProviderResultUnavailable(reason: reason)
        case let .conflict(reason):
            return WalletCore.ProximityRicalProviderResultConflict(reason: reason)
        }
    }

    private let provider: any ProximityRICALProvider
}

private final class KMPProximityRICALConstraintEvaluatorAdapter:
    WalletCore.ProximityRicalConstraintEvaluator,
    @unchecked Sendable {
    init(_ evaluator: any ProximityRICALConstraintEvaluator) {
        self.evaluator = evaluator
    }

    func __accepts(
        constraints: [WalletCore.ProximityRicalTrustConstraint],
        reader: WalletCore.ProximityReaderEvidence
    ) async throws -> KotlinBoolean {
        let values = try constraints.map { constraint in
            try ProximityRICALTrustConstraint(
                valuesCBOR: try constraint.valuesCborBase64Url.mapValues {
                    try decodedBase64URL($0, context: "RICAL trust constraint")
                }
            )
        }
        return KotlinBoolean(bool: try await evaluator.accepts(values, reader: reader.toSwiftEvidence()))
    }

    private let evaluator: any ProximityRICALConstraintEvaluator
}

extension ProximityReaderEvidence {
    func toKMPEvidence() -> WalletCore.ProximityReaderEvidence {
        WalletCore.ProximityReaderEvidence(
            scope: scope.toKMPScope(),
            certificateChainDerBase64Url: certificateChainDER.map { $0.base64URLEncodedString() },
            authenticationIndex: authenticationIndex
        )
    }
}

private extension WalletCore.ProximityReaderEvidence {
    func toSwiftEvidence() throws -> ProximityReaderEvidence {
        try ProximityReaderEvidence(
            scope: scope.toSwiftScope(),
            certificateChainDER: try certificateChainDerBase64Url.map {
                try decodedBase64URL($0, context: "reader certificate evidence")
            },
            authenticationIndex: Int(authenticationIndex)
        )
    }
}

extension WalletCore.ProximityReaderTrustDecision {
    func toSwiftDecision() throws -> ProximityReaderTrustDecision {
        try ProximityReaderTrustDecision(
            state: state.toSwiftTrust(),
            certificatePath: certificatePath.toSwiftPath(),
            revocation: revocation.toSwiftRevocation(),
            rical: rical.toSwiftRICAL(),
            displayName: displayName,
            reason: reason
        )
    }
}

final class KMPProximityReaderTrustEvaluatorAdapter:
    WalletCore.ProximityReaderTrustEvaluator,
    @unchecked Sendable {
    init(evaluator: any ProximityReaderTrustEvaluator) {
        self.evaluator = evaluator
    }

    func __evaluate(
        evidence: WalletCore.ProximityReaderEvidence
    ) async throws -> WalletCore.ProximityReaderTrustDecision {
        let decision = try await evaluator.evaluate(evidence.toSwiftEvidence())
        return WalletCore.ProximityReaderTrustDecision(
            state: decision.state.toKMPState(),
            certificatePath: decision.certificatePath.toKMPState(),
            revocation: decision.revocation.toKMPState(),
            rical: decision.rical.toKMPState(),
            displayName: decision.displayName,
            reason: decision.reason
        )
    }

    private let evaluator: any ProximityReaderTrustEvaluator
}
#endif
