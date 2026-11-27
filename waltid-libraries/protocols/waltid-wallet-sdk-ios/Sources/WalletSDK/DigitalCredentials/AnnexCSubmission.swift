import Foundation

/// Platform-supplied Annex C bytes and the credential choices approved for one retained preview.
public struct AnnexCSubmission: Equatable, Sendable {
    /// Wallet-bound request handle copied from the preview.
    public let requestID: String
    /// Origin verified by the platform and bound to the preview.
    public let verifiedOrigin: String
    /// Original post-consent device request in base64url encoding.
    public let deviceRequestBase64URL: String
    /// Original post-consent encryption information in base64url encoding.
    public let encryptionInfoBase64URL: String
    /// Credentials approved for the requested documents.
    public let selectedCredentialOptions: [PresentationCredentialSelection]

    /// Combines a retained preview with the platform's post-consent request.
    /// - Parameters:
    ///   - preview: Preview whose origin and request handle were reviewed.
    ///   - deviceRequestBase64URL: Raw device request returned after platform consent.
    ///   - encryptionInfoBase64URL: Encryption information from the same platform request.
    ///   - selectedCredentialOptions: Credentials the holder approved.
    public init(
        preview: AnnexCPresentationPreview,
        deviceRequestBase64URL: String,
        encryptionInfoBase64URL: String,
        selectedCredentialOptions: [PresentationCredentialSelection]
    ) {
        self.requestID = preview.requestID
        self.verifiedOrigin = preview.verifiedOrigin
        self.deviceRequestBase64URL = deviceRequestBase64URL
        self.encryptionInfoBase64URL = encryptionInfoBase64URL
        self.selectedCredentialOptions = selectedCredentialOptions
    }
}
