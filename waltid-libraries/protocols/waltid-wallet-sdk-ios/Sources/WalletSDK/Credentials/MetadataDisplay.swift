import Foundation

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
        name: String? = nil,
        locale: String? = nil,
        logoURI: String? = nil,
        logoAltText: String? = nil,
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
