import SwiftUI

/// Bounded review content for app and provider hosts: information scrolls while actions stay visible.
/// The parent owns the sheet/full-screen presentation and the request's state.
public struct WalletReviewScaffold<Content: View, Actions: View>: View {
    private let showsActions: Bool
    private let content: Content
    private let actions: Actions

    public init(
        showsActions: Bool = true,
        @ViewBuilder content: () -> Content,
        @ViewBuilder actions: () -> Actions
    ) {
        self.showsActions = showsActions
        self.content = content()
        self.actions = actions()
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) { content }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20).padding(.top, 20).padding(.bottom, 12)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if showsActions {
                WalletFooter { actions }
            }
        }
        .background(Color(.systemGroupedBackground))
    }
}
