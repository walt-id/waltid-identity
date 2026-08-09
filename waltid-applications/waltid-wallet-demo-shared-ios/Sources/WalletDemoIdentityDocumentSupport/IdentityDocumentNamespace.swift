import Foundation
import WalletSDK

/// Cross-process identity of one demo wallet: App Group, shared Keychain group, and doctypes.
///
/// Every value here has to be identical in the host app and its provider extension, otherwise the
/// two processes open different databases or cannot load the same signing key and the wallet only
/// *looks* shared. The two demos deliberately use different namespaces so that installing both does
/// not make them share credentials.
public struct IdentityDocumentNamespace: Sendable {
    /// App Group holding the encrypted wallet database and the desired-registration state.
    public let appGroupIdentifier: String

    /// Keychain access group, without the `AppIdentifierPrefix`, shared by app and extension.
    ///
    /// Only used for diagnostics and CI assertions. The value actually handed to the wallet is the
    /// build-expanded one from ``keychainAccessGroup``, because Kotlin cannot resolve a Team ID.
    public let keychainAccessGroupSuffix: String

    /// mdoc doctypes this demo advertises, and the only ones it will register with Apple.
    ///
    /// Must stay a subset of the target's
    /// `com.apple.developer.identity-document-services.document-provider.mobile-document-types`
    /// entitlement, which Apple provisions from a fixed list.
    public let supportedDocumentTypes: Set<String>

    /// Creates a cross-process namespace description.
    public init(
        appGroupIdentifier: String,
        keychainAccessGroupSuffix: String,
        supportedDocumentTypes: Set<String>
    ) {
        self.appGroupIdentifier = appGroupIdentifier
        self.keychainAccessGroupSuffix = keychainAccessGroupSuffix
        self.supportedDocumentTypes = supportedDocumentTypes
    }

    /// The `AppIdentifierPrefix`-expanded Keychain access group of the running bundle.
    ///
    /// Read from the bundle rather than composed in code: only the build knows the Team ID, and
    /// guessing it wrong yields a group the process is not entitled to, which fails at first use
    /// rather than at launch.
    public var keychainAccessGroup: String? {
        (Bundle.main.object(forInfoDictionaryKey: Self.keychainAccessGroupInfoKey) as? String)
            .flatMap { $0.isEmpty ? nil : $0 }
    }

    /// Wallet configuration for a process that must share state with its counterpart.
    ///
    /// - Parameter walletID: Wallet identifier. Has no default: the wallet id decides which
    ///   `wallet_<id>` database is opened, so a default here would let the extension silently open a
    ///   different database than the host whenever the host runs with a non-default wallet id. The
    ///   extension resolves it from ``activeWalletID()`` instead of assuming one.
    public func walletConfiguration(walletID: String) throws -> WalletConfiguration {
        guard let keychainAccessGroup else {
            throw IdentityDocumentSupportFailure.unresolvedKeychainAccessGroup(Self.keychainAccessGroupInfoKey)
        }
        return WalletConfiguration(
            walletID: walletID,
            crossProcessAccess: WalletCrossProcessAccess(
                appGroupIdentifier: appGroupIdentifier,
                keychainAccessGroup: keychainAccessGroup
            )
        )
    }

    /// The wallet id the host app published into the shared container.
    ///
    /// Apple's `ISO18013MobileDocumentRequestContext` carries the parsed request, the origin,
    /// `sendResponse` and `cancel` - not the `documentIdentifier` of the registration that matched.
    /// So the extension cannot derive the wallet from the request and has to read the host's own
    /// published state, which is also why the projection covers exactly one wallet.
    ///
    /// - Throws: ``IdentityDocumentSupportFailure/missingDesiredRegistrations`` when no host has
    ///   published yet, or ``IdentityDocumentSupportFailure/unreadableDesiredRegistrations(_:)`` when
    ///   the projection cannot be decoded. Guessing `"default"` in either case would open an empty
    ///   database and present the user an empty picker instead of an error.
    public func activeWalletID() throws -> String {
        switch DigitalCredentialRegistrationStorage.desiredRegistrations(appGroupIdentifier: appGroupIdentifier) {
        case .published(let walletID, _):
            return walletID
        case .missing:
            throw IdentityDocumentSupportFailure.missingDesiredRegistrations(appGroupIdentifier)
        case .malformed(let reason):
            throw IdentityDocumentSupportFailure.unreadableDesiredRegistrations(reason)
        }
    }

    /// Info.plist key carrying the build-expanded shared Keychain access group.
    public static let keychainAccessGroupInfoKey = "WALTKeychainAccessGroup"

    /// Doctypes both demos advertise.
    ///
    /// Only what the demo actually issues and presents. Apple also recognises
    /// `org.iso.23220.photoid.1`, which is intentionally absent: an advertised doctype the wallet
    /// cannot satisfy makes the wallet appear in a picker and then fail.
    public static let demoDocumentTypes: Set<String> = [
        "org.iso.18013.5.1.mDL",
        "eu.europa.ec.eudi.pid.1",
    ]

    /// Namespace of the native SwiftUI demo.
    public static let nativeDemo = IdentityDocumentNamespace(
        appGroupIdentifier: "group.id.walt.wallet.demo",
        keychainAccessGroupSuffix: "id.walt.wallet.shared",
        supportedDocumentTypes: demoDocumentTypes
    )

    /// Namespace of the Compose Multiplatform demo.
    public static let composeDemo = IdentityDocumentNamespace(
        appGroupIdentifier: "group.id.walt.wallet.compose.demo",
        keychainAccessGroupSuffix: "id.walt.wallet.compose.shared",
        supportedDocumentTypes: demoDocumentTypes
    )
}

/// Failures raised by the shared provider support layer.
public enum IdentityDocumentSupportFailure: LocalizedError, Equatable {
    /// The bundle carries no build-expanded shared Keychain access group under the given key.
    case unresolvedKeychainAccessGroup(String)
    /// The App Group container could not be opened, so no desired state can be read.
    case sharedContainerUnavailable(String)
    /// No host app has published desired registrations, so the active wallet is unknown.
    case missingDesiredRegistrations(String)
    /// Desired registrations exist but could not be decoded, so nothing may be concluded from them.
    case unreadableDesiredRegistrations(String)
    /// Apple offered alternative document request sets, which this demo does not choose between.
    case alternativeRequestSetsUnsupported
    /// Apple's parsed request contained no documents.
    case emptyRequest
    /// The encrypted Annex C response was not encoded as this provider expects.
    case invalidResponseEncoding
    /// The user has not chosen a credential for every requested document.
    case missingCredentialSelection
    /// IdentityDocumentServices did not assert a requesting website origin.
    case missingVerifiedOrigin

    public var errorDescription: String? {
        switch self {
        case .unresolvedKeychainAccessGroup(let key):
            return "The Info.plist value \(key) is missing, so app and extension cannot share Keychain items"
        case .sharedContainerUnavailable(let group):
            return "The App Group container \(group) is unavailable"
        case .missingDesiredRegistrations(let group):
            return "No wallet has published its documents into \(group) yet; open the app first"
        case .unreadableDesiredRegistrations(let reason):
            return "The shared document registration state could not be read: \(reason)"
        case .alternativeRequestSetsUnsupported:
            return "Alternative document request sets are not supported"
        case .emptyRequest:
            return "The request does not contain any documents"
        case .invalidResponseEncoding:
            return "The encrypted response could not be encoded"
        case .missingCredentialSelection:
            return "Choose one credential for every requested document"
        case .missingVerifiedOrigin:
            return "IdentityDocumentServices did not assert a website origin"
        }
    }
}
