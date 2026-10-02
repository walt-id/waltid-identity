import SwiftUI

/// Reusable action content for full screens, sheets and platform provider containers.
public struct WalletActions: View {
    private let primary: WalletAction
    private let secondary: WalletAction?
    private let tertiary: WalletAction?
    @Environment(\.sizeCategory) private var sizeCategory

    public init(primary: WalletAction, secondary: WalletAction? = nil, tertiary: WalletAction? = nil) {
        self.primary = primary
        self.secondary = secondary
        self.tertiary = tertiary
    }

    public var body: some View {
        Group {
            if sizeCategory.isAccessibilityCategory {
                stacked
            } else if #available(iOS 16, *) {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 12) { actions }.fixedSize(horizontal: true, vertical: false)
                    stacked
                }
            } else {
                stacked
            }
        }.frame(maxWidth: .infinity, alignment: .trailing)
    }

    private var stacked: some View { VStack(alignment: .trailing, spacing: 8) { actions } }

    @ViewBuilder private var actions: some View {
        if let tertiary { action(tertiary, prominence: .tertiary) }
        if let secondary { action(secondary, prominence: .secondary) }
        action(primary, prominence: .primary)
    }

    private func action(_ action: WalletAction, prominence: WalletActionProminence) -> some View {
        Button(action: action.perform) {
            Text(action.label).font(.body.weight(.semibold))
                .fixedSize(horizontal: false, vertical: true)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 20).padding(.vertical, 10)
                .frame(minHeight: 44)
        }
        .buttonStyle(WalletActionStyle(prominence: prominence))
        .disabled(!action.enabled)
        .accessibilityIdentifier(action.identifier ?? "")
    }
}

private enum WalletActionProminence { case primary, secondary, tertiary }

private struct WalletActionStyle: ButtonStyle {
    let prominence: WalletActionProminence
    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.walletDemoBranding) private var branding

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(prominence == .primary ? branding.onPrimary : branding.primary)
            .background(prominence == .primary ? branding.primary : Color.clear, in: Capsule())
            .overlay(Capsule().strokeBorder(prominence == .secondary ? branding.primary.opacity(0.4) : Color.clear, lineWidth: 1))
            .opacity(!isEnabled ? 0.4 : configuration.isPressed ? 0.72 : 1)
    }
}
