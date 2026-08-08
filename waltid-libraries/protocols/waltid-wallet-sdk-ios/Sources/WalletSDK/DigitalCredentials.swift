import Foundation
#if os(iOS)
import IdentityDocumentServices
#endif
#if canImport(WalletCore) && os(iOS)
import WalletCore
#endif

/// Shared runtime-status bridge used by the SDK and an IdentityDocument provider extension.
///
/// Only Swift can query `IdentityDocumentProviderRegistrationStore`, and only the wallet core knows
/// how the status is stored. So this translates Apple's status into the wallet core's status type and
/// hands it over; neither the App Group key nor the stored spelling of a status is written here.
public enum DigitalCredentialRegistrationStorage {
    #if canImport(WalletCore) && os(iOS)
    /// App Group key the wallet core publishes the registered mdoc document types under.
    public static var documentTypesKey: String { IosIdentityDocumentRegistry.companion.DOCUMENT_TYPES_KEY }

    /// App Group key the wallet core publishes the current logical registry identifier under.
    public static var registryIDKey: String { IosIdentityDocumentRegistry.companion.REGISTRY_ID_KEY }

    @available(iOS 26.0, *)
    public static func persist(
        status: IdentityDocumentProviderRegistrationStore.Status,
        appGroupIdentifier: String
    ) {
        IosIdentityDocumentRegistry.companion.reportRegistrationStatus(
            appGroupIdentifier: appGroupIdentifier,
            status: status.walletRegistrationStatus
        )
    }
    #endif
}

#if canImport(WalletCore) && os(iOS)
@available(iOS 26.0, *)
private extension IdentityDocumentProviderRegistrationStore.Status {
    /// An unrecognized future status is reported as unsupported: the conservative choice, since
    /// registration proceeds only for `.authorized`.
    var walletRegistrationStatus: IosIdentityDocumentRegistrationStatus {
        switch self {
        case .authorized: .authorized
        case .notDetermined: .notDetermined
        case .notAuthorized: .notAuthorized
        case .notSupported: .notSupported
        @unknown default: .notSupported
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
///
/// Only ``trusted`` means the reader was identified, and it requires both a valid signature and an
/// accepting application trust policy. A request whose reader authentication fails cryptographic
/// verification produces no result at all - it is rejected - so every case here describes a request
/// that is still processable, differing in why the reader is not identified.
public enum ReaderTrust: Equatable, Sendable {
    /// The protocol carries no reader authentication, as with the OpenID4VP Digital Credentials API.
    case notApplicable
    /// The request supports reader authentication but carried none, so the reader is anonymous.
    case notAuthenticated
    /// Not checked yet: IdentityDocumentServices withholds the raw request until the user consents.
    ///
    /// The signature is verified at submission, before any credential data is released, but that is
    /// too late for a consent dialog to name the reader.
    case pendingRawRequest
    /// The signature is cryptographically valid, but no application trust policy accepts the reader.
    ///
    /// Not a verification failure. It means the wallet has no basis for telling the user who the
    /// reader is, which is also what a wallet with no configured trust policy always reports.
    case untrusted(reason: String)
    /// Reader authentication is cryptographically valid and an application trust policy accepted it.
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
