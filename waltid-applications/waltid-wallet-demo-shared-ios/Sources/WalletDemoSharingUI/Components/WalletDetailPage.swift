import SwiftUI

/// Page chrome inside the active information presentation; native navigation owns Back.
public struct WalletDetailPage<Content: View>: View {
    private let title: String
    private let content: Content
    @Environment(\.walletDetailDismissal) private var dismissal

    public init(_ title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    public var body: some View {
        if #available(iOS 16, *) {
            page.toolbarBackground(Color(.systemGroupedBackground), for: .navigationBar)
                .toolbarBackground(.visible, for: .navigationBar)
        } else { page }
    }

    private var page: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) { content }.padding(20)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if let dismissal {
                    Button(action: dismissal.perform) {
                        Image(systemName: "xmark").frame(minWidth: 44, minHeight: 44)
                    }
                        .buttonStyle(.plain)
                        .accessibilityLabel(String(localized: "Close", bundle: .module))
                        .accessibilityIdentifier(dismissal.identifier)
                }
            }
        }
    }
}
