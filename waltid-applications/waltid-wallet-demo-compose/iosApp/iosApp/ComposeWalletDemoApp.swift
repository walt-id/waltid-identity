import SwiftUI
import sharedUI

@main
struct ComposeWalletDemoApp: App {
    private let walletId: String
    private let attestationBaseUrl: String
    private let attestationAttesterPath: String
    private let attestationBearerToken: String
    private let attestationHostHeader: String
    private let transactionDataProfilesUrl: String

    init() {
        let env = ProcessInfo.processInfo.environment
        let defaults = UserDefaults.standard
        walletId = env["WALLET_ID"] ?? defaults.string(forKey: "WALLET_ID") ?? "default"
        attestationBaseUrl = env["ATTESTATION_BASE_URL"] ?? defaults.string(forKey: "ATTESTATION_BASE_URL") ?? DemoBackendDefaults.attestationBaseURL
        attestationAttesterPath = env["ATTESTATION_ATTESTER_PATH"] ?? defaults.string(forKey: "ATTESTATION_ATTESTER_PATH") ?? DemoBackendDefaults.attestationAttesterPath
        attestationBearerToken = env["ATTESTATION_BEARER_TOKEN"] ?? defaults.string(forKey: "ATTESTATION_BEARER_TOKEN") ?? DemoBackendDefaults.attestationBearerToken
        attestationHostHeader = env["ATTESTATION_HOST_HEADER"] ?? defaults.string(forKey: "ATTESTATION_HOST_HEADER") ?? DemoBackendDefaults.attestationHostHeader
        transactionDataProfilesUrl = env["TRANSACTION_DATA_PROFILES_URL"] ?? defaults.string(forKey: "TRANSACTION_DATA_PROFILES_URL") ?? DemoBackendDefaults.transactionDataProfilesURL
    }

    var body: some Scene {
        WindowGroup {
            ContentView(
                walletId: walletId,
                attestationBaseUrl: attestationBaseUrl,
                attestationAttesterPath: attestationAttesterPath,
                attestationBearerToken: attestationBearerToken,
                attestationHostHeader: attestationHostHeader,
                transactionDataProfilesUrl: transactionDataProfilesUrl
            )
            .ignoresSafeArea()
            .onOpenURL { url in
                sharedUI.WalletDemoIosKt.handleWalletDemoDeepLink(url: url.absoluteString)
            }
        }
    }
}

private enum DemoBackendDefaults {
    static let attestationBaseURL = ""
    static let attestationAttesterPath = ""
    static let attestationBearerToken = ""
    static let attestationHostHeader = ""
    static let transactionDataProfilesURL = "https://wallet.demo.walt.id/wallet-api/transaction-data-profiles"
}
