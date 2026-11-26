import Foundation

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
