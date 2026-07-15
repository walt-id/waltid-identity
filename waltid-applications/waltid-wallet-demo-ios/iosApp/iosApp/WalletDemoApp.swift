import SwiftUI

@main
struct WalletDemoApp: App {
    @StateObject private var viewModel: WalletViewModel = {
        let env = ProcessInfo.processInfo.environment
        let defaults = UserDefaults.standard
        #if DEBUG
        if env["E2E_USE_MOCK_WALLET"] == "1" {
            return WalletViewModel.mockForUITests()
        }
        #endif
        let walletID = env["E2E_WALLET_ID"] ?? defaults.string(forKey: "E2E_WALLET_ID") ?? "default"
        if env["E2E_MOCK_WALLET"] == "1" {
            let delayMilliseconds = UInt64(env["E2E_MOCK_WALLET_DELAY_MS"] ?? "") ?? 0
            return WalletViewModel(
                walletID: walletID,
                walletClient: MockWalletClient(
                    operationDelayMilliseconds: delayMilliseconds,
                    verifierStyle: Self.mockVerifierStyle(environment: env),
                    duplicatePresentationOptions: env["E2E_MOCK_DUPLICATE_PRESENTATION_OPTIONS"] == "1"
                )
            )
        }
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
            ContentView(viewModel: viewModel)
            .tint(.waltBlue)
            .onOpenURL { url in
                viewModel.handleDeepLink(url)
            }
        }
    }

    private static func mockVerifierStyle(environment: [String: String]) -> MockWalletClient.VerifierStyle {
        if environment["E2E_MOCK_DNS_VERIFIER"] == "1" {
            return .x509SanDns
        }
        if environment["E2E_MOCK_DID_VERIFIER"] == "1" {
            return .did
        }
        return .named
    }
}
