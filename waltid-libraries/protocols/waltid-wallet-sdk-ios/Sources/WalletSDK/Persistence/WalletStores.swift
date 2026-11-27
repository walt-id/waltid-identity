import Foundation

/// Credential entry used by custom Swift credential stores.
public struct StoredCredential: Equatable, Identifiable, Sendable {
    /// Stable local credential identifier.
    public let id: String

    /// Raw serialized credential, such as a JWT VC, SD-JWT VC, or JSON credential.
    public let serializedCredential: String

    /// Credential format, for example `vc+sd-jwt` or `jwt_vc_json`.
    public let format: String

    /// User-facing credential label when available.
    public let label: String?

    /// Date the credential was added to the wallet when available.
    public let addedAt: Date?

    /// Optional arbitrary metadata as a JSON string.
    public let metadataJSON: String?

    /// Creates a credential entry for custom Swift credential stores.
    ///
    /// - Parameters:
    ///   - id: Stable local credential identifier.
    ///   - serializedCredential: Raw serialized credential, such as a JWT VC,
    ///     SD-JWT VC, or JSON credential.
    ///   - format: Credential format, for example `vc+sd-jwt` or
    ///     `jwt_vc_json`.
    ///   - label: User-facing credential label when available.
    ///   - addedAt: Date the credential was added to the wallet when available.
    ///   - metadataJSON: Optional arbitrary metadata as a JSON string.
    public init(
        id: String,
        serializedCredential: String,
        format: String,
        label: String? = nil,
        addedAt: Date? = nil,
        metadataJSON: String? = nil
    ) {
        self.id = id
        self.serializedCredential = serializedCredential
        self.format = format
        self.label = label
        self.addedAt = addedAt
        self.metadataJSON = metadataJSON
    }
}

/// App-owned credential persistence override.
public protocol WalletCredentialStore: Sendable {
    /// Returns a credential by wallet-local identifier.
    ///
    /// - Parameter id: Stable wallet-local credential identifier.
    /// - Returns: Stored credential when present, or `nil` when absent.
    func credential(id: String) async throws -> StoredCredential?

    /// Lists all credentials in this store.
    ///
    /// - Returns: Stored credentials currently owned by this store.
    func credentials() async throws -> [StoredCredential]

    /// Adds or replaces a credential entry.
    ///
    /// - Parameter credential: Credential entry to persist.
    func addCredential(_ credential: StoredCredential) async throws

    /// Removes a credential by wallet-local identifier.
    ///
    /// - Parameter id: Stable wallet-local credential identifier to remove.
    /// - Returns: `true` when the store removed an existing credential.
    func removeCredential(id: String) async throws -> Bool
}

/// DID document entry used by custom Swift DID stores.
public struct StoredDid: Equatable, Identifiable, Sendable {
    /// Stable DID string.
    public let did: String

    /// Serialized DID document JSON object.
    public let documentJSON: String

    /// Stable identifier for SwiftUI and collection APIs.
    public var id: String { did }

    /// Creates a DID document entry for custom Swift DID stores.
    ///
    /// - Parameters:
    ///   - did: Stable DID string.
    ///   - documentJSON: Serialized DID document JSON object.
    public init(did: String, documentJSON: String) {
        self.did = did
        self.documentJSON = documentJSON
    }
}

/// App-owned DID document persistence override.
public protocol WalletDidStore: Sendable {
    /// Returns a DID document by DID string.
    ///
    /// - Parameter id: Stable DID string.
    /// - Returns: Stored DID document when present, or `nil` when absent.
    func did(id: String) async throws -> StoredDid?

    /// Lists all DID documents in this store.
    ///
    /// - Returns: Stored DID documents currently owned by this store.
    func dids() async throws -> [StoredDid]

    /// Adds or replaces a DID document entry.
    ///
    /// - Parameter did: DID document entry to persist.
    func addDid(_ did: StoredDid) async throws

    /// Removes a DID document by DID string.
    ///
    /// - Parameter id: Stable DID string to remove.
    /// - Returns: `true` when the store removed an existing DID document.
    func removeDid(id: String) async throws -> Bool
}
