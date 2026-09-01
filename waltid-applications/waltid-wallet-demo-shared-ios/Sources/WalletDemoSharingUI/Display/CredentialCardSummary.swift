import Foundation
import WalletSDK

public struct CredentialCardSummary {
    public let title: String
    public let credentialType: String?
    public let holderName: String?
    public let issuer: String
    public let dateText: String?
    public let validityText: String?
    public let portraitData: Data?
    public let portraitMimeType: String?
    public let backgroundColor: String?
    public let backgroundImageURI: String?
    public let textColor: String?
    public let logoURI: String?
    public let logoAltText: String?

    public init(
        title: String,
        credentialType: String? = nil,
        holderName: String? = nil,
        issuer: String = "",
        dateText: String? = nil,
        validityText: String? = nil,
        portraitData: Data? = nil,
        portraitMimeType: String? = nil,
        backgroundColor: String? = nil,
        backgroundImageURI: String? = nil,
        textColor: String? = nil,
        logoURI: String? = nil,
        logoAltText: String? = nil
    ) {
        self.title = title
        self.credentialType = credentialType
        self.holderName = holderName
        self.issuer = issuer
        self.dateText = dateText
        self.validityText = validityText
        self.portraitData = portraitData
        self.portraitMimeType = portraitMimeType
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
}

extension CredentialDetails {
    public var cardSummary: CredentialCardSummary {
        let items = groups.flatMap(\.items)
        let holderName = [
            firstText(in: items, role: .givenName),
            firstText(in: items, role: .familyName)
        ]
            .compactMap { $0 }
            .joined(separator: " ")
        let portrait = firstImage(in: items)
        let expiryDate = firstExpiryDate(in: items)
        let addedDate = addedAt.map(Self.cardDateFormatter.string(from:))
        let issuerName = issuerDisplay?.name?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
            ?? issuer?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
            ?? CredentialDisplayText.unknown

        return CredentialCardSummary(
            title: CredentialTitles.displayName(
                format: format,
                credentialDataJSON: credentialDataJSON,
                displayName: credentialDisplay?.name,
                fallback: title
            ),
            credentialType: firstCredentialType(in: items),
            holderName: holderName.isEmpty ? subject : holderName,
            issuer: issuerName,
            dateText: expiryDate ?? addedDate,
            validityText: expiryDate.map(CredentialDisplayText.expires) ?? addedDate.map(CredentialDisplayText.added),
            portraitData: portrait?.data,
            portraitMimeType: portrait?.mimeType,
            backgroundColor: credentialDisplay?.backgroundColor,
            backgroundImageURI: credentialDisplay?.backgroundImageURI,
            textColor: credentialDisplay?.textColor,
            logoURI: credentialDisplay?.logoURI,
            logoAltText: credentialDisplay?.logoAltText
        )
    }

    private static let cardDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}

private extension String {
    var nonEmpty: String? {
        isEmpty ? nil : self
    }
}

private func firstText(in items: [ClaimItem], role: ClaimRole) -> String? {
    for item in items {
        if item.hasRole(role), let text = textValue(item.value) {
            return text
        }
        if case .object(let children) = item.value, let nested = firstText(in: children, role: role) {
            return nested
        }
    }
    return nil
}

private func firstExpiryDate(in items: [ClaimItem]) -> String? {
    for item in items {
        if item.hasRole(.expiryDate), let text = textValue(item.value) {
            return text
        }
        if case .object(let children) = item.value, let nested = firstExpiryDate(in: children) {
            return nested
        }
    }
    return nil
}

private func firstCredentialType(in items: [ClaimItem]) -> String? {
    for item in items {
        if item.hasRole(.credentialType) {
            if let type = (rawCredentialTypeValue(item) ?? credentialTypeValue(item.value)).flatMap(CredentialDisplayVocabulary.readableCredentialType) {
                return type
            }
        }
        if case .object(let children) = item.value, let nested = firstCredentialType(in: children) {
            return nested
        }
    }
    return nil
}

private func firstImage(in items: [ClaimItem]) -> (data: Data, mimeType: String)? {
    for item in items {
        if item.hasRole(.image), case .image(_, let data, let mimeType, _) = item.value {
            return (data, mimeType)
        }
        if case .object(let children) = item.value, let nested = firstImage(in: children) {
            return nested
        }
    }
    return nil
}

private func rawCredentialTypeValue(_ item: ClaimItem) -> String? {
    guard let rawValue = item.rawValue?.trimmingCharacters(in: .whitespacesAndNewlines),
          !rawValue.isEmpty,
          !rawValue.hasPrefix("["),
          !rawValue.hasPrefix("{") else {
        return nil
    }

    if rawValue.hasPrefix("\""), rawValue.hasSuffix("\"") {
        return String(rawValue.dropFirst().dropLast())
    }
    return rawValue
}

private func credentialTypeValue(_ value: DisplayValue) -> String? {
    switch value {
    case .list(let values):
        return values.compactMap(textValue).first { !CredentialDisplayVocabulary.isGenericCredentialType($0) } ??
            values.compactMap(textValue).first
    default:
        return textValue(value)
    }
}

private extension ClaimItem {
    func hasRole(_ role: ClaimRole) -> Bool {
        roles.contains(role)
    }
}

private func textValue(_ value: DisplayValue) -> String? {
    switch value {
    case .decodedText(let value), .text(let value), .number(let value), .raw(let value):
        return value
    case .bool(let value):
        return value ? "true" : "false"
    case .null, .object, .list, .image, .qrCode:
        return nil
    }
}
