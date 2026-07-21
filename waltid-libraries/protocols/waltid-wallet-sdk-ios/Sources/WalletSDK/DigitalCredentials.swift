import Foundation

/// Runtime support advertised by the platform adapter, not a compile-time promise.
public struct DigitalCredentialCapabilities: Equatable, Sendable {
    public let platform: String
    public let platformAvailable: Bool
    public let minimumOSVersion: String
    public let registrationAvailable: Bool
    public let capabilities: [DigitalCredentialCapability]
}

public struct DigitalCredentialCapability: Equatable, Sendable {
    public let protocolIdentifier: String
    public let credentialFormats: [String]
    public let requestProtection: [String]
    public let responseProtection: [String]
    public let supported: Bool
    public let unsupportedReason: String?
}

/// Parsed ISO 18013-7 request Apple exposes before raw request access is granted.
public struct AnnexCParsedRequest: Equatable, Sendable {
    public let documents: [AnnexCDocumentRequest]

    public init(documents: [AnnexCDocumentRequest]) {
        self.documents = documents
    }
}

public struct AnnexCDocumentRequest: Equatable, Sendable {
    public let documentType: String
    public let namespaces: [String: [String]]

    public init(documentType: String, namespaces: [String: [String]]) {
        self.documentType = documentType
        self.namespaces = namespaces
    }
}

public enum ReaderTrust: Equatable, Sendable {
    case notApplicable
    case unverified(reason: String)
    case trusted(certificateSubject: String)
}

/// Consent state retained in the KMP wallet until the raw post-consent request arrives.
public struct AnnexCPresentationPreview: Equatable, Sendable {
    public let requestID: String
    public let verifiedOrigin: String
    public let parsedRequest: AnnexCParsedRequest
    public let credentialOptions: [PresentationCredentialOption]
    public let readerTrust: ReaderTrust
}

/// Encrypted ISO 18013-7 response JSON returned to IdentityDocumentServices.
public struct DigitalCredentialResponse: Equatable, Sendable {
    public let protocolIdentifier: String
    public let dataJSON: String
}
