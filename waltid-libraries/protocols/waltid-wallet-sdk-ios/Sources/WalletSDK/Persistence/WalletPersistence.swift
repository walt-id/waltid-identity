import Foundation

/// Selects the database key source and optional credential and DID stores.
/// The wallet opens SQLCipher for signing-identity state and pending issuance even with both store overrides.
public struct WalletPersistence: Sendable {
    /// Owner of the encrypted local database key.
    public var databaseKeySource: DatabaseKeySource

    /// Optional credential-store override. `nil` uses the encrypted local database.
    public var credentialStore: (any WalletCredentialStore)?

    /// Optional DID-store override. `nil` uses the encrypted local database.
    public var didStore: (any WalletDidStore)?

    /// Creates wallet persistence configuration.
    ///
    /// - Parameters:
    ///   - databaseKeySource: Owner of the encrypted local database key.
    ///   - credentialStore: Optional credential-store override.
    ///   - didStore: Optional DID-store override.
    public init(
        databaseKeySource: DatabaseKeySource = .managed,
        credentialStore: (any WalletCredentialStore)? = nil,
        didStore: (any WalletDidStore)? = nil
    ) {
        self.databaseKeySource = databaseKeySource
        self.credentialStore = credentialStore
        self.didStore = didStore
    }
}

/// Database-key ownership supported by the wallet SDK.
public enum DatabaseKeySource: Sendable {
    /// Platform-managed encrypted local database key.
    case managed

    /// Encrypted local database key material provided by app code.
    case provided(any DatabaseEncryptionKeyProvider)
}

/// Database key material used for wallet-local SQLCipher persistence.
///
/// String and debug descriptions redact the raw bytes. Apps should still avoid
/// logging, serializing, or otherwise exposing ``material``.
public struct DatabaseEncryptionKey: CustomDebugStringConvertible, CustomStringConvertible, Equatable, Sendable {
    /// Stable identifier for the database encryption key.
    public let keyID: String

    /// Raw SQLCipher key material.
    public let material: Data

    /// Creates wallet database key material.
    ///
    /// - Parameters:
    ///   - keyID: Stable identifier for the database encryption key.
    ///   - material: Raw SQLCipher key material.
    public init(keyID: String, material: Data) {
        self.keyID = keyID
        self.material = material
    }

    /// Text representation that redacts raw key material.
    public var description: String {
        "DatabaseEncryptionKey(keyID: \(keyID), material: <redacted>)"
    }

    /// Debug representation that redacts raw key material.
    public var debugDescription: String {
        description
    }
}

/// App-owned provider for wallet database encryption keys.
public protocol DatabaseEncryptionKeyProvider: Sendable {
    /// Returns the existing database encryption key or creates it when absent.
    ///
    /// - Parameters:
    ///   - walletID: Stable wallet identifier from ``WalletConfiguration``.
    ///   - databaseName: Wallet database name derived from the wallet ID.
    /// - Returns: Raw SQLCipher database key material.
    func getOrCreateKey(walletID: String, databaseName: String) async throws -> DatabaseEncryptionKey

    /// Deletes provider-owned key material when local wallet data is deleted.
    ///
    /// - Parameters:
    ///   - walletID: Stable wallet identifier from ``WalletConfiguration``.
    ///   - databaseName: Wallet database name derived from the wallet ID.
    func deleteKey(walletID: String, databaseName: String) async throws
}
