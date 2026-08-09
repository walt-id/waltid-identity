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
    /// Desired mdoc registrations of the wallet that currently owns `appGroupIdentifier`.
    ///
    /// This is the wallet's intent, not Apple's state: it is published regardless of provider
    /// authorization so that a later authorization can be reconciled without reissuing credentials.
    ///
    /// The three outcomes are kept apart rather than flattened to a list, because only a published
    /// projection authorizes removing registrations; see ``DesiredRegistrationProjection``.
    public static func desiredRegistrations(
        appGroupIdentifier: String
    ) -> DesiredRegistrationProjection {
        let result = IosIdentityDocumentRegistry.companion
            .readDesiredRegistrations(appGroupIdentifier: appGroupIdentifier)
        if result is IosIdentityDocumentProjectionResultMissing { return .missing }
        if let malformed = result as? IosIdentityDocumentProjectionResultMalformed {
            return .malformed(reason: malformed.reason)
        }
        if let published = result as? IosIdentityDocumentProjectionResultPublished {
            return .published(
                walletID: published.state.walletId,
                registrations: published.state.registrations
            )
        }
        // A future case this SDK build does not know about says nothing about the wallet's intent,
        // so it must not authorize mutating Apple's store either.
        return .malformed(reason: "Unrecognized identity document projection state")
    }

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
/// What the shared App Group says about the active wallet's desired registrations.
///
/// Deliberately not an array: reconciliation *removes* registrations, and removing one stops the
/// platform offering a document that may still be presentable. Only ``published(walletID:registrations:)``
/// is a statement of intent; the other two mean the intent is unknown and Apple's store must be left
/// exactly as it is.
///
/// Not `Sendable`: the published payload is the wallet core's own projection record, a Kotlin object.
/// Callers map it into their own value type before the first `await`, as reconciliation does.
public enum DesiredRegistrationProjection {
    /// No wallet has published a projection yet - a fresh install, or a build without the App Group.
    case missing
    /// A projection exists but could not be decoded, so nothing can be concluded from it.
    ///
    /// - Parameter reason: Decoder message, for the log that is the only trace of this on a device.
    case malformed(reason: String)
    /// The active wallet's authoritative desired state.
    ///
    /// An empty `registrations` array is authoritative too: the wallet holds no presentable mdoc
    /// credential and its managed registrations must be removed.
    ///
    /// - Parameters:
    ///   - walletID: Wallet these registrations belong to; the extension must open this wallet.
    ///   - registrations: One desired registration per presentable mdoc credential.
    case published(walletID: String, registrations: [IosIdentityDocumentProjectionRecord])
}

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
/// Only ``trusted(certificateSubject:)`` means the reader was identified, and it requires both a
/// valid signature and an accepting application trust policy. A request whose reader authentication
/// fails cryptographic verification produces no result at all - it is rejected - so every case here
/// describes a request that is still processable, differing in why the reader is not identified.
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
