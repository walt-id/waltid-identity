import SwiftUI

/// A grouped settings-style row for a detail destination.
public struct WalletNavigationRow: View {
    private let title: String
    private let subtitle: String?
    private let symbol: String
    private let action: () -> Void

    public init(_ title: String, subtitle: String? = nil, symbol: String = "info.circle", action: @escaping () -> Void) {
        self.title = title
        self.subtitle = subtitle
        self.symbol = symbol
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: symbol).frame(width: 24).accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.body).foregroundStyle(.primary)
                    if let subtitle { Text(subtitle).font(.footnote).foregroundStyle(.secondary) }
                }.frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.forward").font(.footnote).foregroundStyle(.secondary).accessibilityHidden(true)
            }
            .padding(16)
            .frame(minHeight: 56)
            .contentShape(Rectangle())
        }.buttonStyle(.plain)
    }
}
