import Foundation

public enum WalletAccessibilityID {
    public static let status = identifier("status")
    public static let transactionDataProfilesWarning = identifier("transactionDataProfilesWarning")
    public static let credentialsEmpty = identifier("credentials", "empty")
    public static let credentialsTabContent = identifier("credentialsTabContent")
    public static let scanAction = identifier("scanAction")
    public static let scanEmptyAction = identifier("scanEmptyAction")
    public static let scanSheet = identifier("scanSheet")
    public static let scanInput = identifier("scanInput")
    public static let scanSubmit = identifier("scanSubmit")
    public static let scanDismiss = identifier("scanDismiss")
    public static let interactionSheet = identifier("interactionSheet")
    public static let credentialCopyRawData = identifier("credentialCopyRawData")
    public static let credentialDelete = identifier("credentialDelete")
    public static let credentialDeleteConfirm = identifier("credentialDeleteConfirm")
    public static let offerInput = identifier("offerInput")
    public static let offerScanButton = identifier("offerScanButton")
    public static let txCodeInput = identifier("txCodeInput")
    public static let receiveButton = identifier("receiveButton")
    public static let receiveNewButton = identifier("receiveNewButton")
    public static let receiveTabContent = identifier("receiveTabContent")
    public static let offerAcceptButton = identifier("offerAcceptButton")
    public static let offerDeclineButton = identifier("offerDeclineButton")
    public static let offerIssuerSection = identifier("offerIssuerSection")
    public static let offerIssuerDetailsToggle = identifier("offerIssuerDetailsToggle")
    public static let offerIssuerDetails = identifier("offerIssuerDetails")
    public static let offerCredentialsSection = identifier("offerCredentialsSection")
    public static let offerSupportedClaims = identifier("offerSupportedClaims")
    public static let offerAuthorizationSection = identifier("offerAuthorizationSection")
    public static let offerTransactionCodeSection = identifier("offerTransactionCodeSection")
    public static let presentationInput = identifier("presentationInput")
    public static let presentationScanButton = identifier("presentationScanButton")
    public static let presentButton = identifier("presentButton")
    public static let presentationNewButton = identifier("presentationNewButton")
    public static let presentTabContent = identifier("presentTabContent")
    public static let presentationSubmitButton = identifier("presentationSubmitButton")
    public static let presentationRejectButton = identifier("presentationRejectButton")
    public static let presentationError = identifier("presentationError")
    public static let presentationPreparing = identifier("presentationPreparing")
    public static let presentationErrorNotifyButton = identifier("presentationErrorNotifyButton")
    public static let presentationErrorDismissButton = identifier("presentationErrorDismissButton")
    public static let presentationCancelButton = identifier("presentationCancelButton")
    public static let presentationVerifier = identifier("presentationVerifier")
    public static let presentationVerifierSection = identifier("presentationVerifierSection")
    public static let presentationReaderTrustSection = identifier("presentationReaderTrustSection")
    public static let presentationResponseProtectionSection = identifier("presentationResponseProtectionSection")
    public static let presentationTechnicalDetailsSection = identifier("presentationTechnicalDetailsSection")
    public static let verifierTechnicalDetailsToggle = identifier("verifierTechnicalDetailsToggle")
    public static let verifierTechnicalDetails = identifier("verifierTechnicalDetails")
    public static let reviewTechnicalDetailsPage = identifier("reviewTechnicalDetailsPage")
    public static let reviewTechnicalDetailsBack = identifier("reviewTechnicalDetailsBack")

    public static func claim(_ path: String) -> String {
        dynamicIdentifier("claim", path)
    }

    public static func claimImage(_ path: String) -> String {
        dynamicIdentifier("claimImage", path)
    }

    public static func claimGroup(_ title: String) -> String {
        dynamicIdentifier("claimGroup", title)
    }

    public static func claimGroupDisclosure(_ title: String) -> String {
        dynamicIdentifier("claimGroupDisclosure", title)
    }

    public static func credentialCard(_ id: String) -> String {
        identifier("credentialCard", id)
    }

    public static func credentialDetails(_ id: String) -> String {
        identifier("credentialDetails", id)
    }

    public static func credentialOverview(_ id: String) -> String {
        identifier("credentialOverview", id)
    }

    public static func presentationCredential(_ id: String) -> String {
        identifier("presentationCredential", id)
    }

    public static func presentationDisclosureToggle(_ id: String) -> String {
        identifier("presentationDisclosureToggle", id)
    }

    public static func reviewIsland(_ id: String) -> String {
        identifier("reviewIsland", id)
    }

    public static func reviewIslandToggle(_ id: String) -> String {
        identifier("reviewIslandToggle", id)
    }

    public static func reviewIslandTechnicalDetails(_ id: String) -> String {
        identifier("reviewIslandTechnicalDetails", id)
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
