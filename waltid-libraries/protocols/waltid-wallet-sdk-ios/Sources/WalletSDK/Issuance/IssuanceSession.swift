import Foundation

/// Durable reference for cancelling a wallet issuance session.
public struct IssuanceSessionID: RawRepresentable, Hashable, Codable, Sendable, CustomStringConvertible {
    /// Printable form for diagnostics that omits the saved reference.
    public var description: String { "IssuanceSessionID(<redacted>)" }

    /// Persist this value when the app needs to restore the offer.
    public let rawValue: String
    /// Restores a saved session reference.
    /// - Parameter rawValue: Previously saved reference from this wallet.
    public init(rawValue: String) { self.rawValue = rawValue }
}

/// Browser-grant reference. The wallet validates the retained grant, callback and state on use.
public struct AuthorizationIssuanceID: RawRepresentable, Hashable, Codable, Sendable, CustomStringConvertible {
    /// Printable form for diagnostics that omits the saved reference.
    public var description: String { "AuthorizationIssuanceID(<redacted>)" }

    /// Persist this value before opening the browser.
    public let rawValue: String
    /// Restores a saved browser continuation.
    /// - Parameter rawValue: Previously saved reference from this wallet.
    public init(rawValue: String) { self.rawValue = rawValue }
}

/// Identification required to accept a pre-authorized offer.
public enum IssuanceClientIdentification: Sendable {
    /// The server accepts this wallet without a client identifier.
    case anonymousAllowed
    /// The host supplies an identifier registered at the selected server.
    case clientIDRequired
}

/// A browser offer reviewed in this wallet.
public struct AuthorizationCodeOffer: Sendable {
    /// Verified authorization server identifier used to choose the app’s registration.
    public let authorizationServer: String
    let id: IssuanceSessionID
    let wallet: Wallet

    /// Accepts the offer and prepares a browser request and its durable callback reference.
    /// - Parameters:
    ///   - registration: Client identifier and exact redirect registered at the selected server.
    ///   - credentials: Credential configurations and holder bindings accepted by the holder.
    public func beginAuthorization(using registration: IssuanceClientRegistration,
                                   credentials: [IssuanceCredentialSelection]? = nil) async throws -> PendingIssuanceAuthorization {
        try await wallet.beginAuthorization(id, registration: registration, credentials: credentials)
    }
}

/// A pre-authorized offer and the inputs required by its selected authorization server.
public struct PreAuthorizedOffer: Sendable {
    /// Verified server identifier for choosing a registration.
    public let authorizationServer: String
    /// Effective identification requirement for this wallet and server.
    public let clientIdentification: IssuanceClientIdentification
    /// Input requested by the credential issuer when the holder needs a separate code.
    public let transactionCode: IssuanceTransactionCode?
    let id: IssuanceSessionID
    let wallet: Wallet

    /// Accepts the offer after the holder reviews the credentials and supplies any requested inputs.
    /// - Parameters:
    ///   - transactionCode: Holder-entered code for an offer with a transaction-code prompt.
    ///   - credentials: Credential configurations and holder bindings accepted by the holder.
    ///   - clientID: Registered client identifier when identification is required.
    public func accept(transactionCode: String? = nil, credentials: [IssuanceCredentialSelection]? = nil,
                       clientID: String? = nil) async throws -> IssuanceOutcome {
        try await wallet.accept(id, transactionCode: transactionCode, credentials: credentials, clientID: clientID)
    }
}

/// The grant selected during discovery and the operation available after review.
public enum IssuanceGrantSession: Sendable {
    /// A reviewed offer ready for browser authorization.
    case authorizationCode(AuthorizationCodeOffer)
    /// A reviewed offer ready for pre-authorized issuance.
    case preAuthorized(PreAuthorizedOffer)
}

/// Issuer and credential preview together with the chosen grant.
public struct IssuanceSession: Sendable {
    /// Save this reference to reopen or cancel the offer.
    public let id: IssuanceSessionID
    /// Issuer and credential information to show before acceptance.
    public let offer: IssuanceOfferPreview
    /// The grant selected by discovery and its available operation.
    public let grant: IssuanceGrantSession

    init(_ review: IssuanceOfferReview, wallet: Wallet) {
        id = .init(rawValue: review.id)
        offer = review.offer
        switch offer.grant {
        case .authorizationCode:
            grant = .authorizationCode(.init(authorizationServer: review.authorizationServer, id: id, wallet: wallet))
        case .preAuthorizedCode:
            grant = .preAuthorized(.init(authorizationServer: review.authorizationServer, clientIdentification: review.clientIdentification,
                                         transactionCode: offer.transactionCode, id: id, wallet: wallet))
        }
    }
}

struct IssuanceOfferReview: Sendable {
    let id: String
    let offer: IssuanceOfferPreview
    let authorizationServer: String
    let clientIdentification: IssuanceClientIdentification
}

/// A browser request paired with the callback continuation that created it.
public struct PendingIssuanceAuthorization: Sendable {
    /// Durable callback reference for a reopened wallet.
    public let id: AuthorizationIssuanceID
    /// Browser URL, PKCE challenge and expected redirect.
    public let request: IssuanceAuthorization
    let wallet: Wallet

    /// Completes this authorization after the browser returns to the app.
    /// - Parameter callback: URI delivered to the host by the browser.
    public func complete(_ callback: URL) async throws -> IssuanceOutcome {
        try await wallet.continueAuthorizationIssuance(sessionID: id, callbackURI: callback)
    }
}

/// Current retained state, refreshed by listing issuance continuations.
public enum IssuanceContinuationStatus: Equatable, Sendable {
    /// No recognized continuation state has been supplied.
    case unresolved
    /// The issuer is still processing the credential request.
    case awaitingIssuer
    /// Issued credentials are waiting to be saved locally.
    case awaitingLocalSave
    /// The issuer request outcome is uncertain; automatic retry is unsafe.
    case remoteOutcomeUncertain
    /// The local save outcome is uncertain; automatic takeover is unsafe.
    case storageOutcomeUncertain
}

/// Issuance continuation awaiting issuer processing or local credential storage.
public struct DeferredCredential: Equatable, Sendable {
    /// Opaque identifier used to resume this deferred operation.
    public let id: String

    /// Credential configuration, or nil when an isolated request did not supply one.
    public let credentialConfigurationID: String?

    /// Issuer-granted dataset identifier, when available.
    public let credentialIdentifier: String?

    /// Issuer-recommended minimum polling interval in seconds.
    public let intervalSeconds: Int64?

    /// Current retained state of the continuation.
    public let status: IssuanceContinuationStatus

    /// Issuer display metadata only, without credential values or protocol secrets.
    public let displayMetadataJSON: String?

    /// Creates a deferred credential continuation.
    ///
    /// - Parameters:
    ///   - id: Opaque deferred operation identifier.
    ///   - credentialConfigurationID: Credential configuration, if supplied by the original request.
    ///   - intervalSeconds: Issuer-recommended polling interval.
    ///   - credentialIdentifier: Optional issuer-granted dataset identifier.
    ///   - status: Current retained state of the continuation.
    ///   - displayMetadataJSON: Optional issuer display metadata, without credential values or protocol secrets.
    public init(id: String, credentialConfigurationID: String?, intervalSeconds: Int64?, credentialIdentifier: String? = nil,
                status: IssuanceContinuationStatus = .unresolved, displayMetadataJSON: String? = nil) {
        self.id = id
        self.credentialConfigurationID = credentialConfigurationID
        self.intervalSeconds = intervalSeconds
        self.credentialIdentifier = credentialIdentifier
        self.status = status
        self.displayMetadataJSON = displayMetadataJSON
    }
}
