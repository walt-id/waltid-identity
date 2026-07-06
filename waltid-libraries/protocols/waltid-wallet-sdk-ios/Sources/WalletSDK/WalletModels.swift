import Foundation

/// Configuration for a wallet SDK instance.
public struct WalletConfiguration: Sendable {
    /// Stable local wallet identifier used by the underlying wallet store.
    public var walletID: String

    /// Default key type used when bootstrapping a new wallet DID.
    public var defaultKeyType: WalletKeyType

    /// Optional enterprise attestation configuration.
    public var attestation: WalletAttestationConfiguration?

    /// Wallet-local persistence configuration.
    public var persistence: WalletPersistence

    /// Creates wallet configuration.
    ///
    /// - Parameters:
    ///   - walletID: Stable local wallet identifier used for database naming
    ///     and persisted wallet state.
    ///   - defaultKeyType: Key type used by ``Wallet/bootstrap(keyType:didMethod:)``
    ///     when no operation-specific override is supplied.
    ///   - attestation: Optional wallet attestation configuration for issuers
    ///     that require client attestation.
    ///   - persistence: Local persistence configuration for wallet-owned state.
    public init(
        walletID: String = "default",
        defaultKeyType: WalletKeyType = .secp256r1,
        attestation: WalletAttestationConfiguration? = nil,
        persistence: WalletPersistence = WalletPersistence()
    ) {
        self.walletID = walletID
        self.defaultKeyType = defaultKeyType
        self.attestation = attestation
        self.persistence = persistence
    }
}

/// Wallet-local persistence configuration.
public struct WalletPersistence: Sendable {
    /// Owner of the encrypted local database key.
    public var databaseKey: WalletDatabaseKeyConfiguration

    /// Optional store overrides. Omitted stores use default encrypted local persistence.
    public var stores: WalletStores

    /// Creates wallet persistence configuration.
    ///
    /// - Parameters:
    ///   - databaseKey: Owner of the encrypted local database key.
    ///   - stores: Optional store overrides.
    public init(
        databaseKey: WalletDatabaseKeyConfiguration = .managed,
        stores: WalletStores = WalletStores()
    ) {
        self.databaseKey = databaseKey
        self.stores = stores
    }
}

/// Database-key ownership supported by the wallet SDK.
public enum WalletDatabaseKeyConfiguration: Sendable {
    /// Platform-managed encrypted local database key.
    case managed

    /// Encrypted local database key material provided by app code.
    case provided(any WalletDatabaseKeyProvider)
}

/// Optional wallet store overrides.
public struct WalletStores: Sendable {
    /// Optional credential store override. `nil` uses default encrypted local persistence.
    public var credentials: (any WalletCredentialStore)?

    /// Creates wallet store overrides.
    ///
    /// - Parameter credentials: Optional credential store override.
    public init(credentials: (any WalletCredentialStore)? = nil) {
        self.credentials = credentials
    }
}

/// Database key material used for wallet-local SQLCipher persistence.
///
/// String and debug descriptions redact the raw bytes. Apps should still avoid
/// logging, serializing, or otherwise exposing ``material``.
public struct WalletDatabaseKey: CustomDebugStringConvertible, CustomStringConvertible, Equatable, Sendable {
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
        "WalletDatabaseKey(keyID: \(keyID), material: <redacted>)"
    }

    /// Debug representation that redacts raw key material.
    public var debugDescription: String {
        description
    }
}

/// App-owned provider for wallet database encryption keys.
public protocol WalletDatabaseKeyProvider: Sendable {
    /// Returns the existing database encryption key or creates it when absent.
    ///
    /// - Parameters:
    ///   - walletID: Stable wallet identifier from ``WalletConfiguration``.
    ///   - databaseName: Wallet database name derived from the wallet ID.
    /// - Returns: Raw SQLCipher database key material.
    func databaseKey(walletID: String, databaseName: String) async throws -> WalletDatabaseKey

    /// Deletes provider-owned key material when local wallet data is deleted.
    ///
    /// - Parameters:
    ///   - walletID: Stable wallet identifier from ``WalletConfiguration``.
    ///   - databaseName: Wallet database name derived from the wallet ID.
    func deleteDatabaseKey(walletID: String, databaseName: String) async throws
}

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

    /// Creates a credential entry for custom Swift credential stores.
    public init(
        id: String,
        serializedCredential: String,
        format: String,
        label: String? = nil,
        addedAt: Date? = nil
    ) {
        self.id = id
        self.serializedCredential = serializedCredential
        self.format = format
        self.label = label
        self.addedAt = addedAt
    }
}

/// App-owned credential persistence override.
public protocol WalletCredentialStore: Sendable {
    /// Returns a credential by wallet-local identifier.
    func credential(id: String) async throws -> StoredCredential?

    /// Lists all credentials in this store.
    func credentials() async throws -> [StoredCredential]

    /// Adds or replaces a credential entry.
    func addCredential(_ credential: StoredCredential) async throws

    /// Removes a credential by wallet-local identifier.
    func removeCredential(id: String) async throws -> Bool
}

/// Key algorithms supported by the wallet bridge.
public enum WalletKeyType: Equatable, Sendable {
    /// Ed25519 elliptic curve key.
    case ed25519

    /// secp256k1 elliptic curve key.
    case secp256k1

    /// secp256r1 elliptic curve key.
    case secp256r1

    /// secp384r1 elliptic curve key.
    case secp384r1

    /// secp521r1 elliptic curve key.
    case secp521r1

    /// RSA key with the wallet default size.
    case rsa

    /// RSA key with a 3072-bit modulus.
    case rsa3072

    /// RSA key with a 4096-bit modulus.
    case rsa4096
}

/// Wallet attestation settings.
public struct WalletAttestationConfiguration: Equatable, Sendable {
    /// Attestation API base URL.
    public var baseURL: String

    /// Relative attester endpoint path.
    public var attesterPath: String

    /// Optional bearer token for attestation requests.
    public var bearerToken: String

    /// Optional host header override for attestation requests.
    public var hostHeader: String

    /// Creates wallet attestation settings.
    ///
    /// - Parameters:
    ///   - baseURL: Base URL of the attestation service.
    ///   - attesterPath: Relative attester endpoint path.
    ///   - bearerToken: Optional bearer token used for attestation requests.
    ///   - hostHeader: Optional host header override for attestation requests.
    public init(
        baseURL: String,
        attesterPath: String,
        bearerToken: String = "",
        hostHeader: String = ""
    ) {
        self.baseURL = baseURL
        self.attesterPath = attesterPath
        self.bearerToken = bearerToken
        self.hostHeader = hostHeader
    }
}

/// Credential metadata visible to Swift consumers.
public struct Credential: Equatable, Identifiable, Sendable {
    /// Stable local credential identifier.
    public let id: String

    /// Credential format, for example `vc+sd-jwt` or `jwt_vc_json`.
    public let format: String

    /// Issuer identifier or URL when available.
    public let issuer: String?

    /// Credential subject identifier when available.
    public let subject: String?

    /// User-facing credential label when available.
    public let label: String?

    /// Date the credential was added to the wallet when available.
    public let addedAt: Date?

    /// Creates credential metadata visible to SDK consumers.
    ///
    /// - Parameters:
    ///   - id: Stable local credential identifier.
    ///   - format: Credential format, for example `vc+sd-jwt` or
    ///     `jwt_vc_json`.
    ///   - issuer: Issuer identifier or URL when available.
    ///   - subject: Credential subject identifier when available.
    ///   - label: User-facing credential label when available.
    ///   - addedAt: Date the credential was added to the wallet when
    ///     available.
    public init(
        id: String,
        format: String,
        issuer: String?,
        subject: String?,
        label: String?,
        addedAt: Date?
    ) {
        self.id = id
        self.format = format
        self.issuer = issuer
        self.subject = subject
        self.label = label
        self.addedAt = addedAt
    }
}

/// Result of bootstrapping wallet key material and DID state.
public struct WalletBootstrapResult: Equatable, Sendable {
    /// Identifier of the created or selected wallet key.
    public let keyID: String

    /// DID created for the wallet.
    public let did: String

    /// Creates a bootstrap result.
    ///
    /// - Parameters:
    ///   - keyID: Identifier of the created or selected wallet key.
    ///   - did: DID created for the wallet.
    public init(keyID: String, did: String) {
        self.keyID = keyID
        self.did = did
    }
}

/// Result of responding to an OpenID4VP presentation request.
public struct PresentationResult: Equatable, Sendable {
    /// Indicates whether the presentation flow completed successfully.
    public let success: Bool

    /// Optional verifier redirect URL.
    public let redirectTo: URL?

    /// Optional raw verifier response JSON.
    public let verifierResponseJSON: String?

    /// Creates a presentation result.
    ///
    /// - Parameters:
    ///   - success: Indicates whether the presentation flow completed
    ///     successfully.
    ///   - redirectTo: Optional verifier redirect URL.
    ///   - verifierResponseJSON: Optional raw verifier response JSON.
    public init(
        success: Bool,
        redirectTo: URL?,
        verifierResponseJSON: String?
    ) {
        self.success = success
        self.redirectTo = redirectTo
        self.verifierResponseJSON = verifierResponseJSON
    }
}

/// Progress event emitted while issuance or presentation work is running.
public struct WalletEvent: Equatable, Sendable {
    /// Event name emitted by the wallet core.
    public let name: String

    /// High-level workflow phase for the event.
    public let phase: WalletEventPhase

    /// High-level status for the event.
    public let status: WalletEventStatus

    /// Creates a wallet progress event.
    ///
    /// - Parameters:
    ///   - name: Event name emitted by the wallet core.
    ///   - phase: High-level issuance or presentation phase.
    ///   - status: High-level progress status.
    public init(
        name: String,
        phase: WalletEventPhase,
        status: WalletEventStatus
    ) {
        self.name = name
        self.phase = phase
        self.status = status
    }
}

/// High-level workflow phase for a wallet event.
public enum WalletEventPhase: Equatable, Sendable {
    /// Credential issuance workflow.
    case issuance

    /// Credential presentation workflow.
    case presentation
}

/// High-level status for a wallet event.
public enum WalletEventStatus: Equatable, Sendable {
    /// The operation is still running.
    case progress

    /// The operation completed successfully.
    case completed

    /// The operation failed.
    case failed
}
