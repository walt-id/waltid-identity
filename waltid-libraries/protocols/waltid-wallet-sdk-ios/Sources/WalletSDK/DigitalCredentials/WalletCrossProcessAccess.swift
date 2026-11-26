import Foundation

/// Shared storage required when the wallet is opened from an iOS document-provider extension.
public struct WalletCrossProcessAccess: Equatable, Sendable {
    /// App Group identifier shared by the host app and provider extension.
    public let appGroupIdentifier: String
    /// Keychain access group shared by the host app and provider extension.
    public let keychainAccessGroup: String

    /// Creates shared host-app and provider-extension storage configuration.
    ///
    /// - Parameters:
    ///   - appGroupIdentifier: App Group identifier shared by both processes.
    ///   - keychainAccessGroup: Keychain access group shared by both processes.
    public init(appGroupIdentifier: String, keychainAccessGroup: String) {
        self.appGroupIdentifier = appGroupIdentifier
        self.keychainAccessGroup = keychainAccessGroup
    }
}
