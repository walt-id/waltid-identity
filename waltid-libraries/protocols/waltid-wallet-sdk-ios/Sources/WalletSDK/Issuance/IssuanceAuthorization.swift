import Foundation

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
