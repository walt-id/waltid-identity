import SwiftUI

/// Shared detail chrome for stored credentials and active reviews; dismissal never grants consent.
public struct WalletDetailSheet<Content: View>: View {
    private let title: String
    private let onDismiss: () -> Void
    private let closeIdentifier: String
    private let content: Content

    public init(_ title: String, onDismiss: @escaping () -> Void, closeIdentifier: String = "wallet-detail-close", @ViewBuilder content: () -> Content) {
        self.title = title
        self.onDismiss = onDismiss
        self.closeIdentifier = closeIdentifier
        self.content = content()
    }

    public var body: some View {
        if #available(iOS 16, *) {
            NavigationStack {
                WalletDetailPage(title) { content }
            }.environment(\.walletDetailDismissal, dismissal)
        } else {
            NavigationView {
                WalletDetailPage(title) { content }
            }
            .navigationViewStyle(.stack)
            .environment(\.walletDetailDismissal, dismissal)
        }
    }

    private var dismissal: WalletDetailDismissal {
        WalletDetailDismissal(perform: onDismiss, identifier: closeIdentifier)
    }
}

/// The presentation supplies one dismissal action to every page in its navigation stack.
struct WalletDetailDismissal {
    let perform: () -> Void
    let identifier: String
}

private struct WalletDetailDismissalKey: EnvironmentKey {
    static let defaultValue: WalletDetailDismissal? = nil
}

extension EnvironmentValues {
    var walletDetailDismissal: WalletDetailDismissal? {
        get { self[WalletDetailDismissalKey.self] }
        set { self[WalletDetailDismissalKey.self] = newValue }
    }
}
