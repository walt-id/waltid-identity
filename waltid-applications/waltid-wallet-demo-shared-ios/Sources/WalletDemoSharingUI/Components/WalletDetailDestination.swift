import SwiftUI

extension View {
    /// One compatibility seam keeps information pages in the same native navigation container.
    @ViewBuilder
    func walletDetailDestination<Destination: View>(
        isPresented: Binding<Bool>, @ViewBuilder destination: @escaping () -> Destination
    ) -> some View {
        if #available(iOS 16, *) {
            navigationDestination(isPresented: isPresented, destination: destination)
        } else {
            background {
                NavigationLink(isActive: isPresented, destination: destination) { EmptyView() }
                    .hidden().accessibilityHidden(true)
            }
        }
    }
}
