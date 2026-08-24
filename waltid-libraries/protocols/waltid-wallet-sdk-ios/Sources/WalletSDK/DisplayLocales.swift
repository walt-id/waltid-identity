import Foundation

/// Shared locale matching for OpenID4VCI display metadata arrays.
///
/// Preferences are matched from most specific tag to language-only, then an untagged entry,
/// then the first entry. Keep this aligned with `id.walt.credentials.display.DisplayLocales`.
public enum DisplayLocales {
    public static func normalize(_ locale: String?) -> String? {
        guard let locale else { return nil }
        let normalized = locale
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "_", with: "-")
            .lowercased()
        return normalized.isEmpty ? nil : normalized
    }

    public static func lookupTags(_ locale: String) -> [String] {
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

    public static func select<T>(
        _ items: [T],
        preferredLocales: [String],
        localeOf: (T) -> String?
    ) -> T? {
        guard !items.isEmpty else { return nil }
        let preferences = preferredLocales.compactMap(normalize(_:))
        for preferred in preferences {
            for candidate in lookupTags(preferred) {
                if let match = items.first(where: { normalize(localeOf($0)) == candidate }) {
                    return match
                }
            }
        }
        return items.first(where: { normalize(localeOf($0)) == nil }) ?? items.first
    }
}
