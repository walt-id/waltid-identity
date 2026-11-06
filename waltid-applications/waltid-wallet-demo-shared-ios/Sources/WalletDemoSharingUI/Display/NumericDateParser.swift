import Foundation

/// RFC 7519 seconds, including decimals/exponents; never guess milliseconds from magnitude.
enum NumericDateParser {
    private static let number = try! NSRegularExpression(pattern: #"^(-?)([0-9]+)(?:\.([0-9]+))?(?:[eE]([+-]?[0-9]+))?$"#)

    static func parse(_ value: String) -> WalletInstant? {
        let raw = value.trimmingCharacters(in: .whitespacesAndNewlines)
        // Bound work on untrusted input. Unrepresentable values remain visible in their raw form.
        guard raw.utf8.count <= 64,
              let match = number.firstMatch(in: raw, range: NSRange(raw.startIndex..., in: raw)) else { return nil }
        func group(_ index: Int) -> String {
            guard let range = Range(match.range(at: index), in: raw) else { return "" }
            return String(raw[range])
        }
        let exponentText = group(4)
        guard let exponent = exponentText.isEmpty ? 0 : Int(exponentText), (-64...64).contains(exponent) else { return nil }
        let integer = group(2)
        let digits = integer + group(3)
        let point = integer.count + exponent
        let wholeText = point <= 0 ? "0" : String(digits.prefix(point)) + String(repeating: "0", count: max(0, point - digits.count))
        guard let whole = Int64(wholeText) else { return nil }
        var decimal = point <= 0 ? String(repeating: "0", count: -point) + digits : String(digits.dropFirst(point))
        while decimal.last == "0" { decimal.removeLast() }
        guard decimal.count <= 9,
              let nanos = Int(decimal + String(repeating: "0", count: 9 - decimal.count)) else { return nil }
        // Four-digit Gregorian years are readable on every renderer; do not clamp or wrap.
        let negative = group(1) == "-"
        let seconds = negative ? -whole - (nanos > 0 ? 1 : 0) : whole
        guard (-62_135_596_800...253_402_300_799).contains(seconds) else { return nil }
        let positiveNanos = negative && nanos > 0 ? 1_000_000_000 - nanos : nanos
        return WalletInstant(epochSeconds: seconds, nanoseconds: positiveNanos)
    }
}
