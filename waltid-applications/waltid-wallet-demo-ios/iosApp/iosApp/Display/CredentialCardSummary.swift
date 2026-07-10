import Foundation

struct CredentialCardSummary {
    let title: String
    let credentialType: String?
    let holderName: String?
    let dateText: String?
    let validityText: String?
    let portraitData: Data?
    let portraitMimeType: String?
}

extension CredentialDetails {
    var cardSummary: CredentialCardSummary {
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

        return CredentialCardSummary(
            title: title,
            credentialType: firstCredentialType(in: items),
            holderName: holderName.isEmpty ? subject : holderName,
            dateText: expiryDate ?? addedDate,
            validityText: expiryDate.map(CredentialDisplayText.expires) ?? addedDate.map(CredentialDisplayText.added),
            portraitData: portrait?.data,
            portraitMimeType: portrait?.mimeType
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
            if let type = credentialTypeValue(item.value).flatMap(CredentialDisplayVocabulary.readableCredentialType) {
                return type
            }
        }
        if case .object(let children) = item.value, let nested = firstCredentialType(in: children) {
            return nested
        }
    }
    return nil
}

private func firstImage(in items: [ClaimItem]) -> (data: Data, mimeType: String?)? {
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
    case .null, .object, .list, .image:
        return nil
    }
}
