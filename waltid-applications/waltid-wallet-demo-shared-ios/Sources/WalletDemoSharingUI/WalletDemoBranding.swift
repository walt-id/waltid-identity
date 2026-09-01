import SwiftUI

/// Demo-only white-label tokens. Edit ``WalletDemoBranding/default`` to rebrand the native iOS
/// wallet demo, or inject a custom value with `.environment(\.walletDemoBranding, ...)`.
///
/// Launcher names stay in `CFBundleDisplayName` in each app and extension `Info.plist`.
public struct WalletDemoBranding: Equatable {
    public var appTitle: String
    public var primary: Color
    public var onPrimary: Color
    public var secondary: Color
    public var onSecondary: Color
    public var primaryContainer: Color
    public var onPrimaryContainer: Color

    public init(
        appTitle: String = "walt.id Wallet",
        primary: Color = Color(red: 5 / 255, green: 115 / 255, blue: 240 / 255),
        onPrimary: Color = .white,
        secondary: Color = Color(red: 173 / 255, green: 198 / 255, blue: 255 / 255),
        onSecondary: Color = Color(red: 0, green: 46 / 255, blue: 105 / 255),
        primaryContainer: Color = Color(red: 216 / 255, green: 226 / 255, blue: 255 / 255),
        onPrimaryContainer: Color = Color(red: 0, green: 46 / 255, blue: 105 / 255)
    ) {
        self.appTitle = appTitle
        self.primary = primary
        self.onPrimary = onPrimary
        self.secondary = secondary
        self.onSecondary = onSecondary
        self.primaryContainer = primaryContainer
        self.onPrimaryContainer = onPrimaryContainer
    }

    public static let `default` = WalletDemoBranding()
}

private struct WalletDemoBrandingKey: EnvironmentKey {
    static let defaultValue = WalletDemoBranding.default
}

public extension EnvironmentValues {
    var walletDemoBranding: WalletDemoBranding {
        get { self[WalletDemoBrandingKey.self] }
        set { self[WalletDemoBrandingKey.self] = newValue }
    }
}
