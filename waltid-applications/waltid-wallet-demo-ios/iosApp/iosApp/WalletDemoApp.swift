import SwiftUI
import WalletDemoSharingUI

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
        let biometricEnabled = walletBiometricEnabled(environment: env, defaults: defaults)
        if env["E2E_MOCK_WALLET"] == "1" {
            let delayMilliseconds = UInt64(env["E2E_MOCK_WALLET_DELAY_MS"] ?? "") ?? 0
            return WalletViewModel(
                walletID: walletID,
                biometricEnabled: biometricEnabled,
                walletClient: MockWalletClient(
                    storedCredentials: env["E2E_MOCK_STORED_CREDENTIAL"] == "1"
                        ? [MockWalletClient.sampleCredential]
                        : [],
                    operationDelayMilliseconds: delayMilliseconds,
                    verifierStyle: Self.mockVerifierStyle(environment: env),
                    duplicatePresentationOptions: env["E2E_MOCK_DUPLICATE_PRESENTATION_OPTIONS"] == "1",
                    transactionCodeRequired: env["E2E_MOCK_TX_CODE_REQUIRED"] == "1",
                    responseEncryptionRequired: env["E2E_MOCK_UNENCRYPTED_RESPONSE"] != "1",
                    mdocMetadata: env["E2E_MOCK_MDOC_METADATA"] == "1"
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
                transactionDataProfilesUrl: transactionDataProfilesUrl,
                biometricEnabled: biometricEnabled
            )
        }
        return WalletViewModel(
            walletID: walletID,
            transactionDataProfilesUrl: transactionDataProfilesUrl,
            biometricEnabled: biometricEnabled
        )
    }()

    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView(viewModel: viewModel)
            .tint(.waltBlue)
            .onOpenURL { url in
                viewModel.handleIncomingURL(url)
            }
            .onChange(of: scenePhase) { phase in
                // Reconciling requests authorization on a first run, and afterwards picks up a status
                // the user changed in Settings - Apple sends no notification either way. Becoming
                // active is the first moment the app can act on it, so it reconciles here rather
                // than polling.
                guard phase == .active else { return }
                if #available(iOS 26.0, *) {
                    Task { await DemoIdentityDocumentRegistration.updateFromPlatformCallback() }
                }
            }
        }
    }

    private static func mockVerifierStyle(environment: [String: String]) -> MockWalletClient.VerifierStyle {
        if environment["E2E_MOCK_DID_VERIFIER"] == "1" {
            return .did
        }
        return .named
    }
}

private func walletBiometricEnabled(environment: [String: String], defaults: UserDefaults) -> Bool {
    let rawValue = environment["WALLET_BIOMETRIC_ENABLED"] ?? defaults.string(forKey: "WALLET_BIOMETRIC_ENABLED")
    guard let rawValue else { return true }
    switch rawValue.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
    case "0", "false", "no", "off": return false
    case "1", "true", "yes", "on": return true
    default: return true
    }
}

private enum DemoBackendDefaults {
    static let attestationBaseURL = ""
    static let attestationAttesterPath = ""
    static let attestationBearerToken = ""
    static let attestationHostHeader = ""
    static let transactionDataProfilesURL = "https://wallet.demo.walt.id/wallet-api/transaction-data-profiles"
}
