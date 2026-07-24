import Foundation
import WalletSDK

/// Parses sidecar credential metadata JSON written by the wallet on receive.
///
/// The wallet stores OpenID4VCI issuer display under `issuerDisplay` as an array of
/// `{ name, locale, logo: { uri, alt_text } }` entries.
enum StoredCredentialMetadataParser {
    static func issuerDisplay(
        from metadataJSON: String?,
        preferredLocales: [String] = []
    ) -> MetadataDisplay? {
        let raw = metadataJSON?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !raw.isEmpty,
              let data = raw.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }

        let displays: [[String: Any]]
        switch root["issuerDisplay"] {
        case let array as [[String: Any]]:
            displays = array
        case let object as [String: Any]:
            displays = [object]
        default:
            return nil
        }
        guard !displays.isEmpty else { return nil }

        guard let selected = selectPreferredDisplay(displays, preferredLocales: preferredLocales) else {
            return nil
        }

        let logo = selected["logo"] as? [String: Any]
        let name = stringValue(selected["name"])
        let locale = stringValue(selected["locale"])
        let logoURI = stringValue(logo?["uri"])
        let logoAltText = stringValue(logo?["alt_text"]) ?? stringValue(logo?["altText"])

        guard name != nil || logoURI != nil else { return nil }
        return MetadataDisplay(
            name: name,
            locale: locale,
            logoURI: logoURI,
            logoAltText: logoAltText
        )
    }

    private static func selectPreferredDisplay(
        _ displays: [[String: Any]],
        preferredLocales: [String]
    ) -> [String: Any]? {
        let preferences = preferredLocales.compactMap(normalizeLocale(_:))
        for preferred in preferences {
            for candidate in localeLookupTags(preferred) {
                if let match = displays.first(where: { normalizeLocale(stringValue($0["locale"])) == candidate }) {
                    return match
                }
            }
        }
        return displays.first(where: { stringValue($0["locale"]) == nil }) ?? displays.first
    }

    private static func normalizeLocale(_ locale: String?) -> String? {
        guard let locale else { return nil }
        let normalized = locale
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "_", with: "-")
            .lowercased()
        return normalized.isEmpty ? nil : normalized
    }

    private static func localeLookupTags(_ locale: String) -> [String] {
        var subtags = locale.split(separator: "-").map(String.init).filter { !$0.isEmpty }
        var tags: [String] = []
        while !subtags.isEmpty {
            tags.append(subtags.joined(separator: "-"))
            subtags.removeLast()
            if let last = subtags.last, last.count == 1 {
                subtags.removeLast()
            }
        }
        return tags
    }

    private static func stringValue(_ value: Any?) -> String? {
        guard let string = value as? String else { return nil }
        let trimmed = string.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
