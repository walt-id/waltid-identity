import SwiftUI
import sharedUI

@main
struct WalletDemoIosApp: App {
    private let attestationBaseUrl: String
    private let attestationAttesterPath: String
    private let attestationBearerToken: String
    private let attestationHostHeader: String

    init() {
        let env = ProcessInfo.processInfo.environment
        let defaults = UserDefaults.standard
        attestationBaseUrl = env["ATTESTATION_BASE_URL"] ?? defaults.string(forKey: "ATTESTATION_BASE_URL") ?? ""
        attestationAttesterPath = env["ATTESTATION_ATTESTER_PATH"] ?? defaults.string(forKey: "ATTESTATION_ATTESTER_PATH") ?? ""
        attestationBearerToken = env["ATTESTATION_BEARER_TOKEN"] ?? defaults.string(forKey: "ATTESTATION_BEARER_TOKEN") ?? ""
        attestationHostHeader = env["ATTESTATION_HOST_HEADER"] ?? defaults.string(forKey: "ATTESTATION_HOST_HEADER") ?? ""
    }

    var body: some Scene {
        WindowGroup {
            ContentView(
                attestationBaseUrl: attestationBaseUrl,
                attestationAttesterPath: attestationAttesterPath,
                attestationBearerToken: attestationBearerToken,
                attestationHostHeader: attestationHostHeader
            )
            .ignoresSafeArea()
            .onOpenURL { url in
                sharedUI.WalletDemoIosKt.handleWalletDemoDeepLink(url: url.absoluteString)
            }
        }
    }
}
