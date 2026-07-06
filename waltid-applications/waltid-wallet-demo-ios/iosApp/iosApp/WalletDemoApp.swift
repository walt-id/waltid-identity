import SwiftUI

@main
struct WalletDemoApp: App {
    @StateObject private var viewModel: WalletViewModel = {
        let env = ProcessInfo.processInfo.environment
        let defaults = UserDefaults.standard
        let walletID = env["E2E_WALLET_ID"] ?? defaults.string(forKey: "E2E_WALLET_ID") ?? "default"
        let baseUrl = env["ATTESTATION_BASE_URL"] ?? defaults.string(forKey: "ATTESTATION_BASE_URL")
        if let baseUrl, !baseUrl.isEmpty {
            return WalletViewModel(
                walletID: walletID,
                attestationBaseUrl: baseUrl,
                attestationAttesterPath: env["ATTESTATION_ATTESTER_PATH"] ?? defaults.string(forKey: "ATTESTATION_ATTESTER_PATH"),
                attestationBearerToken: env["ATTESTATION_BEARER_TOKEN"] ?? defaults.string(forKey: "ATTESTATION_BEARER_TOKEN"),
                attestationHostHeader: env["ATTESTATION_HOST_HEADER"] ?? defaults.string(forKey: "ATTESTATION_HOST_HEADER")
            )
        }
        return WalletViewModel(walletID: walletID)
    }()

    var body: some Scene {
        WindowGroup {
            NavigationView {
                ContentView(viewModel: viewModel)
                    .navigationTitle("walt.id Wallet")
            }
            .navigationViewStyle(.stack)
            .tint(.waltBlue)
            .onOpenURL { url in
                viewModel.handleDeepLink(url)
            }
        }
    }
}
