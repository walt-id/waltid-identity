import SwiftUI

/// Resolve display settings in the view, so cached credentials never cache a local time string.
public struct WalletInstantText: View {
    private let instant: WalletInstant
    private let font: Font
    @Environment(\.locale) private var locale
    @Environment(\.timeZone) private var timeZone

    public init(_ instant: WalletInstant, font: Font = .body) { self.instant = instant; self.font = font }
    public init(_ date: Date, font: Font = .body) { self.instant = WalletInstant(date); self.font = font }

    public var body: some View {
        let text = instant.formatted(timeZone: timeZone, locale: locale)
        Text(text.fullText)
            .font(font)
            .fixedSize(horizontal: false, vertical: true)
    }
}
