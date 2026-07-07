import SwiftUI

@main
struct WalletDemoIosApp: App {
    @StateObject private var viewModel: WalletViewModel = {
        let env = ProcessInfo.processInfo.environment
        let defaults = UserDefaults.standard
        let baseUrl = env["ATTESTATION_BASE_URL"] ?? defaults.string(forKey: "ATTESTATION_BASE_URL")
        if let baseUrl, !baseUrl.isEmpty {
            return WalletViewModel(
                attestationBaseUrl: baseUrl,
                attestationAttesterPath: env["ATTESTATION_ATTESTER_PATH"] ?? defaults.string(forKey: "ATTESTATION_ATTESTER_PATH"),
                attestationBearerToken: env["ATTESTATION_BEARER_TOKEN"] ?? defaults.string(forKey: "ATTESTATION_BEARER_TOKEN"),
                attestationHostHeader: env["ATTESTATION_HOST_HEADER"] ?? defaults.string(forKey: "ATTESTATION_HOST_HEADER")
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
