import SwiftUI

/// One native stack for a task, with the existing iOS 15 navigation fallback.
public struct WalletNavigationContainer<Content: View>: View {
    private let content: Content

    public init(@ViewBuilder content: () -> Content) { self.content = content() }

    public var body: some View {
        Group {
            if #available(iOS 16, *) { NavigationStack { content } }
            else { NavigationView { content }.navigationViewStyle(.stack) }
        }.environment(\.walletReviewNavigationAvailable, true)
    }
}

private struct WalletReviewNavigationAvailableKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    var walletReviewNavigationAvailable: Bool {
        get { self[WalletReviewNavigationAvailableKey.self] }
        set { self[WalletReviewNavigationAvailableKey.self] = newValue }
    }
}

private struct WalletReviewDestination<Destination: View>: ViewModifier {
    @Environment(\.walletReviewNavigationAvailable) private var hasNavigation
    let isPresented: Binding<Bool>
    let destination: () -> Destination

    @ViewBuilder func body(content: Content) -> some View {
        if hasNavigation { content.walletDetailDestination(isPresented: isPresented, destination: destination) }
        else { content.sheet(isPresented: isPresented) { destination().walletSheetSizing() } }
    }
}

extension View {
    /// Independent consumers retain a sheet adapter; active wallet reviews push in their task stack.
    func walletReviewDestination<Destination: View>(isPresented: Binding<Bool>, @ViewBuilder destination: @escaping () -> Destination) -> some View {
        modifier(WalletReviewDestination(isPresented: isPresented, destination: destination))
    }
}
