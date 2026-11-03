import Foundation

/// An instant, kept independent of display settings and without losing JSON fractional precision.
public struct WalletInstant: Equatable {
    public let epochSeconds: Int64
    public let nanoseconds: Int

    init(epochSeconds: Int64, nanoseconds: Int) {
        self.epochSeconds = epochSeconds
        self.nanoseconds = nanoseconds
    }

    public init(_ date: Date) {
        // Date-backed UI uses millisecond precision to avoid exposing binary floating-point
        // artifacts. JSON claims retain their exact integer nanoseconds instead.
        let milliseconds = (date.timeIntervalSince1970 * 1_000).rounded()
        let seconds = floor(milliseconds / 1_000)
        self.init(epochSeconds: Int64(seconds), nanoseconds: Int(milliseconds - seconds * 1_000) * 1_000_000)
    }

    private var date: Date { Date(timeIntervalSince1970: TimeInterval(epochSeconds)) }

    public func formatted(timeZone: TimeZone = .current, locale: Locale = .current) -> String {
        let formatter = Self.gregorianFormatter()
        formatter.locale = locale
        formatter.timeZone = timeZone
        formatter.setLocalizedDateFormatFromTemplate("yMMMdjmmss")
        let number = NumberFormatter()
        number.locale = locale
        number.minimumIntegerDigits = 9
        number.usesGroupingSeparator = false
        var digits = number.string(from: NSNumber(value: nanoseconds)) ?? ""
        let localZero = number.string(from: 0)?.last ?? "0"
        while digits.last == localZero { digits.removeLast() }
        if !digits.isEmpty {
            formatter.dateFormat = Self.withFraction(formatter.dateFormat, fraction: (number.decimalSeparator ?? ".") + digits)
        }
        return formatter.string(from: date) + " " + Self.offsetLabel(timeZone.secondsFromGMT(for: date))
    }

    static func parseTimestamp(_ value: String) -> WalletInstant? {
        let raw = value.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard let match = timestamp.firstMatch(in: raw, range: NSRange(raw.startIndex..., in: raw)) else { return nil }
        func group(_ index: Int) -> String {
            guard let range = Range(match.range(at: index), in: raw) else { return "" }
            return String(raw[range])
        }
        let wall = group(1)
        let formatter = gregorianFormatter()
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        guard let localDate = formatter.date(from: wall), formatter.string(from: localDate) == wall else { return nil }
        let zone = group(3)
        var offset = 0
        if zone != "Z" {
            guard let hours = Int(zone.dropFirst().prefix(2)), hours <= 18,
                  let minutes = Int(zone.suffix(2)), minutes <= 59, hours < 18 || minutes == 0 else { return nil }
            offset = (hours * 3600 + minutes * 60) * (zone.first == "-" ? -1 : 1)
        }
        let seconds = Int64(localDate.timeIntervalSince1970) - Int64(offset)
        guard (-62_135_596_800...253_402_300_799).contains(seconds) else { return nil }
        let fraction = group(2)
        return WalletInstant(epochSeconds: seconds, nanoseconds: Int(fraction + String(repeating: "0", count: 9 - fraction.count))!)
    }

    private static let timestamp = try! NSRegularExpression(pattern: #"^([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-5][0-9])(?:\.([0-9]{1,9}))?(Z|[+-][0-9]{2}:[0-9]{2})$"#)

    private static func gregorianFormatter() -> DateFormatter {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.gregorianStartDate = .distantPast
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.isLenient = false
        return formatter
    }

    private static func withFraction(_ pattern: String, fraction: String) -> String {
        var quoted = false
        var index = pattern.startIndex
        while index < pattern.endIndex {
            if pattern[index] == "'" { quoted.toggle() }
            if !quoted && pattern[index] == "s" {
                while index < pattern.endIndex && pattern[index] == "s" { index = pattern.index(after: index) }
                return String(pattern[..<index]) + "'\(fraction)'" + pattern[index...]
            }
            index = pattern.index(after: index)
        }
        return pattern
    }

    private static func offsetLabel(_ total: Int) -> String {
        guard total != 0 else { return "UTC" }
        let value = abs(total)
        let minutes = String(format: "%02d:%02d", value / 3600, value / 60 % 60)
        let seconds = value % 60 == 0 ? "" : String(format: ":%02d", value % 60)
        return "UTC\(total < 0 ? "-" : "+")\(minutes)\(seconds)"
    }
}
