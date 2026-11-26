import Foundation

/// Format-aware display title for a stored credential when OpenID4VCI card art is not used.
public enum CredentialTitles {
    /// Resolves a human-readable credential title from stored display metadata or payload fields.
    ///
    /// Prefers an explicit display name, then a format-specific title extracted from the credential
    /// JSON, then `fallback`, and finally the raw format string.
    ///
    /// - Parameters:
    ///   - format: Credential format used to choose the payload title rules.
    ///   - credentialDataJSON: Serialized credential payload, when one is available.
    ///   - displayName: Issuer or wallet display name that should win over extracted titles.
    ///   - fallback: Label to use when neither display metadata nor the payload yields a title.
    public static func displayName(
        format: String,
        credentialDataJSON: String?,
        displayName: String? = nil,
        fallback: String? = nil
    ) -> String {
        if let displayName, !displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        let payload = credentialDataJSON.flatMap(parseJSON)
        let extracted: String?
        if isMdoc(format) {
            extracted = mdocTitle(payload)
        } else if isSdJwt(format) {
            extracted = sdJwtTitle(payload)
        } else {
            extracted = w3cTitle(payload)
        }
        return extracted
            ?? fallback?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
            ?? format
    }

    private static func w3cTitle(_ payload: [String: Any]?) -> String? {
        let types = stringValues(payload?["type"]) ?? stringValues((payload?["vc"] as? [String: Any])?["type"])
        return types?
            .first { !isGenericVerifiableCredentialType($0) }
            .flatMap(humanizeToken)
    }

    private static func sdJwtTitle(_ payload: [String: Any]?) -> String? {
        guard let vct = payload?["vct"] as? String else { return nil }
        return humanizeToken(vct)
    }

    private static func mdocTitle(_ payload: [String: Any]?) -> String? {
        let docType = (payload?["docType"] as? String)
            ?? (payload?["doctype"] as? String)
            ?? (payload?["doc_type"] as? String)
        guard let docType else { return nil }
        if let mapped = mdocFriendlyNames[docType] { return mapped }
        let namespace = docType.split(separator: ".").dropLast().joined(separator: ".")
        return mdocFriendlyNames[namespace] ?? humanizeToken(docType)
    }

    private static func humanizeToken(_ raw: String) -> String? {
        guard let token = trailingToken(raw), !isGenericVerifiableCredentialType(token) else { return nil }
        let spaced = token.replacingOccurrences(
            of: "([a-z])([A-Z])",
            with: "$1 $2",
            options: .regularExpression
        )
        let words = spaced
            .split { $0 == "_" || $0 == "-" || $0 == "." || $0 == " " }
            .filter { !$0.isEmpty }
            .map { word in
                word.lowercased().prefix(1).uppercased() + word.lowercased().dropFirst()
            }
        let joined = words.joined(separator: " ")
        return joined.isEmpty ? nil : joined
    }

    private static func trailingToken(_ raw: String) -> String? {
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return nil }
        let source: String
        if value.contains("://") {
            source = value.split(separator: "/").last.map(String.init)?.split(separator: "?").first.map(String.init) ?? value
        } else {
            source = value
        }
        let parts = source.split { $0 == "/" || $0 == "#" || $0 == ":" || $0 == "." }.map(String.init).filter { !$0.isEmpty }
        guard let last = parts.last else { return source }
        if last.allSatisfy(\.isNumber), parts.count >= 2 {
            return "\(parts[parts.count - 2])_\(last)"
        }
        return last
    }

    private static func isMdoc(_ format: String) -> Bool {
        let normalized = format.lowercased()
        return normalized == "mso_mdoc" || normalized == "mdoc" || normalized.contains("mdoc")
    }

    private static func isSdJwt(_ format: String) -> Bool {
        let normalized = format.lowercased()
        return normalized.contains("sd-jwt") || normalized.contains("sd_jwt")
    }

    private static func isGenericVerifiableCredentialType(_ value: String) -> Bool {
        trailingToken(value)?.caseInsensitiveCompare("VerifiableCredential") == .orderedSame
    }

    private static func parseJSON(_ raw: String) -> [String: Any]? {
        guard let data = raw.data(using: .utf8) else { return nil }
        return (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
    }

    private static func stringValues(_ value: Any?) -> [String]? {
        switch value {
        case let values as [String]:
            return values.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        case let value as String:
            let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : [trimmed]
        default:
            return nil
        }
    }

    private static let mdocFriendlyNames = [
        "org.iso.18013.5.1.mDL": "Mobile Driving Licence",
        "org.iso.18013.5.1": "Mobile Driving Licence",
        "eu.europa.ec.eudi.pid.1": "PID",
        "eu.europa.ec.eudi.pid": "PID",
        "org.iso.23220.photoid.1": "Photo ID",
        "org.iso.23220.1": "Photo ID",
        "eu.europa.ec.eudi.mdl.1": "Mobile Driving Licence",
    ]
}

private extension String {
    var nonEmpty: String? { isEmpty ? nil : self }
}
