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
                    duplicatePresentationOptions: env["E2E_MOCK_DUPLICATE_PRESENTATION_OPTIONS"] == "1",
                    transactionCodeRequired: env["E2E_MOCK_TX_CODE_REQUIRED"] == "1",
                    responseEncryptionRequired: env["E2E_MOCK_UNENCRYPTED_RESPONSE"] != "1"
                )
            )
        }
        let baseUrl = env["ATTESTATION_BASE_URL"] ?? defaults.string(forKey: "ATTESTATION_BASE_URL") ?? DemoBackendDefaults.attestationBaseURL
        let transactionDataProfilesUrl = env["TRANSACTION_DATA_PROFILES_URL"] ?? defaults.string(forKey: "TRANSACTION_DATA_PROFILES_URL") ?? DemoBackendDefaults.transactionDataProfilesURL
        if !baseUrl.isEmpty {
            return WalletViewModel(
                walletID: walletID,
                attestationBaseUrl: baseUrl,
                attestationAttesterPath: env["ATTESTATION_ATTESTER_PATH"] ?? defaults.string(forKey: "ATTESTATION_ATTESTER_PATH") ?? DemoBackendDefaults.attestationAttesterPath,
                attestationBearerToken: env["ATTESTATION_BEARER_TOKEN"] ?? defaults.string(forKey: "ATTESTATION_BEARER_TOKEN") ?? DemoBackendDefaults.attestationBearerToken,
                attestationHostHeader: env["ATTESTATION_HOST_HEADER"] ?? defaults.string(forKey: "ATTESTATION_HOST_HEADER") ?? DemoBackendDefaults.attestationHostHeader,
                transactionDataProfilesUrl: transactionDataProfilesUrl
            )
        }
        return WalletViewModel(
            walletID: walletID,
            transactionDataProfilesUrl: transactionDataProfilesUrl
        )
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

private enum DemoBackendDefaults {
    static let attestationBaseURL = ""
    static let attestationAttesterPath = ""
    static let attestationBearerToken = ""
    static let attestationHostHeader = ""
    static let transactionDataProfilesURL = "https://wallet.demo.walt.id/wallet-api/transaction-data-profiles"
}
