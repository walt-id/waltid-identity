import SwiftUI

/// Bounded review content for app and provider hosts: information scrolls while actions stay visible.
/// The parent owns the sheet/full-screen presentation and the request's state.
public struct WalletReviewScaffold<Content: View, Actions: View>: View {
    private let showsActions: Bool
    private let background: Color
    private let content: Content
    private let actions: Actions

    public init(
        showsActions: Bool = true,
        background: Color = Color(.systemGroupedBackground),
        @ViewBuilder content: () -> Content,
        @ViewBuilder actions: () -> Actions
    ) {
        self.showsActions = showsActions
        self.background = background
        self.content = content()
        self.actions = actions()
    }

    public var body: some View {
        if #available(iOS 27, *) {
            // The footer supplies its own material. UIKit's additional bottom-edge effect
            // can duplicate animation callbacks when the keyboard resizes a fitted sheet.
            // Keep it hidden while scanner actions appear, before requesting that keyboard.
            scaffold.scrollEdgeEffectHidden(true, for: .bottom)
        } else { scaffold }
    }

    private var scaffold: some View {
        WalletSheetScrollView {
            VStack(alignment: .leading, spacing: 14) { content }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20).padding(.top, 20).padding(.bottom, 12)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if showsActions {
                WalletFooter { actions }
            }
        }
        .background(background)
    }
}
