import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

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
                credentials: requiredBridgeArray(input.credentials, of: WalletCore.ProximityApplicationCredential.self).map {
                    ProximityApplicationCredential(
                        credentialID: $0.credentialId,
                        documentType: $0.docType,
                        label: $0.label
                    )
                },
                requestedDocuments: requiredBridgeArray(
                    input.requestedDocuments,
                    of: WalletCore.ProximityApplicationDocumentRequest.self
                ).map { try $0.toSwiftRequest() },
                readerAuthentication: requiredBridgeArray(
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
