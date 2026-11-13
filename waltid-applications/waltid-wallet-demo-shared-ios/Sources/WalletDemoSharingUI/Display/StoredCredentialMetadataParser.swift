import Foundation
import WalletSDK

/// Parses sidecar credential metadata JSON written by the wallet on receive.
///
/// The wallet stores OpenID4VCI issuer display under `issuerDisplay` and credential configuration
/// display under `credentialDisplay` as arrays of locale-tagged objects.
public enum StoredCredentialMetadataParser {
    public static func issuerDisplay(
        from metadataJSON: String?,
        preferredLocales: [String] = []
    ) -> MetadataDisplay? {
        parseDisplay(from: metadataJSON, key: "issuerDisplay", preferredLocales: preferredLocales)
    }

    public static func credentialDisplay(
        from metadataJSON: String?,
        preferredLocales: [String] = []
    ) -> MetadataDisplay? {
        parseDisplay(from: metadataJSON, key: "credentialDisplay", preferredLocales: preferredLocales)
    }

    /// These are definitions advertised by the issuer, never values not yet received.
    public static func claims(from metadataJSON: String?, preferredLocales: [String] = []) -> [CredentialClaimMetadata] {
        guard case .object(let root) = CredentialDisplayJSONParser.parse(metadataJSON ?? ""),
              case .array(let entries)? = root.first(where: { $0.key == "credentialClaims" })?.value else { return [] }
        var seen = Set<[CredentialClaimPathSegment]>()
        var result: [CredentialClaimMetadata] = []
        for entry in entries {
            guard case .object(let fields) = entry,
                  case .array(let values)? = fields.first(where: { $0.key == "path" })?.value,
                  !values.isEmpty else { return [] }
            let path = values.compactMap(CredentialClaimPathSegment.init)
            guard path.count == values.count, seen.insert(path).inserted else { return [] }
            let displaysJSON = fields.first(where: { $0.key == "display" })?.value.rawJSON ?? "[]"
            let displays = (try? JSONSerialization.jsonObject(with: Data(displaysJSON.utf8)) as? [[String: Any]]) ?? []
            let display = DisplayLocales.select(displays, preferredLocales: preferredLocales) { stringValue($0["locale"]) }
            let mandatory: Bool?
            if case .bool(let value)? = fields.first(where: { $0.key == "mandatory" })?.value {
                mandatory = value
            } else {
                mandatory = nil
            }
            result.append(CredentialClaimMetadata(path: path, mandatory: mandatory, name: stringValue(display?["name"])))
        }
        return result
    }

    private static func parseDisplay(
        from metadataJSON: String?,
        key: String,
        preferredLocales: [String]
    ) -> MetadataDisplay? {
        let raw = metadataJSON?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !raw.isEmpty,
              let data = raw.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }

        let displays: [[String: Any]]
        switch root[key] {
        case let array as [[String: Any]]:
            displays = array
        case let object as [String: Any]:
            displays = [object]
        default:
            return nil
        }
        guard !displays.isEmpty else { return nil }

        guard let selected = DisplayLocales.select(displays, preferredLocales: preferredLocales, localeOf: {
            stringValue($0["locale"])
        }) else {
            return nil
        }

        let logo = selected["logo"] as? [String: Any]
        let backgroundImage = (selected["background_image"] as? [String: Any])
            ?? (selected["backgroundImage"] as? [String: Any])
        let name = stringValue(selected["name"])
        let locale = stringValue(selected["locale"])
        let logoURI = stringValue(logo?["uri"])
        let logoAltText = stringValue(logo?["alt_text"]) ?? stringValue(logo?["altText"])
        let description = stringValue(selected["description"])
        let backgroundColor = stringValue(selected["background_color"]) ?? stringValue(selected["backgroundColor"])
        let backgroundImageURI = stringValue(backgroundImage?["uri"])
        let textColor = stringValue(selected["text_color"]) ?? stringValue(selected["textColor"])

        guard name != nil
            || description != nil
            || logoURI != nil
            || backgroundColor != nil
            || backgroundImageURI != nil
            || textColor != nil else {
            return nil
        }
        return MetadataDisplay(
            name: name,
            locale: locale,
            logoURI: logoURI,
            logoAltText: logoAltText,
            description: description,
            backgroundColor: backgroundColor,
            backgroundImageURI: backgroundImageURI,
            textColor: textColor
        )
    }

    private static func stringValue(_ value: Any?) -> String? {
        guard let string = value as? String else { return nil }
        let trimmed = string.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
