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

    fileprivate var kmpReaderTrustEvaluator: any WalletCore.ProximityReaderTrustEvaluator {
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

    fileprivate let evaluator: WalletCore.ProximityCrlRevocationEvaluator
}

extension ProximityConfiguration {
    func toKMPConfiguration() -> WalletCore.ProximityConfiguration {
        WalletCore.ProximityConfiguration(
            profile: profile.toKMPProfile(),
            session: session.toKMPConfiguration(),
            readerPolicy: readerPolicy.toKMPPolicy(),
            deviceAuthenticationPolicy: deviceAuthenticationPolicy.toKMPPolicy(),
            readerTrustEvaluator: readerTrustEvaluator.map { evaluator in
                (evaluator as? any KMPBackedProximityReaderTrustEvaluator)?.kmpReaderTrustEvaluator ??
                    KMPProximityReaderTrustEvaluatorAdapter(evaluator: evaluator)
            } ?? WalletCore.UnconfiguredProximityReaderTrustEvaluator.shared,
            credentialStatusEvaluator: credentialStatusEvaluator.map {
                KMPProximityCredentialStatusEvaluatorAdapter(evaluator: $0)
            } ?? WalletCore.UnconfiguredProximityCredentialStatusEvaluator.shared,
            applicationProfiles: WalletCore.ProximityApplicationProfileRegistry(
                profiles: applicationProfiles.map(KMPProximityApplicationProfileAdapter.init)
            ),
            maximumMessageBytes: Int32(maximumMessageBytes),
            approval: approval.toKMPApproval()
        )
    }
}

extension WalletCore.ProximitySharingPlan {
    func toSwiftSharingPlan() throws -> ProximitySharingPlan {
        .init(review: try review.toSwiftReview(), expiresAt: expiresAt.toDate(),
              readerCertificateSHA256: readerCertificateSha256, bridge: KMPSharingPlanBridge(self))
    }
}

extension WalletCore.ProximitySharingReceipt {
    func toSwiftReceipt() throws -> ProximitySharingReceipt {
        .init(review: try review.toSwiftReview(), submission: try submission.toSwiftSubmission(),
              approvalTiming: approvalTiming == .beforeConnection ? .beforeConnection : .duringConnection,
              completedAt: completedAt.toDate())
    }
}

extension ProximitySubmission {
    func toKMPSubmission() -> WalletCore.ProximitySubmission {
        .init(documents: documents.map { document in
            WalletCore.ProximityDocumentSubmission(requestIndex: Int32(document.requestIndex), credentialId: document.credentialID,
                disclosedElements: Set(document.disclosedElements.map {
                    WalletCore.ProximityElementReference(namespace: $0.namespace, elementIdentifier: $0.elementIdentifier)
                }))
        }, continueAfterResponse: continueAfterResponse)
    }
}

extension WalletCore.ProximitySubmission {
    func toSwiftSubmission() throws -> ProximitySubmission {
        try .init(documents: swiftArray(documents, of: WalletCore.ProximityDocumentSubmission.self).map { document in
            try ProximityDocumentSubmission(requestIndex: Int(document.requestIndex), credentialID: document.credentialId,
                disclosedElements: Set(try swiftSet(document.disclosedElements, of: WalletCore.ProximityElementReference.self).map {
                    try ProximityElementReference(namespace: $0.namespace, elementIdentifier: $0.elementIdentifier)
                }))
        }, continueAfterResponse: continueAfterResponse)
    }
}

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

private protocol KMPBackedProximityReaderTrustEvaluator {
    var kmpReaderTrustEvaluator: any WalletCore.ProximityReaderTrustEvaluator { get }
}

private extension ProximityApproval {
    func toKMPApproval() -> any WalletCore.ProximityApproval {
        switch self {
        case .askEachTime: return WalletCore.ProximityApprovalAskEachTime.shared
        case .prepareBeforeSharing: return WalletCore.ProximityApprovalPrepareBeforeSharing.shared
        case .prepared(let sharing):
            guard let bridge = sharing.bridge as? KMPPreparedSharingBridge else {
                preconditionFailure("Prepared sharing must be issued by this wallet SDK")
            }
            return WalletCore.ProximityApprovalPrepared(sharing: bridge.sharing)
        }
    }
}

private final class KMPSharingPlanBridge: ProximitySharingPlanBridge, @unchecked Sendable {
    let plan: WalletCore.ProximitySharingPlan
    init(_ plan: WalletCore.ProximitySharingPlan) { self.plan = plan }
    var isExpired: Bool { plan.isExpired }

    func approveStatusReviewed(_ submission: ProximitySubmission, reviewedStatus: [String: String]) throws -> ProximityPreparationResult {
        switch onEnum(of: plan.approveStatusReviewed(submission: submission.toKMPSubmission(), reviewedStatus: reviewedStatus)) {
        case .prepared(let value): return .prepared(try value.sharing.toSwiftPreparedSharing())
        case .rejected(let value): return .rejected(value.error.toSwiftError())
        }
    }

    func approve(_ submission: ProximitySubmission) throws -> ProximityPreparationResult {
        switch onEnum(of: plan.approve(submission: submission.toKMPSubmission())) {
        case .prepared(let value): return .prepared(try value.sharing.toSwiftPreparedSharing())
        case .rejected(let value): return .rejected(value.error.toSwiftError())
        }
    }
}

private final class KMPPreparedSharingBridge: ProximityPreparedSharingBridge, @unchecked Sendable {
    let sharing: WalletCore.ProximityPreparedSharing
    init(_ sharing: WalletCore.ProximityPreparedSharing) { self.sharing = sharing }
    var remainingSeconds: Int { Int(sharing.remainingSeconds) }
    func revoke() async { try? await sharing.revoke() }
}

private extension WalletCore.ProximityPreparedSharing {
    func toSwiftPreparedSharing() throws -> ProximityPreparedSharing {
        .init(review: try review.toSwiftReview(), submission: try submission.toSwiftSubmission(),
              expiresAt: expiresAt.toDate(), bridge: KMPPreparedSharingBridge(self))
    }
}

private final class KMPProximityCRLFetcherAdapter: WalletCore.ProximityCrlFetcher, @unchecked Sendable {
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

private extension ProximityReaderEvidence {
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

private extension WalletCore.ProximityReaderTrustDecision {
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

private final class KMPProximityReaderTrustEvaluatorAdapter:
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

private final class KMPProximityCredentialStatusEvaluatorAdapter:
    WalletCore.ProximityCredentialStatusEvaluator,
    @unchecked Sendable {
    init(evaluator: any ProximityCredentialStatusEvaluator) {
        self.evaluator = evaluator
    }

    func __evaluate(
        credential: WalletCore.ProximityCredentialStatusInput
    ) async throws -> WalletCore.ProximityCredentialStatus {
        let status = try await evaluator.evaluate(
            ProximityCredentialStatusInput(
                credentialID: credential.credentialId,
                documentType: credential.docType,
                issuer: credential.issuer,
                validFrom: credential.validFrom.toDate(),
                validUntil: credential.validUntil.toDate()
            )
        )
        switch status {
        case .valid: return .valid
        case .revoked: return .revoked
        case .indeterminate: return .indeterminate
        }
    }

    private let evaluator: any ProximityCredentialStatusEvaluator
}

private final class KMPProximityApplicationProfileAdapter:
    WalletCore.ProximityApplicationProfile,
    @unchecked Sendable {
    init(_ profile: any ProximityApplicationProfile) {
        self.profile = profile
    }

    var id: String { profile.id }

    func __evaluate(
        input: WalletCore.ProximityApplicationProfileInput
    ) async throws -> any WalletCore.ProximityApplicationProfileResult {
        guard let deviceRequest = Data(base64URLEncoded: input.deviceRequestBase64Url) else {
            throw WalletError.internalFailure("Wallet core returned an invalid application-profile request")
        }
        let result = try await profile.evaluate(
            ProximityApplicationProfileInput(
                deviceRequest: deviceRequest,
                credentials: swiftArray(input.credentials, of: WalletCore.ProximityApplicationCredential.self).map {
                    ProximityApplicationCredential(
                        credentialID: $0.credentialId,
                        documentType: $0.docType,
                        label: $0.label
                    )
                },
                requestedDocuments: swiftArray(
                    input.requestedDocuments,
                    of: WalletCore.ProximityApplicationDocumentRequest.self
                ).map { try $0.toSwiftRequest() },
                readerAuthentication: swiftArray(
                    input.readerAuthentication,
                    of: WalletCore.ProximityReaderAuthentication.self
                ).map { try $0.toSwiftAuthentication() }
            )
        )
        switch result {
        case .notRecognized:
            return WalletCore.ProximityApplicationProfileResultNotRecognized()
        case let .rejected(reason):
            return WalletCore.ProximityApplicationProfileResultRejected(reason: reason)
        case let .recognized(authorization):
            return WalletCore.ProximityApplicationProfileResultRecognized(
                authorization: WalletCore.ProximityApplicationAuthorization(
                    profileId: authorization.profileID,
                    displayTitle: authorization.displayTitle,
                    details: authorization.details.map {
                        WalletCore.ProximityApplicationAuthorizationDetail(
                            id: $0.id,
                            label: $0.label,
                            value: $0.value
                        )
                    },
                    compatibleCredentialIds: authorization.compatibleCredentialIDs,
                    resultBindingDigestBase64Url: authorization.resultBindingDigest.base64URLEncodedString(),
                    deviceSignedElements: authorization.deviceSignedElements.map {
                        WalletCore.ProximityDeviceSignedElement(
                            credentialId: $0.credentialID,
                            namespace: $0.namespace,
                            elementIdentifier: $0.elementIdentifier,
                            valueCborBase64Url: $0.valueCBOR.base64URLEncodedString()
                        )
                    }
                )
            )
        }
    }

    private let profile: any ProximityApplicationProfile
}
#endif
