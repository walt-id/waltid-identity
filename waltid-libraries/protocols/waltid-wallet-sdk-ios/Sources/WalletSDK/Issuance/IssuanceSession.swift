import Foundation

/// Durable reference for cancelling a wallet issuance session.
public struct IssuanceSessionID: RawRepresentable, Hashable, Codable, Sendable {
    /// Persist this value when the app needs to restore the offer.
    public let rawValue: String
    /// Restores a saved session reference.
    /// - Parameter rawValue: Previously saved reference from this wallet.
    public init(rawValue: String) { self.rawValue = rawValue }
}

/// Browser-grant reference. The wallet validates the retained grant, callback and state on use.
public struct AuthorizationIssuanceID: RawRepresentable, Hashable, Codable, Sendable {
    /// Persist this value before opening the browser.
    public let rawValue: String
    /// Restores a saved browser continuation.
    /// - Parameter rawValue: Previously saved reference from this wallet.
    public init(rawValue: String) { self.rawValue = rawValue }
}

/// Identification required to accept a pre-authorized offer.
public enum IssuanceClientIdentification: Sendable {
    case anonymousAllowed
    case clientIDRequired
}

/// A browser offer reviewed in this wallet.
public struct AuthorizationCodeOffer: Sendable {
    /// Verified authorization server identifier used to choose the app’s registration.
    public let authorizationServer: String
    let id: IssuanceSessionID
    let wallet: Wallet

    /// Accepts the offer and prepares a browser request and its durable callback reference.
    public func beginAuthorization(using registration: IssuanceClientRegistration,
                                   credentials: [IssuanceCredentialSelection]? = nil) async throws -> PendingIssuanceAuthorization {
        try await wallet.beginAuthorization(id, registration: registration, credentials: credentials)
    }
}

/// A pre-authorized offer and the inputs required by its selected authorization server.
public struct PreAuthorizedOffer: Sendable {
    public let authorizationServer: String
    public let clientIdentification: IssuanceClientIdentification
    public let transactionCode: IssuanceTransactionCode?
    let id: IssuanceSessionID
    let wallet: Wallet

    /// Accepts the offer after the holder reviews the credentials and supplies any requested inputs.
    public func accept(transactionCode: String? = nil, credentials: [IssuanceCredentialSelection]? = nil,
                       clientID: String? = nil) async throws -> IssuanceOutcome {
        try await wallet.accept(id, transactionCode: transactionCode, credentials: credentials, clientID: clientID)
    }
}

/// The grant selected during discovery and the operation available after review.
public enum IssuanceGrantSession: Sendable {
    case authorizationCode(AuthorizationCodeOffer)
    case preAuthorized(PreAuthorizedOffer)
}

/// Issuer and credential preview together with the chosen grant.
public struct IssuanceSession: Sendable {
    /// Save this reference to reopen or cancel the offer.
    public let id: IssuanceSessionID
    public let offer: IssuanceOfferPreview
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
