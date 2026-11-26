import Foundation

/// Input used to start either OpenID4VCI issuance grant.
public struct IssuanceRequest: Equatable, Sendable {
    /// Credential-offer URL to resolve.
    public let offer: URL

    /// Optional identifier of the holder key used for DPoP and credential proofs.
    public let keyID: String?

    /// Optional holder DID URL used when the credential requires DID binding.
    public let did: String?
    /// Minimum host/issuer policy required of the identity's retained key policy.
    public let keyPolicy: SigningIdentityKeyPolicy

    /// Creates an issuance request.
    ///
    /// - Parameters:
    ///   - offer: Credential-offer URL to resolve.
    ///   - keyID: Optional identifier of the selected holder key.
    ///   - did: Optional holder DID URL identifying the selected key.
    ///   - keyPolicy: Minimum required identity policy; this does not claim ecosystem certification.
    public init(
        offer: URL,
        keyID: String? = nil,
        did: String? = nil,
        keyPolicy: SigningIdentityKeyPolicy = .generalPurpose
    ) {
        self.offer = offer
        self.keyID = keyID
        self.did = did
        self.keyPolicy = keyPolicy
    }
}

/// OAuth registration used by the app when starting issuance.
public struct IssuanceClientRegistration: Equatable, Sendable {
    /// Registered OAuth client identifier.
    public let clientID: String
    /// Exact callback URI registered with the authorization server.
    public let redirectURI: URL

    /// Creates an application-owned registration.
    /// - Parameters:
    ///   - clientID: Identifier of the registered OAuth client.
    ///   - redirectURI: Registered callback URI handled by the app.
    public init(clientID: String, redirectURI: URL) {
        self.clientID = clientID
        self.redirectURI = redirectURI
    }
}
