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

    /// Optional store overrides. Omitted credential and DID stores use the
    /// encrypted local database; omitted key stores use platform signing-key
    /// persistence and generation.
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
    /// Optional credential store override. `nil` uses the encrypted local database.
    public var credentials: (any WalletCredentialStore)?

    /// Optional DID document store override. `nil` uses the encrypted local database.
    public var dids: (any WalletDidStore)?

    /// Optional atomic key store and generator override. `nil` uses platform signing-key persistence.
    public var keys: WalletKeys?

    /// Creates wallet store overrides.
    ///
    /// - Parameters:
    ///   - credentials: Optional credential store override.
    ///   - dids: Optional DID document store override.
    ///   - keys: Optional atomic key store and generator override.
    public init(
        credentials: (any WalletCredentialStore)? = nil,
        dids: (any WalletDidStore)? = nil,
        keys: WalletKeys? = nil
    ) {
        self.credentials = credentials
        self.dids = dids
        self.keys = keys
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
    ///
    /// - Parameters:
    ///   - id: Stable local credential identifier.
    ///   - serializedCredential: Raw serialized credential, such as a JWT VC,
    ///     SD-JWT VC, or JSON credential.
    ///   - format: Credential format, for example `vc+sd-jwt` or
    ///     `jwt_vc_json`.
    ///   - label: User-facing credential label when available.
    ///   - addedAt: Date the credential was added to the wallet when available.
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

/// Lightweight metadata about a signing key stored by a custom Swift key store.
public struct WalletKeyInfo: Equatable, Identifiable, Sendable {
    /// Stable wallet-local key identifier.
    public let keyID: String

    /// Signing-key type.
    public let keyType: WalletKeyType

    /// Optional signing algorithm label, such as `EdDSA` or `ES256`.
    public let algorithm: String?

    /// Stable identifier for SwiftUI and collection APIs.
    public var id: String { keyID }

    /// Creates signing-key metadata.
    ///
    /// - Parameters:
    ///   - keyID: Stable wallet-local key identifier.
    ///   - keyType: Signing-key type.
    ///   - algorithm: Optional signing algorithm label.
    public init(keyID: String, keyType: WalletKeyType, algorithm: String? = nil) {
        self.keyID = keyID
        self.keyType = keyType
        self.algorithm = algorithm
    }
}

/// Serialized signing key used by custom Swift key stores.
///
/// The serialized key JSON may contain private signing material. Treat it like a secret and avoid
/// logging or exporting it outside app-owned secure storage.
public struct StoredKey: CustomDebugStringConvertible, CustomStringConvertible, Equatable, Identifiable, Sendable {
    /// Stable wallet-local key identifier.
    public let keyID: String

    /// Signing-key type.
    public let keyType: WalletKeyType

    /// Optional signing algorithm label, such as `EdDSA` or `ES256`.
    public let algorithm: String?

    /// walt.id serialized key JSON payload.
    public let serializedKeyJSON: String

    /// Stable identifier for SwiftUI and collection APIs.
    public var id: String { keyID }

    /// Text representation that redacts serialized key material.
    public var description: String {
        "StoredKey(keyID: \(keyID), keyType: \(keyType), algorithm: \(algorithm ?? "nil"), serializedKeyJSON: <redacted>)"
    }

    /// Debug representation that redacts serialized key material.
    public var debugDescription: String {
        description
    }

    /// Creates a serialized signing-key entry for custom Swift key stores.
    ///
    /// - Parameters:
    ///   - keyID: Stable wallet-local key identifier.
    ///   - keyType: Signing-key type.
    ///   - algorithm: Optional signing algorithm label.
    ///   - serializedKeyJSON: walt.id serialized key JSON payload.
    public init(
        keyID: String,
        keyType: WalletKeyType,
        algorithm: String? = nil,
        serializedKeyJSON: String
    ) {
        self.keyID = keyID
        self.keyType = keyType
        self.algorithm = algorithm
        self.serializedKeyJSON = serializedKeyJSON
    }
}

/// App-owned signing-key persistence override.
public protocol WalletKeyStore: Sendable {
    /// Returns a serialized signing key by wallet-local identifier.
    ///
    /// - Parameter id: Stable wallet-local key identifier.
    /// - Returns: Stored key when present, or `nil` when absent.
    func key(id: String) async throws -> StoredKey?

    /// Lists signing-key metadata in this store.
    ///
    /// - Returns: Stored signing-key metadata currently owned by this store.
    func keys() async throws -> [WalletKeyInfo]

    /// Adds or replaces a serialized signing-key entry.
    ///
    /// - Parameter key: Serialized signing-key entry to persist.
    /// - Returns: Stable wallet-local key identifier for the stored key.
    func addKey(_ key: StoredKey) async throws -> String

    /// Removes a signing key by wallet-local identifier.
    ///
    /// - Parameter id: Stable wallet-local key identifier to remove.
    /// - Returns: `true` when the store removed an existing signing key.
    func removeKey(id: String) async throws -> Bool
}

/// Atomic custom signing-key persistence configuration.
public struct WalletKeys: Sendable {
    /// App-owned signing-key store.
    public let store: any WalletKeyStore

    /// App-owned signing-key generator.
    public let generate: @Sendable (WalletKeyType) async throws -> StoredKey

    /// Creates an atomic signing-key store and generator override.
    ///
    /// - Parameters:
    ///   - store: App-owned signing-key store.
    ///   - generate: App-owned signing-key generator.
    public init(
        store: any WalletKeyStore,
        generate: @escaping @Sendable (WalletKeyType) async throws -> StoredKey
    ) {
        self.store = store
        self.generate = generate
    }
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

    /// Raw parsed credential data encoded as JSON, when available.
    public let credentialDataJSON: String?

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
    ///   - credentialDataJSON: Raw parsed credential data encoded as JSON,
    ///     when available.
    public init(
        id: String,
        format: String,
        issuer: String?,
        subject: String?,
        label: String?,
        addedAt: Date?,
        credentialDataJSON: String? = nil
    ) {
        self.id = id
        self.format = format
        self.issuer = issuer
        self.subject = subject
        self.label = label
        self.addedAt = addedAt
        self.credentialDataJSON = credentialDataJSON
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

/// Preview of an OpenID4VP presentation request before the wallet submits a VP token.
public struct PresentationPreview: Equatable, Sendable {
    /// Verifier/request information shown to the user.
    public let request: PresentationRequestInfo

    /// Credentials that satisfy the request's DCQL queries.
    public let credentialOptions: [PresentationCredentialOption]

    /// Creates a presentation preview.
    ///
    /// - Parameters:
    ///   - request: Verifier and request metadata extracted from the
    ///     presentation request.
    ///   - credentialOptions: Wallet credentials that can satisfy the
    ///     requested credential queries.
    public init(
        request: PresentationRequestInfo,
        credentialOptions: [PresentationCredentialOption]
    ) {
        self.request = request
        self.credentialOptions = credentialOptions
    }
}

/// Verifier metadata extracted from a presentation request.
public struct PresentationRequestInfo: Equatable, Sendable {
    /// OpenID4VP client identifier.
    public let clientID: String?

    /// Human-readable verifier name from client metadata when available.
    public let verifierName: String?

    /// Response URI used for direct-post responses when available.
    public let responseURI: URL?

    /// OpenID state value.
    public let state: String?

    /// OpenID nonce value.
    public let nonce: String?

    /// Creates presentation request information.
    ///
    /// - Parameters:
    ///   - clientID: OpenID4VP client identifier from the request.
    ///   - verifierName: Human-readable verifier name from client metadata
    ///     when available.
    ///   - responseURI: Direct-post response URI when available.
    ///   - state: OpenID state value from the request.
    ///   - nonce: OpenID nonce value from the request.
    public init(
        clientID: String? = nil,
        verifierName: String? = nil,
        responseURI: URL? = nil,
        state: String? = nil,
        nonce: String? = nil
    ) {
        self.clientID = clientID
        self.verifierName = verifierName
        self.responseURI = responseURI
        self.state = state
        self.nonce = nonce
    }
}

/// A wallet credential that satisfies one presentation credential query.
public struct PresentationCredentialOption: Equatable, Identifiable, Sendable {
    /// Stable UI identifier made from query and credential IDs.
    public var id: String { "\(queryID):\(credentialID)" }

    /// DCQL credential query identifier.
    public let queryID: String

    /// Wallet-local credential identifier.
    public let credentialID: String

    /// Credential format.
    public let format: String

    /// Issuer identifier when available.
    public let issuer: String?

    /// Subject identifier when available.
    public let subject: String?

    /// User-facing label when available.
    public let label: String?

    /// Raw credential data encoded as JSON, when available.
    public let credentialDataJSON: String?

    /// Requested credential values shown for informed consent.
    public let disclosures: [PresentationDisclosure]

    /// Creates a presentation credential option.
    ///
    /// - Parameters:
    ///   - queryID: DCQL credential query identifier this option satisfies.
    ///   - credentialID: Wallet-local credential identifier.
    ///   - format: Credential format.
    ///   - issuer: Issuer identifier when available.
    ///   - subject: Subject identifier when available.
    ///   - label: User-facing credential label when available.
    ///   - credentialDataJSON: Raw credential data encoded as JSON, when
    ///     available.
    ///   - disclosures: Credential values requested from this credential.
    public init(
        queryID: String,
        credentialID: String,
        format: String,
        issuer: String?,
        subject: String?,
        label: String?,
        credentialDataJSON: String?,
        disclosures: [PresentationDisclosure] = []
    ) {
        self.queryID = queryID
        self.credentialID = credentialID
        self.format = format
        self.issuer = issuer
        self.subject = subject
        self.label = label
        self.credentialDataJSON = credentialDataJSON
        self.disclosures = disclosures
    }
}

/// Credential value that may be shared.
public struct PresentationDisclosure: Equatable, Identifiable, Sendable {
    /// Stable display identifier.
    public var id: String { path }

    /// JSON-path-like claim path.
    public let path: String

    /// Claim name when known.
    public let name: String?

    /// Raw claim value encoded as JSON.
    public let valueJSON: String

    /// Human-readable value when trivially available.
    public let displayValue: String?

    /// Whether this value comes from a selectively disclosable claim.
    public let selectivelyDisclosable: Bool

    /// Creates a presentation disclosure.
    ///
    /// - Parameters:
    ///   - path: JSON-path-like claim path.
    ///   - name: Claim name when known.
    ///   - valueJSON: Raw claim value encoded as JSON.
    ///   - displayValue: Human-readable value when trivially available.
    ///   - selectivelyDisclosable: Whether this value comes from a
    ///     selectively disclosable claim.
    public init(
        path: String,
        name: String?,
        valueJSON: String,
        displayValue: String?,
        selectivelyDisclosable: Bool
    ) {
        self.path = path
        self.name = name
        self.valueJSON = valueJSON
        self.displayValue = displayValue
        self.selectivelyDisclosable = selectivelyDisclosable
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
