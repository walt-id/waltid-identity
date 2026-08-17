import Foundation

/// Configuration for a wallet SDK instance.
public struct WalletConfiguration: Sendable {
    /// Stable local wallet identifier used by the underlying wallet store.
    public var walletID: String

    /// Default key type used when bootstrapping a new wallet DID.
    public var defaultKeyType: WalletKeyType

    /// Optional enterprise attestation configuration.
    public var attestation: WalletAttestationConfiguration?

    /// Trust anchors used to authenticate verifier Request Objects.
    public var clientIDTrustConfiguration: WalletClientIDTrustConfiguration

    /// Optional verifier for signed Credential Issuer Metadata.
    public var issuerMetadataTrustResolver: (any IssuerMetadataTrustResolver)?

    /// Wallet-local persistence configuration.
    public var persistence: WalletPersistence

    /// Transaction data profiles this wallet accepts in OpenID4VP requests.
    public var transactionDataProfiles: [WalletTransactionDataProfile]

    /// Ordered BCP 47 locale preferences used to select protocol display metadata.
    public var preferredLocales: [String]
    /// Shared app/extension storage and Keychain configuration for IdentityDocumentServices.
    public var crossProcessAccess: WalletCrossProcessAccess?

    /// Creates wallet configuration.
    ///
    /// - Parameters:
    ///   - walletID: Stable local wallet identifier used for database naming
    ///     and persisted wallet state.
    ///   - defaultKeyType: Key type used by ``Wallet/bootstrap(keyType:didMethod:)``
    ///     when no operation-specific override is supplied.
    ///   - attestation: Optional wallet attestation configuration for issuers
    ///     that require client attestation.
    ///   - clientIDTrustConfiguration: Trust anchors used to authenticate verifier
    ///     Request Objects. The default trusts no X.509 verifier by configuration.
    ///   - issuerMetadataTrustResolver: Verifies signed Credential Issuer Metadata
    ///     and establishes the signer's authority.
    ///   - persistence: Local persistence configuration for wallet-owned state.
    ///   - transactionDataProfiles: OpenID4VP transaction data profiles this
    ///     wallet accepts before previewing or submitting a presentation.
    ///   - preferredLocales: Ordered BCP 47 locale preferences used for issuer,
    ///     credential, and verifier display metadata.
    ///   - crossProcessAccess: Optional shared app/extension storage and Keychain configuration
    ///     for IdentityDocumentServices.
    public init(
        walletID: String = "default",
        defaultKeyType: WalletKeyType = .secp256r1,
        attestation: WalletAttestationConfiguration? = nil,
        clientIDTrustConfiguration: WalletClientIDTrustConfiguration = .init(),
        issuerMetadataTrustResolver: (any IssuerMetadataTrustResolver)? = nil,
        persistence: WalletPersistence = WalletPersistence(),
        transactionDataProfiles: [WalletTransactionDataProfile] = [],
        preferredLocales: [String] = Locale.preferredLanguages,
        crossProcessAccess: WalletCrossProcessAccess? = nil
    ) {
        self.walletID = walletID
        self.defaultKeyType = defaultKeyType
        self.attestation = attestation
        self.clientIDTrustConfiguration = clientIDTrustConfiguration
        self.issuerMetadataTrustResolver = issuerMetadataTrustResolver
        self.persistence = persistence
        self.transactionDataProfiles = transactionDataProfiles
        self.preferredLocales = preferredLocales
        self.crossProcessAccess = crossProcessAccess
    }
}

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
        precondition(!appGroupIdentifier.isEmpty, "App Group identifier must not be empty")
        precondition(!keychainAccessGroup.isEmpty, "Keychain access group must not be empty")
        self.appGroupIdentifier = appGroupIdentifier
        self.keychainAccessGroup = keychainAccessGroup
    }
}

/// Trusted signer details returned after verifying signed Credential Issuer Metadata.
public struct IssuerMetadataSigner: Equatable, Sendable {
    /// Identifier of the trusted verification key, as reported by the trust resolver.
    public let keyID: String?
    /// JWS algorithm verified by the trust resolver.
    public let algorithm: String
    /// Authority category established by the trust resolver.
    public let trustType: MetadataTrustType

    /// Creates trusted signer details.
    ///
    /// - Parameters:
    ///   - keyID: Identifier of the trusted verification key, as reported by the trust resolver.
    ///   - algorithm: JWS algorithm verified by the trust resolver.
    ///   - trustType: Authority category established by the trust resolver.
    public init(keyID: String?, algorithm: String, trustType: MetadataTrustType) {
        self.keyID = keyID
        self.algorithm = algorithm
        self.trustType = trustType
    }
}

/// Authority category established for a signer of Credential Issuer Metadata.
public enum MetadataTrustType: Equatable, Sendable {
    /// The signer is the trusted credential issuer.
    case trustedIssuer
    /// The signer is a trusted delegate of the credential issuer.
    case trustedDelegate
}

/// Verifies signed Credential Issuer Metadata before the wallet reads its claims.
public protocol IssuerMetadataTrustResolver: Sendable {
    /// Verifies a compact JWS and establishes that its signer is authorized for the expected issuer.
    ///
    /// - Parameters:
    ///   - compactJWT: Compact JWS returned by the Credential Issuer Metadata endpoint.
    ///   - expectedCredentialIssuer: Credential issuer for which signer authority must be established.
    /// - Returns: Trusted signer details used to retain metadata provenance.
    func verify(compactJWT: String, expectedCredentialIssuer: String) async throws -> IssuerMetadataSigner
}

/// Trust configuration used to authenticate verifier Request Objects.
public struct WalletClientIDTrustConfiguration: Sendable, Equatable {
    /// PEM-encoded X.509 trust anchors pinned by the hosting application.
    public var x509TrustAnchorsPEM: [String]

    /// Creates client-ID trust configuration.
    ///
    /// - Parameter x509TrustAnchorsPEM: PEM-encoded X.509 trust anchors pinned
    ///   by the hosting application.
    public init(x509TrustAnchorsPEM: [String] = []) {
        self.x509TrustAnchorsPEM = x509TrustAnchorsPEM
    }
}

/// OpenID4VP transaction data profile accepted by the wallet.
public struct WalletTransactionDataProfile: Equatable, Sendable {
    /// Collision-resistant OpenID4VP `transaction_data.type` value.
    public let type: String

    /// Human-readable label for consent UI.
    public let displayName: String

    /// Supported transaction type-specific fields.
    public let fields: [String]

    /// Creates a transaction data profile.
    ///
    /// - Parameters:
    ///   - type: Collision-resistant OpenID4VP `transaction_data.type` value.
    ///   - displayName: Human-readable label for consent UI. Defaults to `type`.
    ///   - fields: Supported transaction type-specific fields.
    public init(type: String, displayName: String? = nil, fields: [String] = []) {
        self.type = type
        self.displayName = displayName ?? type
        self.fields = fields
    }

}

/// Wallet-local persistence configuration.
public struct WalletPersistence: Sendable {
    /// Owner of the encrypted local database key.
    public var databaseKey: WalletDatabaseKeyConfiguration

    /// Optional credential-store override. `nil` uses the encrypted local database.
    public var credentialStore: (any WalletCredentialStore)?

    /// Optional DID-store override. `nil` uses the encrypted local database.
    public var didStore: (any WalletDidStore)?

    /// Creates wallet persistence configuration.
    ///
    /// - Parameters:
    ///   - databaseKey: Owner of the encrypted local database key.
    ///   - credentialStore: Optional credential-store override.
    ///   - didStore: Optional DID-store override.
    public init(
        databaseKey: WalletDatabaseKeyConfiguration = .managed,
        credentialStore: (any WalletCredentialStore)? = nil,
        didStore: (any WalletDidStore)? = nil
    ) {
        self.databaseKey = databaseKey
        self.credentialStore = credentialStore
        self.didStore = didStore
    }
}

/// Database-key ownership supported by the wallet SDK.
public enum WalletDatabaseKeyConfiguration: Sendable {
    /// Platform-managed encrypted local database key.
    case managed

    /// Encrypted local database key material provided by app code.
    case provided(any WalletDatabaseKeyProvider)
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

/// Display metadata normalized from issuer, credential, or verifier protocol metadata.
///
/// URI values are untrusted protocol input. Rendering them does not establish
/// issuer or verifier trust.
public struct MetadataDisplay: Equatable, Sendable {
    /// Best localized display name.
    public let name: String?
    /// BCP 47 language tag associated with the selected display entry.
    public let locale: String?
    /// Issuer- or verifier-provided logo URI.
    public let logoURI: String?
    /// Accessible alternative text for the logo.
    public let logoAltText: String?
    /// Human-readable description.
    public let description: String?
    /// Suggested credential background color.
    public let backgroundColor: String?
    /// Suggested credential background image URI.
    public let backgroundImageURI: String?
    /// Suggested credential text color.
    public let textColor: String?

    /// Creates normalized protocol display metadata.
    ///
    /// - Parameters:
    ///   - name: Best localized human-readable name.
    ///   - locale: BCP 47 language tag for the selected display entry.
    ///   - logoURI: Issuer- or verifier-provided logo URI.
    ///   - logoAltText: Accessible alternative text for the logo.
    ///   - description: Human-readable credential description.
    ///   - backgroundColor: Suggested credential background color.
    ///   - backgroundImageURI: Suggested credential background image URI.
    ///   - textColor: Suggested credential text color.
    public init(
        name: String?,
        locale: String?,
        logoURI: String?,
        logoAltText: String?,
        description: String? = nil,
        backgroundColor: String? = nil,
        backgroundImageURI: String? = nil,
        textColor: String? = nil
    ) {
        self.name = name
        self.locale = locale
        self.logoURI = logoURI
        self.logoAltText = logoAltText
        self.description = description
        self.backgroundColor = backgroundColor
        self.backgroundImageURI = backgroundImageURI
        self.textColor = textColor
    }
}

/// Typed OpenID4VP verifier metadata shown during presentation review.
public struct VerifierMetadata: Equatable, Sendable {
    /// Best localized verifier display entry.
    public let display: MetadataDisplay?
    /// Verifier information page URI.
    public let clientURI: String?
    /// Verifier privacy policy URI.
    public let policyURI: String?
    /// Verifier terms-of-service URI.
    public let termsOfServiceURI: String?

    /// Creates typed verifier metadata supplied by an OpenID4VP request.
    ///
    /// - Parameters:
    ///   - display: Best localized verifier name and logo.
    ///   - clientURI: Verifier information-page URI.
    ///   - policyURI: Verifier privacy-policy URI.
    ///   - termsOfServiceURI: Verifier terms-of-service URI.
    public init(
        display: MetadataDisplay?,
        clientURI: String?,
        policyURI: String?,
        termsOfServiceURI: String?
    ) {
        self.display = display
        self.clientURI = clientURI
        self.policyURI = policyURI
        self.termsOfServiceURI = termsOfServiceURI
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

    /// Parsed credential data encoded as JSON for app-side display.
    public let credentialDataJSON: String

    /// Optional sidecar metadata JSON persisted with the credential (e.g. `issuerDisplay`).
    public let metadataJSON: String?

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
    ///   - credentialDataJSON: Parsed credential data encoded as JSON for
    ///     app-side display.
    ///   - metadataJSON: Optional sidecar metadata JSON persisted with the
    ///     credential.
    public init(
        id: String,
        format: String,
        issuer: String?,
        subject: String?,
        label: String?,
        addedAt: Date?,
        credentialDataJSON: String,
        metadataJSON: String? = nil
    ) {
        self.id = id
        self.format = format
        self.issuer = issuer
        self.subject = subject
        self.label = label
        self.addedAt = addedAt
        self.credentialDataJSON = credentialDataJSON
        self.metadataJSON = metadataJSON
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

/// Input used to start either OpenID4VCI issuance grant.
public struct IssuanceRequest: Equatable, Sendable {
    /// Credential-offer URL to resolve.
    public let offer: URL

    /// OAuth client identifier used for the issuance session.
    public let clientID: String

    /// Exact callback URI registered for authorization-code issuance.
    public let redirectURI: URL

    /// Optional identifier of the holder key used for DPoP and credential proofs.
    public let keyID: String?

    /// Optional holder DID URL used when the credential requires DID binding.
    public let did: String?

    /// Creates an issuance request.
    ///
    /// - Parameters:
    ///   - offer: Credential-offer URL to resolve.
    ///   - clientID: OAuth client identifier used for the issuance session.
    ///   - redirectURI: Exact registered callback URI.
    ///   - keyID: Optional identifier of the selected holder key.
    ///   - did: Optional holder DID URL identifying the selected key.
    public init(
        offer: URL,
        clientID: String = "eudiw-abca",
        redirectURI: URL,
        keyID: String? = nil,
        did: String? = nil
    ) {
        self.offer = offer
        self.clientID = clientID
        self.redirectURI = redirectURI
        self.keyID = keyID
        self.did = did
    }

}

/// OAuth grant selected by the resolved credential offer.
public enum IssuanceGrant: Equatable, Sendable {
    /// Issuance using a pre-authorized code and optional transaction code.
    case preAuthorizedCode

    /// Issuance using browser authorization and an authorization code.
    case authorizationCode
}

/// Transaction-code input requested by a pre-authorized credential offer.
public struct IssuanceTransactionCode: Equatable, Sendable {
    /// Expected input mode, such as numeric or text.
    public let inputMode: String?

    /// Expected number of characters when specified by the issuer.
    public let length: Int?

    /// Optional user-facing instructions supplied by the issuer.
    public let descriptionText: String?

    /// Creates a transaction-code requirement.
    ///
    /// - Parameters:
    ///   - inputMode: Expected input mode.
    ///   - length: Expected number of characters.
    ///   - descriptionText: Optional user-facing instructions.
    public init(inputMode: String?, length: Int?, descriptionText: String?) {
        self.inputMode = inputMode
        self.length = length
        self.descriptionText = descriptionText
    }
}

/// Display-safe issuer information resolved from credential issuer metadata.
public struct IssuanceIssuerPreview: Equatable, Sendable {
    /// Credential issuer identifier.
    public let identifier: String

    /// Localized issuer name when advertised.
    public let name: String?

    /// Locale associated with the selected display entry.
    public let locale: String?

    /// Issuer logo URL when advertised.
    public let logoURI: URL?

    /// Alternative text for the issuer logo.
    public let logoAltText: String?

    /// Source and verification provenance for this metadata.
    public let metadataProvenance: MetadataProvenance

    /// Creates an issuer preview.
    ///
    /// - Parameters:
    ///   - identifier: Credential issuer identifier.
    ///   - name: Localized issuer name.
    ///   - locale: Locale associated with the display entry.
    ///   - logoURI: Issuer logo URL.
    ///   - logoAltText: Alternative text for the issuer logo.
    ///   - metadataProvenance: Source and verification provenance for this metadata.
    public init(
        identifier: String,
        name: String?,
        locale: String?,
        logoURI: URL?,
        logoAltText: String?,
        metadataProvenance: MetadataProvenance
    ) {
        self.identifier = identifier
        self.name = name
        self.locale = locale
        self.logoURI = logoURI
        self.logoAltText = logoAltText
        self.metadataProvenance = metadataProvenance
    }
}

/// Provenance for Credential Issuer Metadata used by the wallet.
public enum MetadataProvenance: Equatable, Sendable {
    /// Metadata was received as an unsigned JSON document.
    case unsigned
    /// Metadata was received in a JWS verified by the configured trust resolver.
    case signed(SignedMetadataProvenance)
}

/// Verified signed Credential Issuer Metadata provenance.
public struct SignedMetadataProvenance: Equatable, Sendable {
    /// Exact compact JWS returned by the issuer.
    public let compactJWT: String
    /// JWS algorithm verified by the configured trust resolver.
    public let algorithm: String
    /// Identifier of the trusted verification key, as reported by the trust resolver.
    public let keyID: String?
    /// Authority category established by the trust resolver.
    public let trustType: MetadataTrustType

    /// Creates signed metadata provenance.
    ///
    /// - Parameters:
    ///   - compactJWT: Exact compact JWS returned by the issuer.
    ///   - algorithm: JWS algorithm verified by the trust resolver.
    ///   - keyID: Identifier of the trusted verification key, as reported by the trust resolver.
    ///   - trustType: Authority category established by the trust resolver.
    public init(compactJWT: String, algorithm: String, keyID: String?, trustType: MetadataTrustType) {
        self.compactJWT = compactJWT
        self.algorithm = algorithm
        self.keyID = keyID
        self.trustType = trustType
    }
}

/// Display-safe credential configuration offered by an issuer.
public struct IssuanceCredentialPreview: Equatable, Sendable {
    /// Issuer-defined credential configuration identifier.
    public let configurationID: String

    /// OpenID4VCI credential format identifier.
    public let format: String

    /// Localized credential name when advertised.
    public let name: String?

    /// Localized credential description when advertised.
    public let descriptionText: String?

    /// Credential logo URL when advertised.
    public let logoURI: URL?

    /// Creates a credential preview.
    ///
    /// - Parameters:
    ///   - configurationID: Issuer-defined credential configuration identifier.
    ///   - format: OpenID4VCI credential format identifier.
    ///   - name: Localized credential name.
    ///   - descriptionText: Localized credential description.
    ///   - logoURI: Credential logo URL.
    public init(configurationID: String, format: String, name: String?, descriptionText: String?, logoURI: URL?) {
        self.configurationID = configurationID
        self.format = format
        self.name = name
        self.descriptionText = descriptionText
        self.logoURI = logoURI
    }
}

/// Typed preview of a resolved OpenID4VCI credential offer.
public struct IssuanceOfferPreview: Equatable, Sendable {
    /// OAuth grant selected by the offer.
    public let grant: IssuanceGrant

    /// Issuer information suitable for review UI.
    public let issuer: IssuanceIssuerPreview

    /// Credential configurations included in the offer.
    public let credentials: [IssuanceCredentialPreview]

    /// Transaction-code requirement for pre-authorized issuance, if any.
    public let transactionCode: IssuanceTransactionCode?

    /// Creates an offer preview.
    ///
    /// - Parameters:
    ///   - grant: OAuth grant selected by the offer.
    ///   - issuer: Issuer information suitable for review UI.
    ///   - credentials: Credential configurations included in the offer.
    ///   - transactionCode: Optional transaction-code requirement.
    public init(
        grant: IssuanceGrant,
        issuer: IssuanceIssuerPreview,
        credentials: [IssuanceCredentialPreview],
        transactionCode: IssuanceTransactionCode?
    ) {
        self.grant = grant
        self.issuer = issuer
        self.credentials = credentials
        self.transactionCode = transactionCode
    }
}

/// Non-secret PKCE metadata bound to the authorization request.
public struct IssuancePKCEState: Equatable, Sendable {
    /// S256 challenge sent in the authorization request.
    public let codeChallenge: String

    /// PKCE challenge method, normally `S256`.
    public let codeChallengeMethod: String

    /// Creates PKCE continuation material.
    ///
    /// - Parameters:
    ///   - codeChallenge: Challenge sent in the authorization request.
    ///   - codeChallengeMethod: PKCE challenge method.
    public init(codeChallenge: String, codeChallengeMethod: String) {
        self.codeChallenge = codeChallenge
        self.codeChallengeMethod = codeChallengeMethod
    }

}

/// Browser authorization request and callback binding for an issuance session.
public struct IssuanceAuthorization: Equatable, Sendable {
    /// Authorization URL to open in the system browser.
    public let url: URL

    /// OAuth state value bound to this issuance session.
    public let state: String

    /// Exact redirect URI expected for the callback.
    public let redirectURI: URL

    /// PKCE material bound to the authorization request.
    public let pkce: IssuancePKCEState

    /// Indicates whether the request was submitted through PAR.
    public let pushedAuthorizationRequestUsed: Bool

    /// Absolute expiry of the PAR request URI, when PAR was used.
    public let requestURIExpiresAt: Date?

    /// Creates browser authorization continuation data.
    ///
    /// - Parameters:
    ///   - url: Authorization URL to open in the system browser.
    ///   - state: OAuth state value bound to the session.
    ///   - redirectURI: Exact redirect URI expected for the callback.
    ///   - pkce: PKCE material bound to the request.
    ///   - pushedAuthorizationRequestUsed: Whether PAR was used.
    ///   - requestURIExpiresAt: Absolute expiry of the PAR request URI, when present.
    public init(
        url: URL,
        state: String,
        redirectURI: URL,
        pkce: IssuancePKCEState,
        pushedAuthorizationRequestUsed: Bool,
        requestURIExpiresAt: Date? = nil
    ) {
        self.url = url
        self.state = state
        self.redirectURI = redirectURI
        self.pkce = pkce
        self.pushedAuthorizationRequestUsed = pushedAuthorizationRequestUsed
        self.requestURIExpiresAt = requestURIExpiresAt
    }

}

/// Durable issuance session returned after resolving and validating an offer.
public struct IssuanceSession: Equatable, Sendable {
    /// Opaque identifier used to continue or cancel this session.
    public let id: String

    /// Typed offer preview for application review UI.
    public let offer: IssuanceOfferPreview

    /// Creates an issuance session.
    ///
    /// - Parameters:
    ///   - id: Opaque session identifier.
    ///   - offer: Typed offer preview.
    public init(id: String, offer: IssuanceOfferPreview) {
        self.id = id
        self.offer = offer
    }

}

/// Credential issuance operation that must be resumed after issuer processing.
public struct DeferredCredential: Equatable, Sendable {
    /// Opaque identifier used to resume this deferred operation.
    public let id: String

    /// Credential configuration associated with the operation.
    public let credentialConfigurationID: String

    /// Issuer-recommended minimum polling interval in seconds.
    public let intervalSeconds: Int64?

    /// Creates a deferred credential continuation.
    ///
    /// - Parameters:
    ///   - id: Opaque deferred operation identifier.
    ///   - credentialConfigurationID: Credential configuration being issued.
    ///   - intervalSeconds: Issuer-recommended polling interval.
    public init(id: String, credentialConfigurationID: String, intervalSeconds: Int64?) {
        self.id = id
        self.credentialConfigurationID = credentialConfigurationID
        self.intervalSeconds = intervalSeconds
    }
}

/// Stable category for a sanitized issuance failure.
public enum IssuanceErrorCode: Equatable, Sendable {
    /// The session identifier is unknown, expired, or already consumed.
    case invalidSession

    /// The authorization callback failed session or redirect validation.
    case invalidCallback

    /// Required application input is missing or invalid.
    case invalidInput

    /// The authorization server returned an OAuth authorization error.
    case authorizationFailed

    /// Issuer or authorization server metadata is invalid or unsupported.
    case issuerMetadata

    /// The issuer or authorization server rejected a protocol request.
    case issuerResponse

    /// A transport failure prevented issuer communication.
    case network

    /// Key selection, signing, or proof generation failed.
    case crypto

    /// Credential parsing or local persistence failed.
    case storage

    /// A protocol response was structurally invalid or inconsistent.
    case `protocol`
}

/// Sanitized error returned by a typed issuance transition.
public struct IssuanceFailure: Equatable, Sendable {
    /// Stable error category suitable for application control flow.
    public let code: IssuanceErrorCode

    /// Public error description that excludes protocol secrets.
    public let message: String

    /// Creates a sanitized issuance failure.
    ///
    /// - Parameters:
    ///   - code: Stable error category.
    ///   - message: Public error description.
    public init(code: IssuanceErrorCode, message: String) {
        self.code = code
        self.message = message
    }
}

/// Immediate, deferred, cancelled, or failed result of one issuance transition.
public enum IssuanceOutcome: Equatable, Sendable {
    /// All returned credentials were stored successfully.
    case stored(sessionID: String, credentialIDs: [String])

    /// At least one credential is awaiting issuer processing.
    case deferred(sessionID: String, storedCredentialIDs: [String], credentials: [DeferredCredential])

    /// The user or authorization server cancelled the issuance session.
    case cancelled(sessionID: String)

    /// The transition failed with a sanitized error and any credentials stored before the failure.
    case failed(sessionID: String, error: IssuanceFailure, storedCredentialIDs: [String])
}

/// Result of responding to an OpenID4VP presentation request.
///
/// Each case represents the next action required from the host app.
public enum PresentationResult: Equatable, Sendable {
    /// A protocol response that still requires a host-app delivery action.
    public enum Prepared: Equatable, Sendable {
        /// The host app must open the URL to deliver the protocol response.
        case openURL(URL)

        /// The host app must render the HTML so its self-submitting form can deliver the protocol response.
        case submitForm(html: String)
    }

    /// A protocol response that was transmitted and received a JSON verifier response.
    public enum Transmitted: Equatable, Sendable {
        /// The verifier accepted the protocol response.
        case succeeded(verifierResponseJSON: String, redirectURL: URL? = nil)

        /// The verifier rejected or could not process the protocol response.
        case failed(verifierResponseJSON: String)
    }

    /// The host app still needs to deliver the prepared response.
    case prepared(Prepared)

    /// The verifier returned a response after protocol transmission.
    case transmitted(Transmitted)

}

/// Result of resolving and validating an OpenID4VP request for presentation preview.
public enum PresentationPreviewResult: Equatable, Sendable {
    /// The request is valid and can be reviewed, submitted, or declined.
    case ready(PresentationPreview)

    /// The request cannot be fulfilled, but its protocol error can be returned after user interaction.
    case invalid(PresentationPreviewError)
}

/// Protocol error detected while previewing a presentation request.
public struct PresentationPreviewError: Equatable, Sendable {
    /// Opaque handle required to reject or discard this reviewed request.
    public let previewHandle: PresentationPreviewHandle

    /// Validated response destination and partial request context to show before returning the error.
    public let request: PresentationRequestContext

    /// OpenID4VP or OAuth authorization error code selected by the wallet.
    public let code: PresentationErrorCode

    /// Local diagnostic intended for wallet UI; it is not sent to the verifier automatically.
    public let message: String

    /// Creates a presentation preview error.
    ///
    /// - Parameters:
    ///   - previewHandle: Opaque handle required to reject or discard this reviewed request.
    ///   - request: Validated response destination and request context shown before responding.
    ///   - code: OpenID4VP or OAuth authorization error code selected by the wallet.
    ///   - message: Local diagnostic that is not sent to the verifier automatically.
    public init(
        previewHandle: PresentationPreviewHandle,
        request: PresentationRequestContext,
        code: PresentationErrorCode,
        message: String
    ) {
        self.previewHandle = previewHandle
        self.request = request
        self.code = code
        self.message = message
    }
}

/// Opaque handle for one reviewed OpenID4VP presentation request.
public struct PresentationPreviewHandle: Equatable, Sendable, CustomStringConvertible {
    let value: String

    /// Creates a handle for bridge adapters and test fixtures. Production handles come from preview operations.
    ///
    /// - Parameter value: Nonempty opaque handle value supplied by wallet core.
    public init(value: String) {
        precondition(!value.isEmpty, "Presentation preview handle must not be empty.")
        self.value = value
    }

    /// Redacted representation that does not expose the opaque handle value.
    public var description: String { "PresentationPreviewHandle(<redacted>)" }
}

/// Reviewed OpenID4VP request metadata bound to an opaque presentation handle.
public struct PresentationPreview: Equatable, Sendable {
    /// Opaque handle required to submit, reject, or discard this reviewed request.
    public let previewHandle: PresentationPreviewHandle

    /// Verifier/request information shown to the user.
    public let request: PresentationRequestInfo

    /// Credentials that satisfy the request's DCQL queries.
    public let credentialOptions: [PresentationCredentialOption]

    /// Required DCQL credential query combinations that must be satisfied before submission.
    public let credentialRequirements: [PresentationCredentialRequirement]

    /// Creates a presentation preview.
    ///
    /// - Parameters:
    ///   - previewHandle: Opaque handle required to act on this reviewed request.
    ///   - request: Verifier and request metadata extracted from the
    ///     presentation request.
    ///   - credentialOptions: Wallet credentials that can satisfy the
    ///     requested credential queries.
    ///   - credentialRequirements: Required DCQL credential query combinations
    ///     that must be satisfied before submission.
    public init(
        previewHandle: PresentationPreviewHandle,
        request: PresentationRequestInfo,
        credentialOptions: [PresentationCredentialOption],
        credentialRequirements: [PresentationCredentialRequirement] = []
    ) {
        self.previewHandle = previewHandle
        self.request = request
        self.credentialOptions = credentialOptions
        self.credentialRequirements = credentialRequirements
    }
}

/// A required presentation credential-query combination.
public struct PresentationCredentialRequirement: Equatable, Sendable {
    /// Alternative query-id combinations that can satisfy this requirement.
    ///
    /// All query IDs in one option must be selected together. At least one option
    /// must be satisfied for the requirement to be complete.
    public let options: [[String]]

    /// Creates a required presentation credential-query combination.
    ///
    /// - Parameter options: Alternative query-id combinations that can satisfy
    ///   this requirement.
    public init(options: [[String]]) {
        precondition(
            Self.hasValidOptions(options),
            "A presentation credential requirement must contain non-empty options with non-blank query IDs."
        )
        self.options = options
    }

    static func hasValidOptions(_ options: [[String]]) -> Bool {
        !options.isEmpty && options.allSatisfy { option in
            !option.isEmpty && option.allSatisfy(isNonBlank)
        }
    }
}

/// Partial request context retained when an OpenID4VP request is invalid.
///
/// A reportable invalid request has a validated, non-blank client identifier.
/// Its nonce remains optional because a missing nonce can itself be the
/// validation error. A ready preview exposes a validated, non-optional nonce
/// through ``PresentationRequestInfo``.
public struct PresentationRequestContext: Equatable, Sendable {
    /// Validated OpenID4VP client identifier.
    public let clientID: String

    /// Typed metadata supplied by the OpenID4VP verifier when available.
    public let verifierMetadata: VerifierMetadata?

    /// Authentication established for the authorization request or its Request Object.
    public let requestAuthentication: PresentationRequestAuthentication

    /// Response URI used for direct-post responses when available.
    public let responseURI: URL?

    /// OpenID state value when available.
    public let state: String?

    /// OpenID nonce value when available.
    public let nonce: String?

    /// Response-encryption state selected for the request when available.
    public let responseEncryption: PresentationResponseEncryption

    /// Creates partial presentation request context.
    ///
    /// - Parameters:
    ///   - clientID: Validated OpenID4VP client identifier from the request.
    ///   - verifierMetadata: Typed metadata supplied by the verifier when available.
    ///   - requestAuthentication: Authentication facts established while resolving the request.
    ///   - responseURI: Response URI to which the wallet would submit the presentation or error, when provided.
    ///   - state: OpenID state value from the request, when provided.
    ///   - nonce: OpenID nonce value from the request, when provided. May be nil if the missing nonce is the validation error.
    ///   - responseEncryption: Response-encryption state selected for the request.
    public init(
        clientID: String,
        verifierMetadata: VerifierMetadata? = nil,
        requestAuthentication: PresentationRequestAuthentication,
        responseURI: URL? = nil,
        state: String? = nil,
        nonce: String? = nil,
        responseEncryption: PresentationResponseEncryption = .notRequired
    ) {
        precondition(
            Self.hasValidClientID(clientID),
            "A reportable presentation request must contain a non-blank client ID."
        )
        self.clientID = clientID
        self.verifierMetadata = verifierMetadata
        self.requestAuthentication = requestAuthentication
        self.responseURI = responseURI
        self.state = state
        self.nonce = nonce
        self.responseEncryption = responseEncryption
    }

    static func hasValidClientID(_ clientID: String) -> Bool {
        isNonBlank(clientID)
    }
}

/// Response encryption selected for an OpenID4VP presentation request.
public enum PresentationResponseEncryption: Equatable, Sendable {
    /// The reviewed request does not require an encrypted authorization response.
    case notRequired

    /// The reviewed request requires an encrypted authorization response.
    case required(ResponseEncryptionDetails)
}

/// Algorithms and verifier key identity selected for response encryption.
///
/// These values describe response protection and do not establish verifier trust.
public struct ResponseEncryptionDetails: Equatable, Sendable {
    /// JWE `alg` value selected by the protocol implementation.
    public let keyManagementAlgorithm: String

    /// JWE `enc` value selected by the protocol implementation.
    public let contentEncryptionAlgorithm: String

    /// Verifier-provided identifier of the selected encryption key.
    public let verifierKeyID: String?

    /// RFC 7638 thumbprint of the selected verifier encryption key.
    public let verifierKeyThumbprint: String

    /// Creates response-encryption details.
    ///
    /// - Parameters:
    ///   - keyManagementAlgorithm: JWE `alg` value selected for the response.
    ///   - contentEncryptionAlgorithm: JWE `enc` value selected for the response.
    ///   - verifierKeyID: Verifier-provided identifier of the selected public key.
    ///   - verifierKeyThumbprint: RFC 7638 thumbprint of the selected public key.
    public init(
        keyManagementAlgorithm: String,
        contentEncryptionAlgorithm: String,
        verifierKeyID: String?,
        verifierKeyThumbprint: String
    ) {
        self.keyManagementAlgorithm = keyManagementAlgorithm
        self.contentEncryptionAlgorithm = contentEncryptionAlgorithm
        self.verifierKeyID = verifierKeyID
        self.verifierKeyThumbprint = verifierKeyThumbprint
    }
}

/// Authentication established for an OpenID4VP authorization request.
public enum PresentationRequestAuthentication: Equatable, Sendable {
    /// No signed Request Object authenticated this request.
    case unauthenticated
    /// The Request Object was authenticated by the OpenID4VP client-ID layer.
    case authenticated(
        compactRequestObject: String,
        algorithm: String,
        keyID: String?,
        clientIDScheme: PresentationClientIDScheme
    )
}

/// Client identifier scheme established while authenticating a Request Object.
public enum PresentationClientIDScheme: Equatable, Sendable {
    /// The verifier is identified through pre-registered metadata.
    case preRegistered
    /// The verifier is identified by a redirect URI.
    case redirectURI
    /// The verifier is identified by an X.509 SAN DNS name.
    case x509SanDNS
    /// The verifier is identified by an X.509 certificate hash.
    case x509Hash
    /// The verifier is identified by a decentralized identifier.
    case decentralizedIdentifier
    /// The verifier is identified by a verifier attestation.
    case verifierAttestation
    /// The verifier is identified through OpenID Federation.
    case openIDFederation
}

/// Verifier, transaction, and response-protection metadata extracted from a presentation request.
public struct PresentationRequestInfo: Equatable, Sendable {
    /// OpenID4VP client identifier.
    public let clientID: String

    /// Typed metadata supplied by the OpenID4VP verifier when available.
    public let verifierMetadata: VerifierMetadata?

    /// Authentication established for the authorization request or its Request Object.
    public let requestAuthentication: PresentationRequestAuthentication

    /// Response URI used for direct-post responses when available.
    public let responseURI: URL?

    /// OpenID state value.
    public let state: String?

    /// OpenID nonce value.
    public let nonce: String

    /// Response-encryption state selected for this request.
    public let responseEncryption: PresentationResponseEncryption

    /// Decoded transaction data attached to the request.
    public let transactionData: [PresentationTransactionData]

    /// Creates presentation request information.
    ///
    /// - Parameters:
    ///   - clientID: OpenID4VP client identifier from the request.
    ///   - verifierMetadata: Typed metadata supplied by the verifier.
    ///   - requestAuthentication: Authentication facts established while resolving the request.
    ///   - responseURI: Direct-post response URI when available.
    ///   - state: OpenID state value from the request.
    ///   - nonce: OpenID nonce value from the request.
    ///   - responseEncryption: Response-encryption state selected for the request.
    ///   - transactionData: Decoded transaction data attached to the request.
    public init(
        clientID: String,
        verifierMetadata: VerifierMetadata? = nil,
        requestAuthentication: PresentationRequestAuthentication,
        responseURI: URL? = nil,
        state: String? = nil,
        nonce: String,
        responseEncryption: PresentationResponseEncryption,
        transactionData: [PresentationTransactionData] = []
    ) {
        precondition(
            Self.hasRequiredFields(clientID: clientID, nonce: nonce),
            "A presentation request must contain non-blank client ID and nonce values."
        )
        self.clientID = clientID
        self.verifierMetadata = verifierMetadata
        self.requestAuthentication = requestAuthentication
        self.responseURI = responseURI
        self.state = state
        self.nonce = nonce
        self.responseEncryption = responseEncryption
        self.transactionData = transactionData
    }

    static func hasRequiredFields(clientID: String, nonce: String) -> Bool {
        !clientID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !nonce.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

/// A wallet credential that satisfies one presentation credential query.
public struct PresentationCredentialOption: Equatable, Identifiable, Sendable {
    /// Stable UI identifier made from query and credential IDs.
    public var id: String { selection.id }

    /// Selection value to pass back when this option is shared.
    public var selection: PresentationCredentialSelection {
        PresentationCredentialSelection(queryID: queryID, credentialID: credentialID)
    }

    /// DCQL credential query identifier.
    public let queryID: String

    /// Wallet-local credential identifier.
    public let credentialID: String

    /// Whether the DCQL credential query allows sharing multiple matching credentials.
    public let multiple: Bool

    /// Credential format.
    public let format: String

    /// Issuer identifier when available.
    public let issuer: String?

    /// Subject identifier when available.
    public let subject: String?

    /// User-facing label when available.
    public let label: String?

    /// Parsed credential data encoded as JSON.
    public let credentialDataJSON: String

    /// Requested credential values shown for informed consent.
    public let disclosures: [PresentationDisclosure]

    /// Creates a presentation credential option.
    ///
    /// - Parameters:
    ///   - queryID: DCQL credential query identifier this option satisfies.
    ///   - credentialID: Wallet-local credential identifier.
    ///   - multiple: Whether the request allows multiple credentials for this query.
    ///   - format: Credential format.
    ///   - issuer: Issuer identifier when available.
    ///   - subject: Subject identifier when available.
    ///   - label: User-facing credential label when available.
    ///   - credentialDataJSON: Parsed credential data encoded as JSON.
    ///   - disclosures: Credential values requested from this credential.
    public init(
        queryID: String,
        credentialID: String,
        multiple: Bool = false,
        format: String,
        issuer: String?,
        subject: String?,
        label: String?,
        credentialDataJSON: String,
        disclosures: [PresentationDisclosure] = []
    ) {
        precondition(
            isNonBlank(queryID),
            "A presentation credential option must contain a non-blank query ID."
        )
        self.queryID = queryID
        self.credentialID = credentialID
        self.multiple = multiple
        self.format = format
        self.issuer = issuer
        self.subject = subject
        self.label = label
        self.credentialDataJSON = credentialDataJSON
        self.disclosures = disclosures
    }
}

/// A selected presentation credential option.
public struct PresentationCredentialSelection: Equatable, Hashable, Identifiable, Sendable {
    /// Stable UI identifier made from query and credential IDs.
    public var id: String { "\(queryID.count):\(queryID)\(credentialID.count):\(credentialID)" }

    /// DCQL credential query identifier.
    public let queryID: String

    /// Wallet-local credential identifier.
    public let credentialID: String

    /// Creates a selected presentation credential option.
    ///
    /// - Parameters:
    ///   - queryID: DCQL credential query identifier from the preview option.
    ///   - credentialID: Wallet-local credential identifier from the preview option.
    public init(queryID: String, credentialID: String) {
        self.queryID = queryID
        self.credentialID = credentialID
    }
}

/// A selected selectively disclosable presentation claim.
public struct PresentationDisclosureSelection: Equatable, Hashable, Identifiable, Sendable {
    /// Stable UI identifier made from query, credential, and disclosure path.
    public var id: String { "\(queryID.count):\(queryID)\(credentialID.count):\(credentialID)\(path.count):\(path)" }

    /// DCQL credential query identifier.
    public let queryID: String

    /// Wallet-local credential identifier.
    public let credentialID: String

    /// Opaque disclosure path token from ``PresentationDisclosure/path``.
    public let path: String

    /// Creates a selected presentation disclosure.
    ///
    /// - Parameters:
    ///   - queryID: DCQL credential query identifier from the preview option.
    ///   - credentialID: Wallet-local credential identifier from the preview option.
    ///   - path: Opaque disclosure path token from the preview disclosure.
    public init(queryID: String, credentialID: String, path: String) {
        self.queryID = queryID
        self.credentialID = credentialID
        self.path = path
    }
}

/// Credential value that may be shared.
public struct PresentationDisclosure: Equatable, Identifiable, Sendable {
    /// Stable display identifier.
    public var id: String { path }

    /// Opaque claim path token supplied by the presentation engine.
    public let path: String

    /// Claim name when known.
    public let name: String?

    /// Raw claim value encoded as JSON.
    public let valueJSON: String

    /// Human-readable value when trivially available.
    public let displayValue: String?

    /// Whether this value comes from a selectively disclosable claim.
    public let selectivelyDisclosable: Bool

    /// Whether the presentation request requires this claim for the matched query.
    public let required: Bool

    /// Whether apps may let the user toggle this claim for submission.
    public let selectable: Bool

    /// Creates a presentation disclosure.
    ///
    /// - Parameters:
    ///   - path: Opaque claim path token supplied by the presentation engine.
    ///   - name: Claim name when known.
    ///   - valueJSON: Raw claim value encoded as JSON.
    ///   - displayValue: Human-readable value when trivially available.
    ///   - selectivelyDisclosable: Whether the credential format can selectively
    ///     disclose this claim.
    ///   - required: Whether the request requires this claim.
    ///   - selectable: Whether apps may let the user toggle this claim.
    public init(
        path: String,
        name: String?,
        valueJSON: String,
        displayValue: String?,
        selectivelyDisclosable: Bool,
        required: Bool? = nil,
        selectable: Bool? = nil
    ) {
        self.path = path
        self.name = name
        self.valueJSON = valueJSON
        self.displayValue = displayValue
        self.selectivelyDisclosable = selectivelyDisclosable
        let resolvedRequired = required ?? !selectivelyDisclosable
        let resolvedSelectable = selectable ?? (selectivelyDisclosable && !resolvedRequired)
        precondition(
            Self.hasValidSelectionState(
                selectivelyDisclosable: selectivelyDisclosable,
                required: resolvedRequired,
                selectable: resolvedSelectable
            ),
            "A selectable disclosure must be selectively disclosable and optional."
        )
        self.required = resolvedRequired
        self.selectable = resolvedSelectable
    }

    static func hasValidSelectionState(
        selectivelyDisclosable: Bool,
        required: Bool,
        selectable: Bool
    ) -> Bool {
        !selectable || (selectivelyDisclosable && !required)
    }
}

/// Decoded transaction_data item from an OpenID4VP presentation request.
public struct PresentationTransactionData: Equatable, Sendable {
    /// Transaction data type.
    public let type: String

    /// Human-readable label from the accepted wallet profile.
    public let displayName: String

    /// Related DCQL credential query identifiers.
    public let credentialQueryIDs: [String]

    /// Profile-declared transaction-data fields the wallet accepts.
    public let supportedFields: [String]

    /// Decoded raw transaction data JSON.
    public let rawJSON: String

    /// Transaction type-specific details encoded as JSON.
    public let detailsJSON: String

    /// Creates transaction data for display.
    ///
    /// - Parameters:
    ///   - type: Transaction data type.
    ///   - displayName: Human-readable label from the accepted wallet profile.
    ///   - credentialQueryIDs: Related DCQL credential query identifiers.
    ///   - supportedFields: Profile-declared transaction-data fields.
    ///   - rawJSON: Decoded raw transaction data JSON.
    ///   - detailsJSON: Transaction type-specific details encoded as JSON.
    public init(
        type: String,
        displayName: String,
        credentialQueryIDs: [String],
        supportedFields: [String],
        rawJSON: String,
        detailsJSON: String
    ) {
        precondition(
            !credentialQueryIDs.isEmpty,
            "Transaction data must reference at least one credential query ID."
        )
        precondition(
            credentialQueryIDs.allSatisfy(isNonBlank),
            "Transaction data must contain non-blank credential query IDs."
        )
        self.type = type
        self.displayName = displayName
        self.credentialQueryIDs = credentialQueryIDs
        self.supportedFields = supportedFields
        self.rawJSON = rawJSON
        self.detailsJSON = detailsJSON
    }
}

/// OAuth 2.0 and OpenID4VP 1.0 authorization error codes supported by the wallet.
///
/// Use ``accessDenied`` when the user declines, the wallet has no requested
/// credential, or user authentication fails. Other cases describe protocol or
/// availability failures and should not be presented as end-user choices.
public enum PresentationErrorCode: String, Equatable, Sendable {
    /// The user or wallet denied the presentation request.
    case accessDenied = "access_denied"

    /// The authorization request is malformed or missing a required parameter.
    case invalidRequest = "invalid_request"

    /// The request's client identification is invalid.
    case invalidClient = "invalid_client"

    /// The requested scope is invalid, unknown, or unsupported.
    case invalidScope = "invalid_scope"

    /// The client is not authorized to make this presentation request.
    case unauthorizedClient = "unauthorized_client"

    /// The wallet does not support the requested response type.
    case unsupportedResponseType = "unsupported_response_type"

    /// The wallet cannot fulfill the request because of an unexpected error.
    case serverError = "server_error"

    /// The wallet cannot fulfill the request because it is temporarily unavailable.
    case temporarilyUnavailable = "temporarily_unavailable"

    /// The wallet does not support any requested verifiable-presentation format.
    case vpFormatsNotSupported = "vp_formats_not_supported"

    /// The wallet does not support the request's `request_uri_method`.
    case invalidRequestURIMethod = "invalid_request_uri_method"

    /// The request contains invalid or unsupported transaction data.
    case invalidTransactionData = "invalid_transaction_data"

    /// The requested wallet is unavailable.
    case walletUnavailable = "wallet_unavailable"

    /// OpenID4VP error code sent for this reason.
    public var errorCode: String { rawValue }
}

/// Lifecycle event emitted while issuance or presentation work is running.
public enum WalletEvent: CaseIterable, Equatable, Sendable {
    /// Credential offer resolution completed.
    case issuanceOfferResolved
    /// Wallet attestation was obtained.
    case issuanceAttestationObtained
    /// Issuance token was obtained.
    case issuanceTokenObtained
    /// Credential proof was signed.
    case issuanceProofSigned
    /// Credential was received from the issuer.
    case issuanceCredentialReceived
    /// Issuance was deferred by the issuer.
    case issuanceDeferred
    /// Credential was stored locally.
    case issuanceCredentialStored
    /// Issuance completed successfully.
    case issuanceCompleted
    /// Issuance failed.
    case issuanceFailed
    /// Presentation request was parsed.
    case presentationRequestParsed
    /// Presentation credentials were selected.
    case presentationCredentialsSelected
    /// Presentation was signed.
    case presentationSigned
    /// Presentation protocol response was prepared for delivery.
    case presentationResponsePrepared
    /// Presentation was submitted.
    case presentationSubmitted
    /// Presentation completed successfully.
    case presentationCompleted
    /// Presentation failed.
    case presentationFailed

    /// Creates an event from its stable wallet-core name.
    ///
    /// - Parameter name: Stable event name emitted by the wallet core.
    public init?(name: String) {
        guard let event = Self.allCases.first(where: { $0.name == name }) else {
            return nil
        }
        self = event
    }

    /// Stable event name emitted by the wallet core.
    public var name: String {
        switch self {
        case .issuanceOfferResolved: return "issuance_offer_resolved"
        case .issuanceAttestationObtained: return "issuance_attestation_obtained"
        case .issuanceTokenObtained: return "issuance_token_obtained"
        case .issuanceProofSigned: return "issuance_proof_signed"
        case .issuanceCredentialReceived: return "issuance_credential_received"
        case .issuanceDeferred: return "issuance_deferred"
        case .issuanceCredentialStored: return "issuance_credential_stored"
        case .issuanceCompleted: return "issuance_completed"
        case .issuanceFailed: return "issuance_failed"
        case .presentationRequestParsed: return "presentation_request_parsed"
        case .presentationCredentialsSelected: return "presentation_credentials_selected"
        case .presentationSigned: return "presentation_signed"
        case .presentationResponsePrepared: return "presentation_response_prepared"
        case .presentationSubmitted: return "presentation_submitted"
        case .presentationCompleted: return "presentation_completed"
        case .presentationFailed: return "presentation_failed"
        }
    }

    /// High-level workflow phase for the event.
    public var phase: WalletEventPhase {
        switch self {
        case .issuanceOfferResolved,
             .issuanceAttestationObtained,
             .issuanceTokenObtained,
             .issuanceProofSigned,
             .issuanceCredentialReceived,
             .issuanceDeferred,
             .issuanceCredentialStored,
             .issuanceCompleted,
             .issuanceFailed:
            return .issuance
        case .presentationRequestParsed,
             .presentationCredentialsSelected,
             .presentationSigned,
             .presentationResponsePrepared,
             .presentationSubmitted,
             .presentationCompleted,
             .presentationFailed:
            return .presentation
        }
    }

    /// High-level status for the event.
    public var status: WalletEventStatus {
        switch self {
        case .issuanceCompleted, .presentationCompleted:
            return .completed
        case .issuanceFailed, .presentationFailed:
            return .failed
        default:
            return .progress
        }
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

private func isNonBlank(_ value: String) -> Bool {
    !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
}
