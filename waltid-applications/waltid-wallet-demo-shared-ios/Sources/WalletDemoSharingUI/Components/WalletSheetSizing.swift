import SwiftUI

extension View {
    /// Supply a height measured before presentation. Unknown or long flows stay fully expanded.
    public func walletSheetSizing(preferredHeight: CGFloat? = nil, expanded: Bool = false) -> some View {
        modifier(WalletSheetSizing(preferredHeight: preferredHeight, expanded: expanded))
    }
}

private struct WalletSheetSizing: ViewModifier {
    let preferredHeight: CGFloat?
    let expanded: Bool

    @ViewBuilder func body(content: Content) -> some View {
        if #available(iOS 16, *) {
            ResizableWalletSheet(preferredHeight: preferredHeight, expanded: expanded, content: content)
        } else { content }
    }
}

@available(iOS 16, *)
private struct ResizableWalletSheet<Content: View>: View {
    let expanded: Bool
    let content: Content
    @State private var entryDetent: PresentationDetent
    @State private var selection: PresentationDetent

    init(preferredHeight: CGFloat?, expanded: Bool, content: Content) {
        self.expanded = expanded
        self.content = content
        _entryDetent = State(initialValue: Self.detent(height: preferredHeight, expanded: false))
        _selection = State(initialValue: Self.detent(height: preferredHeight, expanded: expanded))
    }

    var body: some View {
        content
            // Detent identities stay fixed throughout the native presentation animation.
            .presentationDetents([entryDetent, .large], selection: $selection)
            .presentationDragIndicator(.visible)
            .onChange(of: expanded) { selection = $0 ? .large : entryDetent }
    }

    private static func detent(height: CGFloat?, expanded: Bool) -> PresentationDetent {
        guard !expanded, let height, height.isFinite, height > 0 else { return .large }
        return .height(ceil(height))
    }
}
