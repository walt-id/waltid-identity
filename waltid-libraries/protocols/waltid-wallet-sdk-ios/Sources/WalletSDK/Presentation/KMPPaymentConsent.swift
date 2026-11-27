import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension Waltid_openid4vc_walletPreparedPaymentConsent {
    func toSwiftPaymentConsent() -> PaymentConsent {
        PaymentConsent(revision: revision, locale: payment.locale, title: payment.title, securityHint: payment.securityHint,
            affirmativeAction: payment.affirmativeAction, denialAction: payment.denialAction,
            requiresUnsignedRequestWarning: requiresUnsignedRequestWarning,
            fields: swiftArray(payment.fields, of: Waltid_openid4vc_walletPaymentConsentField.self).map { field in
                let placement: PaymentConsentFieldPlacement
                switch field.placement {
                case .prominent: placement = .prominent
                case .main: placement = .main
                case .details: placement = .details
                case .omitted: placement = .omitted
                }
                return PaymentConsentField(label: field.label, description: field.descriptionText, value: field.value,
                    placement: placement, path: swiftArray(field.path, of: String.self))
            })
    }
}

extension Waltid_openid4vc_walletPaymentConsentFailure {
    func toSwiftPaymentFailure() -> PaymentConsentFailure {
        switch self {
        case .untrustedCredential: return .untrustedCredential
        case .metadataUnavailable: return .metadataUnavailable
        case .invalidMetadata: return .invalidMetadata
        case .integrityMismatch: return .integrityMismatch
        case .unsupportedSchema: return .unsupportedSchema
        case .unsupportedPayment: return .unsupportedPayment
        case .invalidPayment: return .invalidPayment
        case .missingTranslation: return .missingTranslation
        case .consentRequired: return .consentRequired
        case .staleConsent: return .staleConsent
        }
    }
}
#endif
