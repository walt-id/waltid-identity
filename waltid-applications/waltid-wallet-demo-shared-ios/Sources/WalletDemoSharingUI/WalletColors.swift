import SwiftUI

public extension Color {
    static let waltBlue = Color(red: 5/255, green: 115/255, blue: 240/255)
    static let waltBlueLight = Color(red: 173/255, green: 198/255, blue: 255/255)
    static let waltBlueContainer = Color(red: 216/255, green: 226/255, blue: 255/255)
    static let waltBlueDark = Color(red: 0, green: 46/255, blue: 105/255)
    static let walletPortalPrimary = Color(red: 15/255, green: 23/255, blue: 42/255)
    static let walletPortalDisabled = Color(red: 148/255, green: 163/255, blue: 184/255)
    static let walletPortalBorder = Color(red: 203/255, green: 213/255, blue: 225/255)
}

public struct WalletPrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.colorScheme) private var colorScheme

    private let compact: Bool

    public init(compact: Bool = false) {
        self.compact = compact
    }

    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(foregroundColor)
            .frame(minHeight: compact ? 40 : 48)
            .padding(.horizontal, compact ? 12 : 14)
            .background(backgroundColor(configuration.isPressed))
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .padding(.vertical, compact ? 2 : 0)
    }

    private var foregroundColor: Color {
        colorScheme == .dark ? .waltBlueDark : .white
    }

    private func backgroundColor(_ pressed: Bool) -> Color {
        guard isEnabled else { return colorScheme == .dark ? Color(.systemGray3) : .walletPortalDisabled }
        if colorScheme == .dark { return pressed ? .waltBlueLight : Color(red: 140/255, green: 184/255, blue: 255/255) }
        return pressed ? .waltBlueDark : .waltBlue
    }
}

public struct WalletSecondaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.colorScheme) private var colorScheme

    public init() {}

    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.medium))
            .foregroundStyle(isEnabled ? foregroundColor : .walletPortalDisabled)
            .frame(minHeight: 48)
            .padding(.horizontal, 14)
            .background(backgroundColor(configuration.isPressed))
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(colorScheme == .dark ? Color(.systemGray3) : .walletPortalBorder, lineWidth: 1)
            )
    }

    private var foregroundColor: Color {
        colorScheme == .dark ? .waltBlueLight : .waltBlue
    }

    private func backgroundColor(_ pressed: Bool) -> Color {
        guard pressed else { return colorScheme == .dark ? Color(.systemBackground) : .white }
        return colorScheme == .dark ? Color(.secondarySystemBackground) : Color(red: 241/255, green: 245/255, blue: 249/255)
    }
}
