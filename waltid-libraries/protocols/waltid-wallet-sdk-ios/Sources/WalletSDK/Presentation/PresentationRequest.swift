import Foundation

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
    ///   - requestAuthentication: Authentication facts established while resolving the request.
    ///   - verifierMetadata: Typed metadata supplied by the verifier when available.
    ///   - responseURI: Response URI to which the wallet would submit the presentation or error, when provided.
    ///   - state: OpenID state value from the request, when provided.
    ///   - nonce: OpenID nonce value from the request, when provided. May be nil if the missing nonce is the validation error.
    ///   - responseEncryption: Response-encryption state selected for the request.
    public init(
        clientID: String,
        requestAuthentication: PresentationRequestAuthentication,
        responseEncryption: PresentationResponseEncryption,
        verifierMetadata: VerifierMetadata? = nil,
        responseURI: URL? = nil,
        state: String? = nil,
        nonce: String? = nil
    ) throws {
        guard Self.hasValidClientID(clientID) else {
            throw WalletError.invalidInput("A reportable presentation request must contain a non-blank client ID.")
        }
        self.clientID = clientID
        self.verifierMetadata = verifierMetadata
        self.requestAuthentication = requestAuthentication
        self.responseURI = responseURI
        self.state = state
        self.nonce = nonce
        self.responseEncryption = responseEncryption
    }

    static func hasValidClientID(_ clientID: String) -> Bool {
        isPresentationIdentifierNonBlank(clientID)
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

    /// Serialized OpenID4VP response mode requested by the verifier.
    public let responseMode: String?

    /// Decoded transaction data attached to the request.
    public let transactionData: [PresentationTransactionData]

    /// Creates presentation request information.
    ///
    /// - Parameters:
    ///   - clientID: OpenID4VP client identifier from the request.
    ///   - requestAuthentication: Authentication facts established while resolving the request.
    ///   - nonce: OpenID nonce value from the request.
    ///   - responseEncryption: Response-encryption state selected for the request.
    ///   - verifierMetadata: Typed metadata supplied by the verifier.
    ///   - responseURI: Direct-post response URI when available.
    ///   - state: OpenID state value from the request.
    ///   - responseMode: Serialized OpenID4VP response mode requested by the verifier.
    ///   - transactionData: Decoded transaction data attached to the request.
    public init(
        clientID: String,
        requestAuthentication: PresentationRequestAuthentication,
        nonce: String,
        responseEncryption: PresentationResponseEncryption,
        verifierMetadata: VerifierMetadata? = nil,
        responseURI: URL? = nil,
        state: String? = nil,
        responseMode: String? = nil,
        transactionData: [PresentationTransactionData] = []
    ) throws {
        guard Self.hasRequiredFields(clientID: clientID, nonce: nonce) else {
            throw WalletError.invalidInput("A presentation request must contain non-blank client ID and nonce values.")
        }
        self.clientID = clientID
        self.verifierMetadata = verifierMetadata
        self.requestAuthentication = requestAuthentication
        self.responseURI = responseURI
        self.state = state
        self.nonce = nonce
        self.responseEncryption = responseEncryption
        self.responseMode = responseMode
        self.transactionData = transactionData
    }

    static func hasRequiredFields(clientID: String, nonce: String) -> Bool {
        !clientID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !nonce.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}
