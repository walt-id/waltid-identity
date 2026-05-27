import SwiftUI

@main
struct WalletDemoIosApp: App {
    @StateObject private var viewModel = WalletViewModel()

    var body: some Scene {
        WindowGroup {
            NavigationStack {
                ContentView(viewModel: viewModel)
                    .navigationTitle("walt.id Wallet")
            }
            .tint(.waltBlue)
            .onOpenURL { url in
                viewModel.handleDeepLink(url)
            }
        }
    }
}
