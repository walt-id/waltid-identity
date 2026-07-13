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

    static func details(
        id: String,
        title: String,
        issuer: String?,
        subject: String?,
        format: String,
        addedAt: Date?,
        credentialDataJSON: String
    ) -> CredentialDetails {
        let rawJSON = credentialDataJSON.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !rawJSON.isEmpty, let data = rawJSON.data(using: .utf8) else {
            return CredentialDetails(
                id: id,
                title: title,
                issuer: issuer,
                subject: subject,
                format: format,
                addedAt: addedAt,
                groups: []
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
                groups: []
            )
        }

        let groupedItems = object.keys.sorted().flatMap { key in
            let path = DisplayClaimPath.topLevel(key)
            return claimItems(path: path, label: CredentialDisplayVocabulary.humanizedLabel(key), value: object[key] as Any)
                .map { item in
                    (CredentialDisplayVocabulary.groupKind(for: path.components), item)
                }
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
            groups: groups
        )
    }

    private static func claimItem(path: DisplayClaimPath, label: String, value: Any) -> ClaimItem {
        ClaimItem(
            path: path.itemPath,
            label: label,
            value: displayValue(for: value, path: path),
            rawValue: rawString(value),
            roles: CredentialDisplayVocabulary.roles(for: path.components)
        )
    }

    private static func claimItems(path: DisplayClaimPath, label: String, value: Any) -> [ClaimItem] {
        let item = claimItem(path: path, label: label, value: value)
        if case .object = item.value {
            return flattenObjectForClaimRows(item)
        }
        return [item]
    }

    private static func flattenObjectForClaimRows(_ item: ClaimItem) -> [ClaimItem] {
        guard case .object(let entries) = item.value else {
            return [item]
        }

        return entries.flatMap { entry in
            let rows = flattenObjectForClaimRows(entry)
            if rows.count == 1,
               case .image = rows[0].value,
               rows[0].label == CredentialDisplayVocabulary.humanizedLabel(imageWrapperClaimName) {
                return [rows[0].relabelled(item.label)]
            }
            return rows
        }
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
            let items = object.keys.map { key in
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
    private static let imageWrapperClaimName = "elementValue"
}

private extension ClaimItem {
    func relabelled(_ label: String) -> ClaimItem {
        ClaimItem(
            path: path,
            label: label,
            value: value,
            rawValue: rawValue,
            roles: roles
        )
    }
}
