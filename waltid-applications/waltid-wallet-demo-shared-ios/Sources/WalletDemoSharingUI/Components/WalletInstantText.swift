import SwiftUI

/// Resolve display settings in the view, so cached credentials never cache a local time string.
public struct WalletInstantText: View {
    private let instant: WalletInstant
    @Environment(\.locale) private var locale
    @Environment(\.timeZone) private var timeZone

    public init(_ instant: WalletInstant) { self.instant = instant }
    public init(_ date: Date) { self.instant = WalletInstant(date) }

    public var body: some View {
        let text = instant.formatted(timeZone: timeZone, locale: locale)
        VStack(alignment: .leading, spacing: 2) {
            Text(text.date).font(.body)
            Text(text.supportingText).font(.subheadline).foregroundStyle(.primary)
        }
        .fixedSize(horizontal: false, vertical: true)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text.fullText)
    }
}
