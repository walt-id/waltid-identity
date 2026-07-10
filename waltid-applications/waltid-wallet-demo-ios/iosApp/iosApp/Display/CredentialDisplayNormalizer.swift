import Foundation
import WalletSDK

enum CredentialDisplayNormalizer {

    static func details(for credential: Credential) -> CredentialDetails {
        details(
            id: credential.id,
            title: credential.label ?? credential.format,
            issuer: credential.issuer,
            subject: credential.subject,
            format: credential.format,
            addedAt: credential.addedAt,
            credentialDataJSON: credential.credentialDataJSON
        )
    }

    static func details(for option: PresentationCredentialOption) -> CredentialDetails {
        let parsed = details(
            id: option.credentialID,
            title: option.label ?? option.format,
            issuer: option.issuer,
            subject: option.subject,
            format: option.format,
            addedAt: nil,
            credentialDataJSON: option.credentialDataJSON
        )
        let disclosures = ClaimGroup(
            title: CredentialDisplayVocabulary.requestedDisclosuresTitle,
            items: option.disclosures.enumerated().map { index, disclosure in
                let label = CredentialDisplayVocabulary.disclosureLabel(name: disclosure.name, path: disclosure.path)
                let path = DisplayClaimPath.disclosure(index: index, rawPath: disclosure.path, label: label)
                return ClaimItem(
                    path: path.itemPath,
                    label: label,
                    value: displayValue(fromJSON: disclosure.valueJSON, displayValue: disclosure.displayValue, path: path),
                    rawValue: disclosure.valueJSON,
                    requested: true,
                    shareable: disclosure.selectivelyDisclosable,
                    roles: CredentialDisplayVocabulary.roles(for: path.components)
                )
            }
        )
        return CredentialDetails(
            id: parsed.id,
            title: parsed.title,
            issuer: parsed.issuer,
            subject: parsed.subject,
            format: parsed.format,
            addedAt: parsed.addedAt,
            groups: [disclosures] + parsed.groups,
            technicalGroups: parsed.technicalGroups
        )
    }

    private static func displayValue(fromJSON rawJSON: String, displayValue: String?, path: DisplayClaimPath) -> DisplayValue {
        let trimmed = rawJSON.trimmingCharacters(in: .whitespacesAndNewlines)
        if let data = trimmed.data(using: .utf8),
           let json = try? JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]) {
            let parsed = self.displayValue(for: json, path: path)
            if case .text = parsed, let displayValue, !displayValue.isEmpty {
                return .text(displayValue)
            }
            return parsed
        }

        if let displayValue, !displayValue.isEmpty {
            return .text(displayValue)
        }
        return .raw(rawJSON)
    }

    private static func details(
        id: String,
        title: String,
        issuer: String?,
        subject: String?,
        format: String,
        addedAt: Date?,
        credentialDataJSON: String?
    ) -> CredentialDetails {
        let rawJSON = credentialDataJSON?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !rawJSON.isEmpty, let data = rawJSON.data(using: .utf8) else {
            return CredentialDetails(
                id: id,
                title: title,
                issuer: issuer,
                subject: subject,
                format: format,
                addedAt: addedAt,
                groups: [],
                technicalGroups: []
            )
        }

        guard let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return CredentialDetails(
                id: id,
                title: title,
                issuer: issuer,
                subject: subject,
                format: format,
                addedAt: addedAt,
                groups: [],
                technicalGroups: [
                    ClaimGroup(
                        title: CredentialDisplayVocabulary.rawCredentialDataTitle,
                        items: [ClaimItem(path: ClaimItemPath.root(), label: CredentialDisplayVocabulary.rawCredentialDataLabel, value: .raw(rawJSON), rawValue: rawJSON, requested: false, shareable: false)]
                    )
                ]
            )
        }

        let groupedItems = object.keys.sorted().map { key in
            let path = DisplayClaimPath.topLevel(key)
            return (
                CredentialDisplayVocabulary.groupKind(for: path.components),
                claimItem(path: path, label: CredentialDisplayVocabulary.humanizedLabel(key), value: object[key] as Any)
            )
        }
        let groups = Dictionary(grouping: groupedItems, by: { $0.0 })
            .sorted { $0.key.order < $1.key.order }
            .map { ClaimGroup(title: $0.key.title, items: $0.value.map(\.1)) }

        return CredentialDetails(
            id: id,
            title: title,
            issuer: issuer,
            subject: subject,
            format: format,
            addedAt: addedAt,
            groups: groups,
            technicalGroups: [
                ClaimGroup(
                    title: CredentialDisplayVocabulary.rawCredentialDataTitle,
                    items: [ClaimItem(path: ClaimItemPath.root(), label: CredentialDisplayVocabulary.credentialDataJSONLabel, value: .raw(rawJSON), rawValue: rawJSON, requested: false, shareable: false)]
                )
            ]
        )
    }

    private static func claimItems(fromJSON rawJSON: String, pathPrefix: DisplayClaimPath, fallbackLabel: String) -> [ClaimItem] {
        let trimmed = rawJSON.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let data = trimmed.data(using: .utf8) else {
            return []
        }

        guard let json = try? JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]) else {
            return [
                ClaimItem(
                    path: pathPrefix.itemPath,
                    label: fallbackLabel,
                    value: .raw(trimmed),
                    rawValue: trimmed,
                    requested: false,
                    shareable: false,
                    roles: CredentialDisplayVocabulary.roles(for: pathPrefix.components)
                )
            ]
        }

        if let object = json as? [String: Any] {
            return object.keys.sorted().map { key in
                claimItem(path: pathPrefix.child(key), label: CredentialDisplayVocabulary.humanizedLabel(key), value: object[key] as Any)
            }
        }

        return [
            ClaimItem(
                path: pathPrefix.itemPath,
                label: fallbackLabel,
                value: displayValue(for: json, path: pathPrefix),
                rawValue: rawString(json),
                requested: false,
                shareable: false,
                roles: CredentialDisplayVocabulary.roles(for: pathPrefix.components)
            )
        ]
    }

    private static func claimItem(path: DisplayClaimPath, label: String, value: Any) -> ClaimItem {
        ClaimItem(
            path: path.itemPath,
            label: label,
            value: displayValue(for: value, path: path),
            rawValue: rawString(value),
            requested: false,
            shareable: true,
            roles: CredentialDisplayVocabulary.roles(for: path.components)
        )
    }

    private static func displayValue(for value: Any, path: DisplayClaimPath) -> DisplayValue {
        if value is NSNull {
            return .null
        }
        if let string = value as? String {
            if let dateText = epochDateStringIfTemporal(value: string, path: path) {
                return .text(dateText)
            }
            return CredentialDisplayValueDecoder.decodedValue(
                for: string,
                path: path,
                renderJSON: { json, jsonPath in displayValue(for: json, path: jsonPath) }
            ) ?? .text(string)
        }
        if let number = value as? NSNumber {
            if CFGetTypeID(number) == CFBooleanGetTypeID() {
                return .bool(number.boolValue)
            }
            if let dateText = epochDateStringIfTemporal(value: number.stringValue, path: path) {
                return .text(dateText)
            }
            return .number(number.stringValue)
        }
        if let object = value as? [String: Any] {
            let items = object.keys.sorted().map { key in
                claimItem(path: path.child(key), label: CredentialDisplayVocabulary.humanizedLabel(key), value: object[key] as Any)
            }
            return .object(items)
        }
        if let list = value as? [Any] {
            if let image = CredentialDisplayValueDecoder.imageDisplayValue(
                for: list,
                roles: CredentialDisplayVocabulary.roles(for: path.components)
            ) {
                return image
            }
            return .list(list.enumerated().map { index, element in
                displayValue(for: element, path: path.indexed(index))
            })
        }
        return .raw(String(describing: value))
    }

    private static func rawString(_ value: Any) -> String? {
        if JSONSerialization.isValidJSONObject(value),
           let data = try? JSONSerialization.data(withJSONObject: value),
           let string = String(data: data, encoding: .utf8) {
            return string
        }
        if value is NSNull {
            return "null"
        }
        return String(describing: value)
    }

    private static func epochDateStringIfTemporal(value: String, path: DisplayClaimPath) -> String? {
        guard CredentialDisplayVocabulary.roles(for: path.components).contains(.temporal),
              let rawEpoch = Int64(value.trimmingCharacters(in: .whitespacesAndNewlines)),
              rawEpoch >= minimumCredibleEpochSeconds else {
            return nil
        }
        let epochSeconds = rawEpoch >= epochMillisecondsThreshold ? rawEpoch / 1_000 : rawEpoch
        return utcDateFormatter.string(from: Date(timeIntervalSince1970: TimeInterval(epochSeconds)))
    }

    private static let utcDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private static let minimumCredibleEpochSeconds: Int64 = 100_000_000
    private static let epochMillisecondsThreshold: Int64 = 10_000_000_000
}
