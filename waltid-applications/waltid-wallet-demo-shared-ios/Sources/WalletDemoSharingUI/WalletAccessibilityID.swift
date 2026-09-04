import Foundation

public enum WalletAccessibilityID {
    public static let status = identifier("status")
    public static let statusDismiss = identifier("statusDismiss")
    public static let statusExpand = identifier("statusExpand")
    public static let appTitle = identifier("appTitle")
    public static let settingsButton = identifier("settingsButton")
    public static let settingsAppTitle = identifier("settingsAppTitle")
    public static let settingsDid = identifier("settingsDid")
    public static let settingsDidCopy = identifier("settingsDidCopy")
    public static let settingsKeyId = identifier("settingsKeyId")
    public static let settingsKeyIdCopy = identifier("settingsKeyIdCopy")
    public static let settingsPublicJwk = identifier("settingsPublicJwk")
    public static let settingsPublicJwkCopy = identifier("settingsPublicJwkCopy")
    public static let settingsCredentialSharing = identifier("settingsCredentialSharing")
    public static let settingsShowDcApiPreview = identifier("settingsShowDcApiPreview")
    public static let settingsReaderAuthentication = identifier("settingsReaderAuthentication")
    public static let readerTrustPolicy = identifier("readerTrustPolicy")
    public static let readerTrustAllowUntrusted = identifier("readerTrustAllowUntrusted")
    public static let readerTrustRequireTrusted = identifier("readerTrustRequireTrusted")
    public static let readerTrustImport = identifier("readerTrustImport")
    public static let readerTrustImportProgress = identifier("readerTrustImportProgress")
    public static let readerTrustImportReview = identifier("readerTrustImportReview")
    public static let readerTrustImportConfirm = identifier("readerTrustImportConfirm")
    public static let readerTrustImportCancel = identifier("readerTrustImportCancel")
    public static let readerTrustReset = identifier("readerTrustReset")
    public static let readerTrustResetConfirm = identifier("readerTrustResetConfirm")
    public static let readerTrustError = identifier("readerTrustError")
    public static let settingsLock = identifier("settingsLock")
    public static let settingsReset = identifier("settingsReset")
    public static let settingsResetConfirm = identifier("settingsResetConfirm")
    public static let signingProtectionBiometric = identifier("signingProtectionBiometric")
    public static let signingProtectionNone = identifier("signingProtectionNone")
    public static let signingProtectionConfirm = identifier("signingProtectionConfirm")
    public static let signingProtectionRetry = identifier("signingProtectionRetry")
    public static let signingProtectionProgress = identifier("signingProtectionProgress")
    public static let signingProtectionError = identifier("signingProtectionError")
    public static let signingProtectionAvailability = identifier("signingProtectionAvailability")
    public static let signingProtectionWarning = identifier("signingProtectionWarning")
    public static let signingProtectionWarningDismiss = identifier("signingProtectionWarningDismiss")
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
    public static let detailsMenu = identifier("detailsMenu")
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
    public static let presentationClaimsDialog = identifier("presentationClaimsDialog")
    public static let presentationClaimsClose = identifier("presentationClaimsClose")
    public static let presentationRequesterDetailsToggle = identifier("presentationRequesterDetailsToggle")
    public static let presentationRequesterDetails = identifier("presentationRequesterDetails")
    public static let presentationReaderTrustSection = identifier("presentationReaderTrustSection")
    public static let presentationResponseProtectionSection = identifier("presentationResponseProtectionSection")
    public static let presentationTechnicalDetailsSection = identifier("presentationTechnicalDetailsSection")
    public static let verifierTechnicalDetailsToggle = identifier("verifierTechnicalDetailsToggle")
    public static let verifierTechnicalDetails = identifier("verifierTechnicalDetails")
    public static let proximityStartButton = identifier("proximityStartButton")
    public static let proximityScreen = identifier("proximityScreen")
    public static let proximityStatus = identifier("proximityStatus")
    public static let proximityQRCode = identifier("proximityQRCode")
    public static let proximityReview = identifier("proximityReview")
    public static let proximityReaderSection = identifier("proximityReaderSection")
    public static let proximityReaderDetailsToggle = identifier("proximityReaderDetailsToggle")
    public static let proximityReaderDetails = identifier("proximityReaderDetails")
    public static let proximityContinueAfterResponse = identifier("proximityContinueAfterResponse")
    public static let proximityApproveButton = identifier("proximityApproveButton")
    public static let proximityDeclineButton = identifier("proximityDeclineButton")
    public static let proximityCancelButton = identifier("proximityCancelButton")
    public static let proximityDoneButton = identifier("proximityDoneButton")
    public static let proximityRetryButton = identifier("proximityRetryButton")
    public static let proximityError = identifier("proximityError")

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

    public static func proximityCredential(requestIndex: Int, credentialID: String) -> String {
        identifier("proximityCredential", String(requestIndex), credentialID.identifierSegment)
    }

    public static func proximityElement(
        requestIndex: Int,
        namespace: String,
        elementIdentifier: String
    ) -> String {
        identifier(
            "proximityElement",
            String(requestIndex),
            "\(namespace).\(elementIdentifier)".identifierSegment
        )
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
