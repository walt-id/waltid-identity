import SwiftUI

@main
struct WalletDemoIosApp: App {
    @StateObject private var viewModel = WalletViewModel()

    var body: some Scene {
        WindowGroup {
            NavigationStack {
                HomeView(viewModel: viewModel)
                    .navigationTitle("walt.id Wallet")
                    .toolbar {
                        ToolbarItem(placement: .navigationBarTrailing) {
                            NavigationLink(destination: SettingsView(viewModel: viewModel)) {
                                Image(systemName: "gear")
                            }
                        }
                    }
            }
            .tint(.waltBlue)
            .onOpenURL { url in
                handleDeepLink(url)
            }
        }
    }

    private func handleDeepLink(_ url: URL) {
        // Deep link handling — the URL is passed to relevant views
        let urlString = url.absoluteString
        switch url.scheme {
        case "openid-credential-offer":
            // Navigate to receive with this URL
            break
        case "openid4vp":
            // Navigate to present with this URL
            break
        default:
            break
        }
    }
}
