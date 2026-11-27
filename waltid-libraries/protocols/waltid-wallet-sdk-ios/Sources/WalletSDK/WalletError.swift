import Foundation

/// Errors thrown by the public Swift wallet SDK facade.
public enum WalletError: Error, Equatable, Sendable {
    /// The selected credential status warning changed after it was rendered.
    case credentialStatusReviewChanged
    /// The wallet's retained selection or holder has changed since review.
    case presentationReviewChanged
    /// The SDK input could not be parsed or validated.
    case invalidInput(String)

    /// An invalid OpenID4VP request whose protocol error could not be reported safely.
    /// - Parameters:
    ///   - code: The original OpenID4VP error code.
    ///   - message: The original request-validation message.
    ///   - responseSafetyFailure: The reason the error response was not sent.
    case presentationValidation(code: PresentationErrorCode, message: String, responseSafetyFailure: String)

    /// Local wallet storage failed.
    case storage(String)

    /// A protected wallet-key request failed for a stable, actionable reason.
    case keyUseAuthorization(WalletKeyUseAuthorizationFailure)

    /// Payment review or authorization failed for a stable reason.
    case paymentConsent(PaymentConsentFailure, message: String)

    /// The operation was cancelled.
    case cancelled

    /// A transport deadline expired before a result was available.
    case timeout
    /// A remote service could not be reached.
    case network
    /// A remote service returned an HTTP error response.
    case remoteResponse
    /// The requested cryptographic operation failed.
    case crypto
    /// Another owner holds this issuance claim or the issuer outcome is unknown.
    case issuanceOutcomeUncertain
    /// A sent protocol response may have reached the verifier.
    case deliveryUncertain

    /// The SDK encountered an unexpected internal failure.
    case internalFailure(String)
}

/// Stable reasons for refusing payment consent, resolved by the shared wallet core.
public enum PaymentConsentFailure: Equatable, Sendable {
    /// The credential issuer or signature could not be authenticated.
    case untrustedCredential
    /// Required metadata could not be retrieved within the resource limits.
    case metadataUnavailable
    /// The issuer instructions are malformed or incomplete.
    case invalidMetadata
    /// Fetched metadata does not match a supplied integrity reference.
    case integrityMismatch
    /// The schema or inheritance mechanism is not supported.
    case unsupportedSchema
    /// The payment shape, currency or number of authorizations is unsupported.
    case unsupportedPayment
    /// The transaction contains invalid payment values.
    case invalidPayment
    /// No complete preferred-language catalogue is available.
    case missingTranslation
    /// This attempt has no prepared and acknowledged review.
    case consentRequired
    /// The selection or another bound input changed after review.
    case staleConsent
}

extension WalletError: LocalizedError {
    /// A localized, user-readable description of the wallet error.
    public var errorDescription: String? {
        switch self {
        case .invalidInput: return "The wallet request is invalid or has expired."
        case .storage: return "Wallet storage is unavailable."
        case .internalFailure: return "The wallet operation could not be completed."
        case .presentationValidation: return "The request is invalid and the wallet cannot safely report it to the verifier."
        case .paymentConsent: return "Payment authorization could not be completed."
        case .timeout: return "The wallet service did not respond in time."
        case .network: return "The wallet service could not be reached."
        case .remoteResponse: return "The remote service returned an error."
        case .crypto: return "The requested cryptographic operation failed."
        case .issuanceOutcomeUncertain: return "Issuance is already in progress or its outcome is unknown. Check its status."
        case .deliveryUncertain: return "Delivery could not be confirmed. Check the verifier transaction before sharing again."
        case .keyUseAuthorization(let failure):
            return "Wallet key authorization failed: \(failure)"
        case .presentationReviewChanged:
            return "Presentation changed. Review the updated selection before sharing."
        case .credentialStatusReviewChanged:
            return "Credential status changed. Review the updated information before sharing."
        case .cancelled:
            return "The wallet operation was cancelled."
        }
    }

    /// A localized explanation of why the wallet error occurred.
    public var failureReason: String? {
        nil
    }

    /// A localized recovery suggestion for the wallet error.
    public var recoverySuggestion: String? {
        nil
    }

    /// A localized help anchor for documentation related to the wallet error.
    public var helpAnchor: String? {
        nil
    }
}
