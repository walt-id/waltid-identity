import SwiftUI

@main
struct WalletDemoIosApp: App {
    @StateObject private var viewModel: WalletViewModel = {
        let defaults = UserDefaults.standard
        let baseUrl = defaults.string(forKey: "ATTESTATION_BASE_URL")
        if let baseUrl, !baseUrl.isEmpty {
            return WalletViewModel(
                attestationBaseUrl: baseUrl,
                attestationAttesterPath: defaults.string(forKey: "ATTESTATION_ATTESTER_PATH"),
                attestationBearerToken: defaults.string(forKey: "ATTESTATION_BEARER_TOKEN"),
                attestationHostHeader: defaults.string(forKey: "ATTESTATION_HOST_HEADER")
            )
        }
        return WalletViewModel()
    }()

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
