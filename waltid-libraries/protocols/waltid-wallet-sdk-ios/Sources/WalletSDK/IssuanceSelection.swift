/// One requested credential instance and its existing wallet holder key.
public struct IssuanceHolderBinding: Equatable, Sendable {
    /// Identifier of the wallet key that will sign the credential proof.
    public let keyID: String
    /// Holder DID when the issuer requires DID binding.
    public let did: String?

    /// Creates a holder binding without generating a key.
    /// - Parameters:
    ///   - keyID: Existing wallet signing key identifier.
    ///   - did: Optional DID associated with that key.
    public init(keyID: String, did: String? = nil) {
        self.keyID = keyID
        self.did = did
    }
}

/// Existing keys or an explicit number of keys prepared within session acceptance.
public enum IssuanceCredentialHolders: Equatable, Sendable {
    /// Uses one existing wallet holder key per requested credential copy.
    case existing([IssuanceHolderBinding])
    /// Prepares the requested number of new holder keys during session acceptance.
    case newKeys(count: Int)
}

/// An accepted credential configuration and the explicit copies requested for it.
public struct IssuanceCredentialSelection: Equatable, Sendable {
    /// Configuration identifier from the reviewed offer.
    public let configurationID: String
    /// Issuer-granted dataset identifier, when known; never invent a dataset identifier.
    public let credentialIdentifier: String?
    /// One entry per requested copy; the issuer's batch limit does not create copies automatically.
    public let holders: IssuanceCredentialHolders

    /// Creates an explicit selection for either issuance grant.
    /// - Parameters:
    ///   - configurationID: Offered credential configuration identifier.
    ///   - credentialIdentifier: Optional identifier granted by the issuer.
    ///   - holders: Existing keys or a count of new keys, validated against issuer capabilities by the core.
    public init(configurationID: String, credentialIdentifier: String? = nil, holders: IssuanceCredentialHolders) {
        self.configurationID = configurationID
        self.credentialIdentifier = credentialIdentifier
        self.holders = holders
    }
}

/// One configuration or issuer-granted dataset addressed by a credential request.
public struct IssuanceCredentialTarget: Equatable, Sendable {
    /// Credential configuration identifier.
    public let configurationID: String
    /// Issuer-granted dataset identifier, if the token granted one.
    public let credentialIdentifier: String?

    /// Creates a target descriptor.
    /// - Parameters:
    ///   - configurationID: Credential configuration identifier.
    ///   - credentialIdentifier: Optional issuer-granted dataset identifier.
    public init(configurationID: String, credentialIdentifier: String? = nil) {
        self.configurationID = configurationID
        self.credentialIdentifier = credentialIdentifier
    }
}

/// Stage at which processing a credential target stopped.
public enum IssuanceFailureStage: Equatable, Sendable {
    /// Holder proof creation failed.
    case proof
    /// The request failed; a transport failure may leave the issuer's processing status unknown.
    case request
    /// The issuer response could not be accepted.
    case response
    /// Credential or continuation persistence failed.
    case storage
    /// A required persistence observer failed after a write.
    case observer
}

/// Sanitized target failure and the remaining targets that were not attempted.
public struct IssuanceTargetFailure: Equatable, Sendable {
    /// Target whose processing stopped.
    public let target: IssuanceCredentialTarget
    /// Stage where processing stopped.
    public let stage: IssuanceFailureStage
    /// Remaining targets, in request order; retrying must not redeem the original grant again.
    public let notAttempted: [IssuanceCredentialTarget]

    /// Creates a target failure without protocol secrets or issuer response bodies.
    /// - Parameters:
    ///   - target: Failed target.
    ///   - stage: Failed processing stage.
    ///   - notAttempted: Targets not requested after the failure.
    public init(target: IssuanceCredentialTarget, stage: IssuanceFailureStage, notAttempted: [IssuanceCredentialTarget]) {
        self.target = target
        self.stage = stage
        self.notAttempted = notAttempted
    }
}
