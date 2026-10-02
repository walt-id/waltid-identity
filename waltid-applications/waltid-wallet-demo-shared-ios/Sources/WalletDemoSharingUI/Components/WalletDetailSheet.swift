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
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) { content }.padding(20)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(String(localized: "Close", bundle: .module), action: onDismiss)
                        .accessibilityIdentifier(closeIdentifier)
                }
            }
        }.navigationViewStyle(.stack)
    }
}
