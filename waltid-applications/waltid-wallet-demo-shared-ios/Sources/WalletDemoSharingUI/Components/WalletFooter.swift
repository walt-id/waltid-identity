import SwiftUI

/// The screen owns one footer and supplies the controls and any adjacent feedback.
public struct WalletFooter<Content: View>: View {
    private let content: Content
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    public init(@ViewBuilder content: () -> Content) { self.content = content() }

    public var body: some View {
        VStack(spacing: 8) { content }
            .frame(maxWidth: .infinity, alignment: .trailing)
            .padding(.horizontal, 16).padding(.vertical, 10)
            .background {
                if reduceTransparency { Color(.systemGroupedBackground) }
                else { Rectangle().fill(.regularMaterial) }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("wallet.footer")
    }
}
