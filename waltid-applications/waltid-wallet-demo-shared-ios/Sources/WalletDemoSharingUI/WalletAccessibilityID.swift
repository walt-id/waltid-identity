import Foundation

public enum WalletAccessibilityID {
    public static let status = identifier("status")
    public static let statusDismiss = identifier("statusDismiss")
    public static let statusExpand = identifier("statusExpand")
    public static let settingsButton = identifier("settingsButton")
    public static let settingsDid = identifier("settingsDid")
    public static let settingsDidCopy = identifier("settingsDidCopy")
    public static let settingsKeyId = identifier("settingsKeyId")
    public static let settingsKeyIdCopy = identifier("settingsKeyIdCopy")
    public static let settingsLock = identifier("settingsLock")
    public static let settingsReset = identifier("settingsReset")
    public static let settingsResetConfirm = identifier("settingsResetConfirm")
    public static let copyRawCredential = identifier("copyRawCredential")
    public static let deleteCredential = identifier("deleteCredential")
    public static let pinInput = identifier("pinInput")
    public static let pinConfirmationInput = identifier("pinConfirmationInput")
    public static let pinSubmitButton = identifier("pinSubmitButton")
    public static let pinBiometricToggle = identifier("pinBiometricToggle")
    public static let pinBiometricButton = identifier("pinBiometricButton")
    public static let transactionDataProfilesWarning = identifier("transactionDataProfilesWarning")
    public static let credentialsEmpty = identifier("credentials", "empty")
    public static let credentialDetailsScreen = identifier("credentialDetailsScreen")
    public static let detailsBack = identifier("detailsBack")
    public static let credentialsTabContent = identifier("credentialsTabContent")
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
    public static let presentationRequesterDetailsToggle = identifier("presentationRequesterDetailsToggle")
    public static let presentationRequesterDetails = identifier("presentationRequesterDetails")
    public static let presentationReaderTrustSection = identifier("presentationReaderTrustSection")
    public static let presentationResponseProtectionSection = identifier("presentationResponseProtectionSection")
    public static let presentationTechnicalDetailsSection = identifier("presentationTechnicalDetailsSection")
    public static let verifierTechnicalDetailsToggle = identifier("verifierTechnicalDetailsToggle")
    public static let verifierTechnicalDetails = identifier("verifierTechnicalDetails")

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

    public static func presentationCredentialToggle(_ id: String) -> String {
        identifier("presentationCredentialToggle", id)
    }

    public static func presentationClaimsToggle(_ id: String) -> String {
        identifier("presentationClaimsToggle", id)
    }

    public static func presentationDisclosureToggle(_ id: String) -> String {
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
