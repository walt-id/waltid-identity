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
                WalletDetailPage(title, onDismiss: onDismiss, closeIdentifier: closeIdentifier) { content }
            }
        } else {
            NavigationView {
                WalletDetailPage(title, onDismiss: onDismiss, closeIdentifier: closeIdentifier) { content }
            }
            .navigationViewStyle(.stack)
        }
    }
}
