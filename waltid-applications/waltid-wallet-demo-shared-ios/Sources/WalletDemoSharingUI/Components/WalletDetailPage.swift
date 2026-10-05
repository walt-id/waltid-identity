import SwiftUI

/// Page chrome inside the active information presentation; native navigation owns Back.
public struct WalletDetailPage<Content: View>: View {
    private let title: String
    private let onDismiss: () -> Void
    private let closeIdentifier: String
    private let content: Content

    public init(_ title: String, onDismiss: @escaping () -> Void,
                closeIdentifier: String = "wallet-detail-close", @ViewBuilder content: () -> Content) {
        self.title = title
        self.onDismiss = onDismiss
        self.closeIdentifier = closeIdentifier
        self.content = content()
    }

    public var body: some View {
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
    }
}
