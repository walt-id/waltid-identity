import SwiftUI

/// Activity decorates a stable toolbar symbol instead of replacing its native button label.
public struct WalletToolbarIcon: View {
    private let symbol: String
    private let isBusy: Bool

    public init(_ symbol: String, isBusy: Bool = false) {
        self.symbol = symbol
        self.isBusy = isBusy
    }

    public var body: some View {
        Image(systemName: symbol)
            .frame(width: 44, height: 44)
            .overlay(alignment: .bottomTrailing) {
                if isBusy {
                    ProgressView().controlSize(.mini)
                        .frame(width: 14, height: 14)
                        .offset(x: -2, y: -2)
                }
            }
            .accessibilityHidden(true)
            .transaction { $0.animation = nil }
    }
}
