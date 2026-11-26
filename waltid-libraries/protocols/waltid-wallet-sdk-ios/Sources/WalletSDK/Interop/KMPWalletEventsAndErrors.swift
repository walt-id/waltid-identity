import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension MobileWalletEvent {
    func toSwiftEvent() -> WalletEvent {
        switch self {
        case .issuanceOfferResolved: return .issuanceOfferResolved
        case .issuanceAttestationObtained: return .issuanceAttestationObtained
        case .issuanceTokenObtained: return .issuanceTokenObtained
        case .issuanceProofSigned: return .issuanceProofSigned
        case .issuanceCredentialReceived: return .issuanceCredentialReceived
        case .issuanceDeferred: return .issuanceDeferred
        case .issuanceCredentialStored: return .issuanceCredentialStored
        case .issuanceCompleted: return .issuanceCompleted
        case .issuanceFailed: return .issuanceFailed
        case .presentationRequestParsed: return .presentationRequestParsed
        case .presentationCredentialsSelected: return .presentationCredentialsSelected
        case .presentationSigned: return .presentationSigned
        case .presentationResponsePrepared: return .presentationResponsePrepared
        case .presentationSubmitted: return .presentationSubmitted
        case .presentationCompleted: return .presentationCompleted
        case .presentationFailed: return .presentationFailed
        }
    }
}

extension WalletBridgeError {
    func toSwiftWalletError() -> WalletError {
        switch category {
        case .presentationReviewChanged:
            return .presentationReviewChanged
        case .credentialStatusReviewChanged:
            return .credentialStatusReviewChanged
        case .presentationValidation:
            guard let presentationValidationFailure else {
                return .internalFailure("Presentation validation error did not include a failure reason")
            }
            return .presentationValidation(
                code: presentationValidationFailure.errorCode.toSwiftErrorCode(),
                message: presentationValidationFailure.message,
                responseSafetyFailure: presentationValidationFailure.responseSafetyFailure
            )
        case .invalidInput:
            return .invalidInput(message)
        case .network:
            return .network(message)
        case .issuer:
            return .issuer(message)
        case .verifier:
            return .verifier(message)
        case .storage:
            return .storage(message)
        case .crypto:
            return .crypto(message)
        case .credentialNotFound:
            return .credentialNotFound(message)
        case .paymentConsent:
            guard let paymentConsentFailure else {
                return .internalFailure("Payment consent error did not include a failure reason")
            }
            return .paymentConsent(paymentConsentFailure.toSwiftPaymentFailure(), message: message)
        case .authorization:
            guard let authorizationFailure else {
                return .internalFailure("Authorization error did not include a failure reason")
            }
            return .keyUseAuthorization(authorizationFailure.toSwiftAuthorizationFailure())
        case .cancelled:
            return .cancelled
        case .internalFailure:
            return .internalFailure(message)
        }
    }
}
#endif
