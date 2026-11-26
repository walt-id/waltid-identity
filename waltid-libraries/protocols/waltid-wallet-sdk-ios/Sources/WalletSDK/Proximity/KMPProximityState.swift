import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension ProximityAction {
    func toKMPAction() -> any WalletCore.ProximityAction {
        switch self {
        case .cancel:
            return WalletCore.ProximityActionCancel()
        case let .decline(reviewID):
            return WalletCore.ProximityActionDecline(reviewId: WalletCore.ProximityReviewId(value: reviewID.value))
        case .retryPrerequisites:
            return WalletCore.ProximityActionRetryPrerequisites()
        case let .reportRemediation(action, result):
            return WalletCore.ProximityActionReportRemediation(
                action: action.toKMPAction(),
                result: result.toKMPResult()
            )
        case let .approve(reviewID, submission):
            return WalletCore.ProximityActionApprove(
                reviewId: WalletCore.ProximityReviewId(value: reviewID.value),
                submission: submission.toKMPSubmission()
            )
        }
    }
}

extension ProximityRemediationAction {
    func toKMPAction() -> WalletCore.ProximityRemediationAction {
        switch self {
        case .requestBluetoothPermission: return .requestBluetoothPermission
        case .requestNearbyWifiPermission: return .requestNearbyWifiPermission
        case .requestLocalNetworkPermission: return .requestLocalNetworkPermission
        case .openApplicationSettings: return .openApplicationSettings
        case .enableBluetooth: return .enableBluetooth
        case .enableWifi: return .enableWifi
        case .enableNFC: return .enableNfc
        case .useSupportedDevice: return .useSupportedDevice
        case .retry: return .retry
        }
    }
}

extension WalletCore.ProximityActionResult {
    func toSwiftResult() -> ProximityActionResult {
        switch onEnum(of: self) {
        case .accepted:
            return .accepted
        case let .rejected(value):
            return .rejected(value.error.toSwiftError())
        }
    }
}

extension WalletCore.ProximityState {
    func toSwiftState() throws -> ProximityState {
        switch onEnum(of: self) {
        case let .checkingPrerequisites(value):
            return try .checkingPrerequisites(value.capabilities.toSwiftCapabilities())
        case let .preparing(value):
            return .preparing(profile: value.profile.toSwiftProfile())
        case let .engagementReady(value):
            return .engagementReady(
                swiftArray(value.engagements, of: WalletCore.ProximityEngagement.self).map {
                    $0.toSwiftEngagement()
                }
            )
        case let .connecting(value):
            return .connecting(
                swiftArray(value.engagements, of: WalletCore.ProximityEngagement.self).map {
                    $0.toSwiftEngagement()
                }
            )
        case let .awaitingRequest(value):
            return .awaitingRequest(exchange: Int(value.exchange))
        case let .reviewRequired(value):
            return .reviewRequired(try value.review.toSwiftReview(), reason: value.reason.toSwiftReason())
        case let .preparationRequired(value):
            return .preparationRequired(try value.plan.toSwiftSharingPlan(), reason: value.reason.toSwiftReason())
        case let .authorizingHolderKey(value):
            return .authorizingHolderKey(value.authorization.toSwiftAuthorization())
        case let .sendingResponse(value):
            return .sendingResponse(exchange: Int(value.exchange))
        case let .awaitingNextRequest(value):
            return .awaitingNextRequest(completedExchanges: Int(value.completedExchanges))
        case let .terminating(value):
            return .terminating(exchange: Int(value.exchange))
        case let .noData(value):
            return .noData(exchange: Int(value.exchange), unsatisfiedRequest: try value.unsatisfiedRequest.toSwiftUnsatisfiedRequest())
        case let .completed(value):
            return .completed(exchanges: Int(value.exchanges), declined: value.declined, receipt: try value.receipt?.toSwiftReceipt())
        case .cancelled:
            return .cancelled
        case let .failed(value):
            return .failed(value.error.toSwiftError())
        }
    }
}

extension WalletCore.ProximityReview {
    func toSwiftReview() throws -> ProximityReview {
        try ProximityReview(
            reviewID: ProximityReviewID(value: reviewId.value),
            exchange: Int(exchange),
            documents: try swiftArray(documents, of: WalletCore.ProximityDocumentReview.self).map {
                try $0.toSwiftReview()
            },
            readerAuthentication: swiftArray(
                readerAuthentication,
                of: WalletCore.ProximityReaderAuthentication.self
            ).map { try $0.toSwiftAuthentication() },
            readerAuthenticationSummary: readerAuthenticationSummary.toSwiftSummary(),
            useCases: swiftArray(useCases, of: WalletCore.ProximityUseCase.self).map {
                $0.toSwiftUseCase()
            },
            applicationAuthorizations: try swiftArray(
                applicationAuthorizations,
                of: WalletCore.ProximityApplicationAuthorization.self
            ).map { try $0.toSwiftAuthorization() }
        )
    }
}

extension WalletCore.ProximityDocumentReview {
    func toSwiftReview() throws -> ProximityDocumentReview {
        try ProximityDocumentReview(
            requestIndex: Int(requestIndex),
            documentType: docType,
            credentialOptions: try swiftArray(
                credentialOptions,
                of: WalletCore.ProximityCredentialOption.self
            ).map { try $0.toSwiftOption() },
            requiredElements: Set(swiftSet(requiredElements, of: WalletCore.ProximityElementReference.self).map {
                try ProximityElementReference(namespace: $0.namespace, elementIdentifier: $0.elementIdentifier)
            })
        )
    }
}

extension WalletCore.ProximityApplicationDocumentRequest {
    func toSwiftRequest() throws -> ProximityApplicationDocumentRequest {
        try ProximityApplicationDocumentRequest(
            requestIndex: Int(requestIndex),
            documentType: docType,
            requestedElements: swiftArray(
                requestedElements,
                of: WalletCore.ProximityRequestedElement.self
            ).map { try $0.toSwiftElement() }
        )
    }
}

internal extension WalletCore.ProximityUnsatisfiedRequest {
    func toSwiftUnsatisfiedRequest() throws -> ProximityUnsatisfiedRequest {
        try ProximityUnsatisfiedRequest(documents: swiftArray(documents, of: WalletCore.ProximityUnsatisfiedDocument.self).map { document in
            try ProximityUnsatisfiedDocument(
                requestIndex: Int(document.requestIndex),
                documentType: document.docType,
                requestedElements: swiftArray(document.requestedElements, of: WalletCore.ProximityRequestedElement.self)
                    .map { try $0.toSwiftElement() }
            )
        })
    }
}

extension WalletCore.ProximityReaderAuthentication {
    func toSwiftAuthentication() throws -> ProximityReaderAuthentication {
        let result: ProximityReaderAuthenticationOutcome
        switch onEnum(of: outcome) {
        case .absent: result = .absent
        case let .malformed(value): result = .malformed(reason: value.reason)
        case let .invalid(value): result = .invalid(reason: value.reason)
        case let .valid(value):
            let trust = value.trust
            result = .valid(try ProximityReaderTrustDecision(
                state: trust.state.toSwiftTrust(),
                certificatePath: trust.certificatePath.toSwiftPath(),
                revocation: trust.revocation.toSwiftRevocation(),
                rical: trust.rical.toSwiftRICAL(),
                displayName: trust.displayName,
                reason: trust.reason
            ))
        }
        return ProximityReaderAuthentication(
            scope: try scope.toSwiftScope(),
            outcome: result,
            authenticationIndex: Int(authenticationIndex)
        )
    }
}

extension WalletCore.ProximityReaderTrustState {
    func toSwiftTrust() -> ProximityReaderTrustState {
        switch self {
        case .notEvaluated: return .notEvaluated
        case .validButUntrusted: return .validButUntrusted
        case .revoked: return .revoked
        case .trusted: return .trusted
        }
    }
}

extension WalletCore.ProximityReaderCertificatePathState {
    func toSwiftPath() -> ProximityReaderCertificatePathState {
        switch self {
        case .notEvaluated: return .notEvaluated
        case .unknownAuthority: return .unknownAuthority
        case .invalid: return .invalid
        case .valid: return .valid
        }
    }
}

extension WalletCore.ProximityReaderRevocationState {
    func toSwiftRevocation() -> ProximityReaderRevocationState {
        switch self {
        case .notChecked: return .notChecked
        case .good: return .good
        case .revoked: return .revoked
        case .indeterminate: return .indeterminate
        }
    }
}

extension WalletCore.ProximityRicalState {
    func toSwiftRICAL() -> ProximityRICALState {
        switch self {
        case .notEvaluated: return .notEvaluated
        case .unavailable: return .unavailable
        case .invalid: return .invalid
        case .noMatchingAuthority: return .noMatchingAuthority
        case .matched: return .matched
        }
    }
}

private extension ProximityHostActionResult {
    func toKMPResult() -> WalletCore.ProximityHostActionResult {
        switch self {
        case .completed: return .completed
        case .cancelled: return .cancelled
        case .failed: return .failed
        }
    }
}

private extension WalletCore.ProximityReviewReason {
    func toSwiftReason() -> ProximityReviewReason {
        switch self {
        case .requestReceived: .requestReceived
        case .preparedSharingChanged: .preparedSharingChanged
        }
    }
}

private extension WalletCore.ProximityEngagement {
    func toSwiftEngagement() -> ProximityEngagement {
        switch onEnum(of: self) {
        case let .qr(value): return .qr(payload: value.payload)
        case .nfc: return .nfc
        }
    }
}

private extension WalletCore.ProximityHolderAuthorization {
    func toSwiftAuthorization() -> ProximityHolderAuthorization {
        ProximityHolderAuthorization(
            reviewID: ProximityReviewID(value: reviewId.value),
            exchange: Int(exchange),
            requests: swiftArray(
                requests,
                of: WalletCore.ProximityHolderAuthorizationRequest.self
            ).map { request in
                ProximityHolderAuthorizationRequest(
                    requestIndex: Int(request.requestIndex),
                    credentialID: request.credentialId,
                    deviceAuthentication: request.deviceAuthentication.toSwiftMethod()
                )
            }
        )
    }
}

private extension WalletCore.ProximityCredentialOption {
    func toSwiftOption() throws -> ProximityCredentialOption {
        try ProximityCredentialOption(
            credentialID: credentialId,
            label: label,
            issuer: issuer,
            validUntil: validUntil.toDate(),
            deviceAuthentication: deviceAuthentication.toSwiftMethod(),
            requestedElements: swiftArray(
                requestedElements,
                of: WalletCore.ProximityRequestedElement.self
            ).map { try $0.toSwiftElement() }
        )
    }
}

private extension WalletCore.ProximityDeviceAuthenticationMethod {
    func toSwiftMethod() -> ProximityDeviceAuthenticationMethod {
        switch self {
        case .signature: return .signature
        case .mac: return .mac
        }
    }
}

private extension WalletCore.ProximityRequestedElement {
    func toSwiftElement() throws -> ProximityRequestedElement {
        try ProximityRequestedElement(
            namespace: namespace,
            elementIdentifier: elementIdentifier,
            intentToRetain: intentToRetain,
            satisfiesRequestedElements: swiftArray(
                satisfiesRequestedElements,
                of: WalletCore.ProximityElementReference.self
            ).map { try ProximityElementReference(namespace: $0.namespace, elementIdentifier: $0.elementIdentifier) }
        )
    }
}

private extension WalletCore.ProximityReaderAuthenticationSummary {
    func toSwiftSummary() -> ProximityReaderAuthenticationSummary {
        switch self {
        case .absent: return .absent
        case .malformed: return .malformed
        case .invalid: return .invalid
        case .revoked: return .revoked
        case .partial: return .partial
        case .validButUntrusted: return .validButUntrusted
        case .trusted: return .trusted
        }
    }
}

private extension WalletCore.ProximityUseCase {
    func toSwiftUseCase() -> ProximityUseCase {
        ProximityUseCase(
            index: Int(index),
            mandatory: mandatory,
            documentRequestIndices: swiftArray(documentRequestIndices, of: KotlinInt.self).map {
                Int($0.int32Value)
            },
            purposeHints: swiftArray(purposeHints, of: WalletCore.ProximityPurposeHint.self).map {
                ProximityPurposeHint(
                    type: $0.type,
                    code: Int($0.code),
                    readerAsserted: $0.readerAsserted
                )
            }
        )
    }
}

private extension WalletCore.ProximityApplicationAuthorization {
    func toSwiftAuthorization() throws -> ProximityApplicationAuthorization {
        let digest = try decodedBase64URL(resultBindingDigestBase64Url, context: "application binding digest")
        return try ProximityApplicationAuthorization(
            profileID: profileId,
            displayTitle: displayTitle,
            details: swiftArray(
                details,
                of: WalletCore.ProximityApplicationAuthorizationDetail.self
            ).map { try ProximityApplicationAuthorizationDetail(id: $0.id, label: $0.label, value: $0.value) },
            compatibleCredentialIDs: swiftSet(compatibleCredentialIds, of: String.self),
            resultBindingDigest: digest,
            deviceSignedElements: try swiftArray(
                deviceSignedElements,
                of: WalletCore.ProximityDeviceSignedElement.self
            ).map {
                try ProximityDeviceSignedElement(
                    credentialID: $0.credentialId,
                    namespace: $0.namespace,
                    elementIdentifier: $0.elementIdentifier,
                    valueCBOR: try decodedBase64URL($0.valueCborBase64Url, context: "device-signed value")
                )
            }
        )
    }
}
#endif
