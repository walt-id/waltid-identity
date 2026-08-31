import Foundation

/// Shared locale matching for OpenID4VCI display metadata arrays.
///
/// Preferences are matched from most specific tag to language-only, then an untagged entry,
/// then the first entry. Keep this aligned with `id.walt.credentials.display.DisplayLocales`.
public enum DisplayLocales {
    /// Normalizes a BCP 47 locale tag for case-insensitive display matching.
    ///
    /// - Parameter locale: Locale tag that may use `_` separators or mixed case.
    /// - Returns: A lowercase hyphenated tag, or `nil` when the input is blank.
    public static func normalize(_ locale: String?) -> String? {
        guard let locale else { return nil }
        let normalized = locale
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "_", with: "-")
            .lowercased()
        return normalized.isEmpty ? nil : normalized
    }

    /// Builds the most-specific-to-language-only lookup tags for a locale.
    ///
    /// - Parameter locale: Already-normalized BCP 47 locale tag.
    /// - Returns: Tags from the full locale down to the language subtag.
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

    /// Selects the display entry that best matches the preferred locales.
    ///
    /// Matching walks each preference from most specific tag to language-only,
    /// then an untagged entry, then the first item.
    ///
    /// - Parameters:
    ///   - items: Display metadata entries to choose from.
    ///   - preferredLocales: Locale tags in preference order.
    ///   - localeOf: Reads the locale tag from one display entry.
    /// - Returns: The best matching entry, or `nil` when `items` is empty.
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
