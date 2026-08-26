import Foundation

/// CSS Color Level 3 channels used by demo card art. Kept local to this package so WalletCore
/// does not grow a public color type solely for the demos.
public struct CssColorChannels: Equatable {
    public let red: Double
    public let green: Double
    public let blue: Double
    public let alpha: Double

    public init(red: Double, green: Double, blue: Double, alpha: Double) {
        self.red = red
        self.green = green
        self.blue = blue
        self.alpha = alpha
    }
}

/// Parses OID4VCI `background_color` / `text_color` as CSS Color Level 3.
///
/// Accepts `#rgb`, `#rrggbb`, `rgb()`, `rgba()`, `hsl()`, `hsla()`, and `transparent`.
/// Bare hex, prefixed function names, trailing text after `)`, and 8-digit hex are rejected.
public enum CssColorParser {
    public static func parse(_ value: String?) -> CssColorChannels? {
        let raw = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !raw.isEmpty else { return nil }
        if raw.caseInsensitiveCompare("transparent") == .orderedSame {
            return CssColorChannels(red: 0, green: 0, blue: 0, alpha: 0)
        }
        if raw.hasPrefix("#") {
            return parseCssHex(raw)
        }
        if functionName(raw, equals: "rgba") { return parseCssRgbFunction(raw, alpha: true) }
        if functionName(raw, equals: "rgb") { return parseCssRgbFunction(raw, alpha: false) }
        if functionName(raw, equals: "hsla") { return parseCssHslFunction(raw, alpha: true) }
        if functionName(raw, equals: "hsl") { return parseCssHslFunction(raw, alpha: false) }
        return nil
    }
}

private func functionName(_ value: String, equals name: String) -> Bool {
    guard let open = value.firstIndex(of: "("), open > value.startIndex else { return false }
    return value[value.startIndex..<open]
        .trimmingCharacters(in: .whitespacesAndNewlines)
        .caseInsensitiveCompare(name) == .orderedSame
}

private func parseCssHex(_ value: String) -> CssColorChannels? {
    guard value.hasPrefix("#") else { return nil }
    let hex = String(value.dropFirst())
    let normalized: String
    switch hex.count {
    case 3:
        normalized = hex.map { "\($0)\($0)" }.joined()
    case 6:
        normalized = hex
    default:
        return nil
    }
    guard let rgb = UInt32(normalized, radix: 16) else { return nil }
    return CssColorChannels(
        red: Double((rgb >> 16) & 0xFF) / 255,
        green: Double((rgb >> 8) & 0xFF) / 255,
        blue: Double(rgb & 0xFF) / 255,
        alpha: 1
    )
}

private func parseCssRgbFunction(_ value: String, alpha: Bool) -> CssColorChannels? {
    guard let parts = cssFunctionArguments(value), parts.count == (alpha ? 4 : 3) else { return nil }
    guard let red = cssRgbChannel(parts[0]),
          let green = cssRgbChannel(parts[1]),
          let blue = cssRgbChannel(parts[2]) else { return nil }
    let parsedAlpha = alpha ? cssAlpha(parts[3]) : 1
    guard let parsedAlpha else { return nil }
    return CssColorChannels(red: red, green: green, blue: blue, alpha: parsedAlpha)
}

private func parseCssHslFunction(_ value: String, alpha: Bool) -> CssColorChannels? {
    guard let parts = cssFunctionArguments(value), parts.count == (alpha ? 4 : 3) else { return nil }
    guard let hue = Double(parts[0].replacingOccurrences(of: "deg", with: "")),
          let saturation = cssPercent(parts[1]),
          let lightness = cssPercent(parts[2]) else { return nil }
    let parsedAlpha = alpha ? cssAlpha(parts[3]) : 1
    guard let parsedAlpha else { return nil }
    let rgb = cssHslToRgb(hue: hue, saturation: saturation, lightness: lightness)
    return CssColorChannels(red: rgb.0, green: rgb.1, blue: rgb.2, alpha: parsedAlpha)
}

private func cssFunctionArguments(_ value: String) -> [String]? {
    guard let open = value.firstIndex(of: "(") else { return nil }
    let afterOpen = value.index(after: open)
    guard let close = value[afterOpen...].firstIndex(of: ")") else { return nil }
    let trailing = value[value.index(after: close)...]
        .trimmingCharacters(in: .whitespacesAndNewlines)
    guard trailing.isEmpty else { return nil }
    return value[afterOpen..<close]
        .split(separator: ",")
        .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
}

private func cssRgbChannel(_ value: String) -> Double? {
    if value.hasSuffix("%") {
        guard let percent = Double(value.dropLast()) else { return nil }
        return min(max(percent / 100, 0), 1)
    }
    guard let channel = Double(value) else { return nil }
    return min(max(channel / 255, 0), 1)
}

private func cssPercent(_ value: String) -> Double? {
    guard value.hasSuffix("%"), let percent = Double(value.dropLast()) else { return nil }
    return min(max(percent / 100, 0), 1)
}

private func cssAlpha(_ value: String) -> Double? {
    if value.hasSuffix("%") {
        guard let percent = Double(value.dropLast()) else { return nil }
        return min(max(percent / 100, 0), 1)
    }
    guard let alpha = Double(value) else { return nil }
    return min(max(alpha, 0), 1)
}

private func cssHslToRgb(hue: Double, saturation: Double, lightness: Double) -> (Double, Double, Double) {
    let wrappedHue = ((hue.truncatingRemainder(dividingBy: 360)) + 360)
        .truncatingRemainder(dividingBy: 360)
    let chroma = (1 - abs(2 * lightness - 1)) * saturation
    let x = chroma * (1 - abs((wrappedHue / 60).truncatingRemainder(dividingBy: 2) - 1))
    let match = lightness - chroma / 2
    let primary: (Double, Double, Double)
    switch wrappedHue {
    case ..<60: primary = (chroma, x, 0)
    case ..<120: primary = (x, chroma, 0)
    case ..<180: primary = (0, chroma, x)
    case ..<240: primary = (0, x, chroma)
    case ..<300: primary = (x, 0, chroma)
    default: primary = (chroma, 0, x)
    }
    return (primary.0 + match, primary.1 + match, primary.2 + match)
}
