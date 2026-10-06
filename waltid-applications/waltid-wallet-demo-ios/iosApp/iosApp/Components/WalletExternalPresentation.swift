import SwiftUI

private struct WalletOpenInAppKey: EnvironmentKey {
    static let defaultValue: (() -> Void)? = nil
}

extension EnvironmentValues {
    var walletOpenInApp: (() -> Void)? {
        get { self[WalletOpenInAppKey.self] }
        set { self[WalletOpenInAppKey.self] = newValue }
    }
}

struct WalletOpenInAppButton: View {
    @Environment(\.walletOpenInApp) private var open
    var body: some View {
        if let open {
            Button("Open in app", action: open).accessibilityIdentifier("wallet.external.openInApp")
        }
    }
}
