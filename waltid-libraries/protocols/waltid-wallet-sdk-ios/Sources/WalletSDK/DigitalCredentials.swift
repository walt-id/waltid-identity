import Foundation
#if os(iOS)
import IdentityDocumentServices
#endif

/// Shared runtime-status bridge used by the SDK and an IdentityDocument provider extension.
public enum DigitalCredentialRegistrationStorage {
    /// App Group user-defaults key containing the latest provider-registration status.
    public static let registrationStatusKey = "id.walt.wallet.identity-document-registration-status"

    #if os(iOS)
    @available(iOS 26.0, *)
    public static func persist(
        status: IdentityDocumentProviderRegistrationStore.Status,
        appGroupIdentifier: String
    ) {
        UserDefaults(suiteName: appGroupIdentifier)?.set(
            status.walletStorageValue,
            forKey: registrationStatusKey
        )
    }
    #endif
}

#if os(iOS)
@available(iOS 26.0, *)
private extension IdentityDocumentProviderRegistrationStore.Status {
    var walletStorageValue: String {
        switch self {
        case .authorized: "authorized"
        case .notDetermined: "notDetermined"
        case .notAuthorized: "notAuthorized"
        case .notSupported: "notSupported"
        @unknown default: "notSupported"
        }
    }
}
#endif

/// Runtime support advertised by the platform adapter, not a compile-time promise.
public struct DigitalCredentialCapabilities: Equatable, Sendable {
    /// Platform that supplied this capability snapshot.
    public let platform: String
    /// Whether the platform APIs required for digital credentials are available.
    public let platformAvailable: Bool
    /// Minimum OS version required by the platform adapter.
    public let minimumOSVersion: String
    /// Whether the wallet can register credential metadata with the platform.
    public let registrationAvailable: Bool
    /// Protocol-specific capabilities supported by the platform adapter.
    public let capabilities: [DigitalCredentialCapability]
}

/// Protocol-specific digital credential capability advertised by the wallet.
public struct DigitalCredentialCapability: Equatable, Sendable {
    /// Platform protocol identifier.
    public let protocolIdentifier: String
    /// Credential formats accepted for this protocol.
    public let credentialFormats: [String]
    /// Request protection mechanisms supported by this protocol.
    public let requestProtection: [String]
    /// Response protection mechanisms supported by this protocol.
    public let responseProtection: [String]
    /// Whether this protocol is usable on the current platform.
    public let supported: Bool
    /// Reason the protocol is unavailable when ``supported`` is `false`.
    public let unsupportedReason: String?
}

/// Parsed ISO 18013-7 request Apple exposes before raw request access is granted.
public struct AnnexCParsedRequest: Equatable, Sendable {
    /// Document requests parsed from Apple's pre-consent request.
    public let documents: [AnnexCDocumentRequest]

    /// Creates a parsed Annex C request.
    ///
    /// - Parameter documents: Document requests included in the presentation request.
    public init(documents: [AnnexCDocumentRequest]) {
        self.documents = documents
    }
}

/// Requested document type and namespaces from an Annex C presentation request.
public struct AnnexCDocumentRequest: Equatable, Sendable {
    /// ISO mdoc document type requested by the verifier.
    public let documentType: String
    /// Requested elements grouped by namespace.
    public let namespaces: [String: [String]]

    /// Creates an Annex C document request.
    ///
    /// - Parameters:
    ///   - documentType: ISO mdoc document type requested by the verifier.
    ///   - namespaces: Requested elements grouped by namespace.
    public init(documentType: String, namespaces: [String: [String]]) {
        self.documentType = documentType
        self.namespaces = namespaces
    }
}

/// Reader-authentication trust result for an Annex C presentation request.
public enum ReaderTrust: Equatable, Sendable {
    /// Reader authentication does not apply to this request.
    case notApplicable
    /// Reader authentication could not be verified.
    case unverified(reason: String)
    /// Reader authentication was verified against the configured trust policy.
    case trusted(certificateSubject: String)
}

/// Consent state retained in the KMP wallet until the raw post-consent request arrives.
public struct AnnexCPresentationPreview: Equatable, Sendable {
    /// Wallet-bound handle used to submit the retained preview.
    public let requestID: String
    /// Verified origin bound to the presentation request.
    public let verifiedOrigin: String
    /// Parsed request that the raw post-consent request must match.
    public let parsedRequest: AnnexCParsedRequest
    /// Credentials eligible for user consent.
    public let credentialOptions: [PresentationCredentialOption]
    /// Result of reader-authentication trust evaluation.
    public let readerTrust: ReaderTrust
}

/// Encrypted ISO 18013-7 response JSON returned to IdentityDocumentServices.
public struct DigitalCredentialResponse: Equatable, Sendable {
    /// Platform protocol identifier for the encrypted response.
    public let protocolIdentifier: String
    /// Protocol response encoded as JSON.
    public let dataJSON: String
}
