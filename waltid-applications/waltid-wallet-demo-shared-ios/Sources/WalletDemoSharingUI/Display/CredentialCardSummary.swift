import Foundation
import WalletSDK

public struct CredentialCardItem: Identifiable {
    public let id: String
    public let summary: CredentialCardSummary

    public init(id: String, summary: CredentialCardSummary) {
        self.id = id
        self.summary = summary
    }
}

public struct CredentialCardSummary {
    public let title: String
    public let issuer: String
    public let backgroundColor: String?
    public let backgroundImageURI: String?
    public let textColor: String?
    public let logoURI: String?
    public let logoAltText: String?

    public init(
        title: String,
        issuer: String = "",
        backgroundColor: String? = nil,
        backgroundImageURI: String? = nil,
        textColor: String? = nil,
        logoURI: String? = nil,
        logoAltText: String? = nil
    ) {
        self.title = title
        self.issuer = issuer
        self.backgroundColor = backgroundColor
        self.backgroundImageURI = backgroundImageURI
        self.textColor = textColor
        self.logoURI = logoURI
        self.logoAltText = logoAltText
    }

    public static func offered(from credential: IssuanceCredentialPreview) -> CredentialCardSummary {
        CredentialCardSummary(
            title: CredentialTitles.displayName(
                format: credential.format,
                credentialDataJSON: credential.typePayloadJSON,
                displayName: credential.name,
                fallback: credential.format
            ),
            backgroundColor: credential.backgroundColor,
            backgroundImageURI: credential.backgroundImageURI?.absoluteString,
            textColor: credential.textColor,
            logoURI: credential.logoURI?.absoluteString,
            logoAltText: credential.logoAltText
        )
    }

    /// The list card uses issuer artwork and a title; personal claim groups stay unopened.
    public static func stored(from credential: Credential) -> CredentialCardSummary {
        let display = StoredCredentialMetadataParser.credentialDisplay(
            from: credential.metadataJSON, preferredLocales: Locale.preferredLanguages
        )
        return CredentialCardSummary(
            title: CredentialTitles.displayName(
                format: credential.format,
                credentialDataJSON: credential.credentialDataJSON,
                displayName: display?.name,
                fallback: credential.label ?? credential.format
            ),
            backgroundColor: display?.backgroundColor,
            backgroundImageURI: display?.backgroundImageURI,
            textColor: display?.textColor,
            logoURI: display?.logoURI,
            logoAltText: display?.logoAltText
        )
    }
}

extension CredentialDetails {
    public var cardSummary: CredentialCardSummary {
        CredentialCardSummary(
            title: cardTitle,
            issuer: issuerDisplay?.name?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                ?? issuer?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                ?? CredentialDisplayText.unknown,
            backgroundColor: credentialDisplay?.backgroundColor,
            backgroundImageURI: credentialDisplay?.backgroundImageURI,
            textColor: credentialDisplay?.textColor,
            logoURI: credentialDisplay?.logoURI,
            logoAltText: credentialDisplay?.logoAltText
        )
    }
}

private extension String {
    var nonEmpty: String? { isEmpty ? nil : self }
}
