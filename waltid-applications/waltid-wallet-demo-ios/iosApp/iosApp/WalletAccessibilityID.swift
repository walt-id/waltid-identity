import Foundation

enum WalletAccessibilityID {
    static let status = identifier("status")
    static let transactionDataProfilesWarning = identifier("transactionDataProfilesWarning")
    static let credentialsEmpty = identifier("credentials", "empty")
    static let credentialsTabContent = identifier("credentialsTabContent")
    static let offerInput = identifier("offerInput")
    static let offerScanButton = identifier("offerScanButton")
    static let txCodeInput = identifier("txCodeInput")
    static let receiveButton = identifier("receiveButton")
    static let receiveNewButton = identifier("receiveNewButton")
    static let receiveTabContent = identifier("receiveTabContent")
    static let offerAcceptButton = identifier("offerAcceptButton")
    static let offerDeclineButton = identifier("offerDeclineButton")
    static let offerIssuerSection = identifier("offerIssuerSection")
    static let offerCredentialsSection = identifier("offerCredentialsSection")
    static let offerSupportedClaims = identifier("offerSupportedClaims")
    static let offerTransactionCodeSection = identifier("offerTransactionCodeSection")
    static let presentationInput = identifier("presentationInput")
    static let presentationScanButton = identifier("presentationScanButton")
    static let presentButton = identifier("presentButton")
    static let presentationNewButton = identifier("presentationNewButton")
    static let presentTabContent = identifier("presentTabContent")
    static let presentationSubmitButton = identifier("presentationSubmitButton")
    static let presentationRejectButton = identifier("presentationRejectButton")
    static let presentationError = identifier("presentationError")
    static let presentationErrorNotifyButton = identifier("presentationErrorNotifyButton")
    static let presentationErrorDismissButton = identifier("presentationErrorDismissButton")
    static let presentationVerifier = identifier("presentationVerifier")
    static let presentationCancelButton = identifier("presentationCancelButton")
    static let presentationVerifierSection = identifier("presentationVerifierSection")
    static let presentationResponseProtectionSection = identifier("presentationResponseProtectionSection")
    static let presentationTechnicalDetailsSection = identifier("presentationTechnicalDetailsSection")
    static let verifierTechnicalDetailsToggle = identifier("verifierTechnicalDetailsToggle")
    static let verifierTechnicalDetails = identifier("verifierTechnicalDetails")

    static func claim(_ path: String) -> String {
        dynamicIdentifier("claim", path)
    }

    static func claimImage(_ path: String) -> String {
        dynamicIdentifier("claimImage", path)
    }

    static func claimGroup(_ title: String) -> String {
        dynamicIdentifier("claimGroup", title)
    }

    static func claimGroupDisclosure(_ title: String) -> String {
        dynamicIdentifier("claimGroupDisclosure", title)
    }

    static func credentialCard(_ id: String) -> String {
        identifier("credentialCard", id)
    }

    static func credentialDetails(_ id: String) -> String {
        identifier("credentialDetails", id)
    }

    static func credentialOverview(_ id: String) -> String {
        identifier("credentialOverview", id)
    }

    static func presentationCredential(_ id: String) -> String {
        identifier("presentationCredential", id)
    }

    static func presentationDisclosureToggle(_ id: String) -> String {
        identifier("presentationDisclosureToggle", id)
    }

    private static func identifier(_ segments: String...) -> String {
        ([namespace] + segments).joined(separator: ".")
    }

    private static func dynamicIdentifier(_ kind: String, _ rawValue: String) -> String {
        identifier(kind, rawValue.identifierSegment)
    }

    private static let namespace = "wallet"
}

private extension String {
    var identifierSegment: String {
        map { $0.isLetter || $0.isNumber ? String($0) : "_" }.joined()
    }
}
