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

/// Reference to a retained pre-authorized grant.
public struct PreAuthorizedIssuanceID: RawRepresentable, Hashable, Codable, Sendable {
    /// Persist this value to return to the reviewed offer.
    public let rawValue: String
    /// Restores a saved pre-authorized offer reference.
    /// - Parameter rawValue: Previously saved reference from this wallet.
    public init(rawValue: String) { self.rawValue = rawValue }
}

/// The grant and matching continuation selected during offer discovery.
public enum IssuanceGrantSession: Equatable, Sendable {
    /// Accept with the app's browser registration.
    case authorizationCode(AuthorizationIssuanceID)
    /// Accept with a transaction code or identified client when required.
    case preAuthorized(PreAuthorizedIssuanceID)
}

/// Issuer and credential preview together with the chosen grant.
public struct IssuanceSession: Equatable, Sendable {
    /// Stable offer reference for cancellation.
    public let id: IssuanceSessionID
    /// Issuer and credentials to review.
    public let offer: IssuanceOfferPreview
    /// The operations available for this offer.
    public var grant: IssuanceGrantSession {
        switch offer.grant {
        case .authorizationCode: return .authorizationCode(.init(rawValue: id.rawValue))
        case .preAuthorizedCode: return .preAuthorized(.init(rawValue: id.rawValue))
        }
    }

    /// Creates the display model for a resolved offer.
    /// - Parameters:
    ///   - id: Durable session identifier.
    ///   - offer: Resolved display metadata and selected grant.
    public init(id: String, offer: IssuanceOfferPreview) {
        self.id = IssuanceSessionID(rawValue: id)
        self.offer = offer
    }
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
