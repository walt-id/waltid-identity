import SwiftUI

/// Resolve display settings in the view, so cached credentials never cache a local time string.
public struct WalletInstantText: View {
    private let instant: WalletInstant
    @Environment(\.locale) private var locale
    @Environment(\.timeZone) private var timeZone

    public init(_ instant: WalletInstant) { self.instant = instant }
    public init(_ date: Date) { self.instant = WalletInstant(date) }

    public var body: some View {
        Text(instant.formatted(timeZone: timeZone, locale: locale))
    }
}
