import Foundation

enum CredentialTypeIdentifier {
    static func token(_ rawValue: String) -> String? {
        let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return nil }

        let token = httpURLToken(value) ?? trailingIdentifierToken(value)
        return token.isEmpty ? nil : token
    }

    private static func httpURLToken(_ value: String) -> String? {
        guard let components = URLComponents(string: value),
              let scheme = components.scheme?.lowercased(),
              httpSchemes.contains(scheme) else {
            return nil
        }

        return components.path
            .split(separator: "/")
            .last
            .map(String.init)
    }

    private static func trailingIdentifierToken(_ value: String) -> String {
        let parts = value
            .split(whereSeparator: identifierDelimiters.contains)
            .map(String.init)
        guard let last = parts.last else {
            return value
        }
        if last.allSatisfy(\.isNumber), parts.count >= 2 {
            return "\(parts[parts.count - 2])_\(last)"
        }
        return last
    }

    private static let httpSchemes: Set<String> = ["http", "https"]
    private static let identifierDelimiters: Set<Character> = ["/", "#", ":"]
}
