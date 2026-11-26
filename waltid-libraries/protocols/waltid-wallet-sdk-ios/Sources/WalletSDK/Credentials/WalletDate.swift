import Foundation

func parseWalletISO8601Date(_ value: String) -> Date? {
    let formatter = ISO8601DateFormatter()
    if let date = formatter.date(from: value) {
        return date
    }

    formatter.formatOptions.insert(.withFractionalSeconds)
    return formatter.date(from: value)
}
