import SwiftUI

/// Reusable action content for full screens, sheets and platform provider containers.
public struct WalletActions: View {
    private let primary: WalletAction?
    private let secondary: WalletAction?
    private let tertiary: WalletAction?

    public init(primary: WalletAction? = nil, secondary: WalletAction? = nil, tertiary: WalletAction? = nil) {
        self.primary = primary
        self.secondary = secondary
        self.tertiary = tertiary
    }

    public var body: some View {
        Group {
            if #available(iOS 16, *) {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 12) { actions }.fixedSize(horizontal: true, vertical: false)
                    stacked
                }
            } else {
                LegacyWalletActionLayout { actions }
            }
        }.frame(maxWidth: .infinity, alignment: .trailing)
    }

    private var stacked: some View { VStack(alignment: .trailing, spacing: 8) { actions } }

    @ViewBuilder private var actions: some View {
        if let tertiary { action(tertiary, prominence: .tertiary) }
        if let secondary { action(secondary, prominence: .secondary) }
        if let primary { action(primary, prominence: .primary) }
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

/// iOS 15 keeps the same fit-based behavior without changing the app's deployment target.
private struct LegacyWalletActionLayout<Content: View>: View {
    @ViewBuilder let content: () -> Content
    @State private var availableWidth: CGFloat = 0
    @State private var intrinsicWidth: CGFloat = .infinity

    var body: some View {
        Group {
            if intrinsicWidth <= availableWidth {
                HStack(spacing: 12, content: content)
            } else {
                VStack(alignment: .trailing, spacing: 8, content: content)
            }
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
        .background(GeometryReader { geometry in
            Color.clear.preference(key: ActionAvailableWidth.self, value: geometry.size.width)
        })
        .overlay(alignment: .trailing) {
            HStack(spacing: 12, content: content).fixedSize(horizontal: true, vertical: false)
                .background(GeometryReader { geometry in
                    Color.clear.preference(key: ActionIntrinsicWidth.self, value: geometry.size.width)
                })
                .hidden().accessibilityHidden(true).allowsHitTesting(false)
        }
        .onPreferenceChange(ActionAvailableWidth.self) { availableWidth = $0 }
        .onPreferenceChange(ActionIntrinsicWidth.self) { intrinsicWidth = $0 }
    }
}

private struct ActionAvailableWidth: PreferenceKey {
    static let defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

private struct ActionIntrinsicWidth: PreferenceKey {
    static let defaultValue: CGFloat = .infinity
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
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
            .opacity(!isEnabled ? 0.4 : configuration.isPressed ? 0.72 : 1)
    }
}
