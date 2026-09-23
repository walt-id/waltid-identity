import Foundation
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
        let signingProtectionMode = walletSigningProtectionMode(environment: env, defaults: defaults)
        if env["E2E_MOCK_WALLET"] == "1" {
            let delayMilliseconds = UInt64(env["E2E_MOCK_WALLET_DELAY_MS"] ?? "") ?? 0
            #if DEBUG
            let imageCredential = Self.mockImageCredential(environment: env)
            #else
            let imageCredential: (dataJSON: String, portraitValueJSON: String)? = nil
            #endif
            return WalletViewModel(
                walletID: walletID,
                signingProtectionMode: signingProtectionMode,
                walletClient: MockWalletClient(
                    operationDelayMilliseconds: delayMilliseconds,
                    verifierStyle: Self.mockVerifierStyle(environment: env),
                    duplicatePresentationOptions: env["E2E_MOCK_DUPLICATE_PRESENTATION_OPTIONS"] == "1",
                    transactionCodeRequired: env["E2E_MOCK_TX_CODE_REQUIRED"] == "1",
                    responseEncryptionRequired: env["E2E_MOCK_UNENCRYPTED_RESPONSE"] != "1",
                    mdocMetadata: env["E2E_MOCK_MDOC_METADATA"] == "1",
                    sampleCredentialDataJSON: imageCredential?.dataJSON,
                    samplePortraitDisclosureValueJSON: imageCredential?.portraitValueJSON
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
                signingProtectionMode: signingProtectionMode
            )
        }
        return WalletViewModel(
            walletID: walletID,
            transactionDataProfilesUrl: transactionDataProfilesUrl,
            signingProtectionMode: signingProtectionMode
        )
    }()

    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView(viewModel: viewModel)
            .environment(\.walletDemoBranding, .default)
            .tint(WalletDemoBranding.default.primary)
            .onOpenURL { url in
                viewModel.handleDeepLink(url)
            }
            .task {
                guard scenePhase == .active else { return }
                viewModel.handleApplicationBecameActive()
            }
            .onChange(of: scenePhase) { phase in
                // Reconciling requests authorization on a first run, and afterwards picks up a status
                // the user changed in Settings - Apple sends no notification either way. Becoming
                // active is the first moment the app can act on it, so it reconciles here rather
                // than polling. The same foreground signal also drives the one-shot biometric
                // unlock prompt, because PinView's scenePhase can stay inactive on a cold launch.
                guard phase == .active else { return }
                viewModel.handleApplicationBecameActive()
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

    #if DEBUG
    private static func mockImageCredential(
        environment: [String: String]
    ) -> (dataJSON: String, portraitValueJSON: String)? {
        guard let portrait = environment["E2E_MOCK_PORTRAIT_DATA_URL"],
              let signature = environment["E2E_MOCK_SIGNATURE_DATA_URL"],
              let verificationDocument = environment["E2E_MOCK_VERIFICATION_DOCUMENT_DATA_URL"],
              let data = try? JSONSerialization.data(withJSONObject: [
                  "vct": "https://issuer.example/credential-types/mobile-driving-licence",
                  "given_name": "Ada",
                  "family_name": "Lovelace",
                  "valid_to": 1_781_654_400,
                  "resident_address": [
                      "street_address": "Main Street 1",
                      "locality": "Vienna",
                  ],
                  "portrait": portrait,
                  "signature_usual_mark": signature,
                  "verification_artifact": verificationDocument,
              ]),
              let dataJSON = String(data: data, encoding: .utf8),
              let portraitJSONData = try? JSONEncoder().encode(portrait),
              let portraitValueJSON = String(data: portraitJSONData, encoding: .utf8) else {
            return nil
        }
        return (dataJSON, portraitValueJSON)
    }
    #endif
}

private func walletSigningProtectionMode(
    environment: [String: String],
    defaults: UserDefaults
) -> WalletDemoSigningProtectionMode {
    let rawValue = environment["WALLET_SIGNING_PROTECTION_MODE"]
        ?? defaults.string(forKey: "WALLET_SIGNING_PROTECTION_MODE")
        ?? WalletDemoSigningProtectionMode.optional.rawValue
    guard let mode = WalletDemoSigningProtectionMode(
        rawValue: rawValue.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    ) else {
        preconditionFailure(
            "WALLET_SIGNING_PROTECTION_MODE must be required, optional, or disabled"
        )
    }
    return mode
}

private enum DemoBackendDefaults {
    static let attestationBaseURL = ""
    static let attestationAttesterPath = ""
    static let attestationBearerToken = ""
    static let attestationHostHeader = ""
    static let transactionDataProfilesURL = "https://wallet.demo.walt.id/wallet-api/transaction-data-profiles"
}
