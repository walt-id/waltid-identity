import Foundation
import SwiftUI
import WalletDemoSharingUI
import WalletSDK
import UIKit
#if DEBUG
@_spi(WalletDemoTesting) import WalletDemoBackgroundStatus
#else
import WalletDemoBackgroundStatus
#endif
import WalletDemoIdentityDocumentSupport

@main
struct WalletDemoApp: App {
    var body: some Scene {
        WindowGroup {
            #if DEBUG
            if #available(iOS 26.0, *), ProcessInfo.processInfo.environment["E2E_ANNEX_C_SCENARIO"] != nil {
                MockAnnexCScene()
            } else {
                WalletForegroundScene()
            }
            #else
            WalletForegroundScene()
            #endif
        }
    }

    @UIApplicationDelegateAdaptor(NativeBackgroundStatusDelegate.self) private var backgroundStatusDelegate
}

private struct WalletForegroundScene: View {
    @Environment(\.scenePhase) private var scenePhase
    @State private var activated = false
    @State private var pendingURL: URL?

    var body: some View {
        Group {
            if activated || scenePhase == .active {
                WalletForegroundRoot(pendingURL: $pendingURL)
            }
        }
        .onChange(of: scenePhase) { if $0 == .active { activated = true } }
        .onOpenURL { pendingURL = $0 }
    }
}

private struct WalletForegroundRoot: View {
    @Binding var pendingURL: URL?
    #if DEBUG
    @MainActor private static let initialCredentialReadGate = MockInitialCredentialReadGate()
    #endif

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
            var initialCredentialRead: (@Sendable () async -> Void)? = nil
            #if DEBUG
            if env["E2E_MOCK_HOLD_INITIAL_READ"] == "1" {
                initialCredentialRead = { await Self.initialCredentialReadGate.wait() }
            }
            let imageCredential = Self.mockImageCredential(environment: env)
            let statusSnapshot = MockWalletClient.uiTestCredentialStatusSnapshot(env["E2E_MOCK_STATUS_JSON"])
            var seededCredentials = env["E2E_MOCK_STATUS_NOTIFICATION"] == "1" ? [
                Credential(id: "cred-1", format: "jwt_vc_json",
                    credentialDataJSON: #"{"given_name":"Ada"}"#, issuer: "Example Issuer", label: "Travel credential")
            ] : []
            if env["E2E_MOCK_STATUS_NOTIFICATION"] == "1", env["E2E_MOCK_STATUS_QUEUE"] == "1" {
                seededCredentials.append(Credential(id: "photo-id-1", format: "jwt_vc_json",
                    credentialDataJSON: #"{"given_name":"Ada"}"#, issuer: "Example Issuer", label: "Access credential"))
            }
            let registrationUpdate: (@Sendable () async throws -> Void)? = {}
            #else
            let imageCredential: (dataJSON: String, portraitValueJSON: String)? = nil
            let statusSnapshot: CredentialStatusSnapshot? = nil
            let seededCredentials: [Credential] = []
            let registrationUpdate: (@Sendable () async throws -> Void)? = nil
            #endif
            return WalletViewModel(
                walletID: walletID,
                signingProtectionMode: signingProtectionMode,
                walletClient: MockWalletClient(
                    storedCredentials: seededCredentials,
                    initialCredentialRead: initialCredentialRead,
                    operationDelayMilliseconds: delayMilliseconds,
                    verifierStyle: Self.mockVerifierStyle(environment: env),
                    duplicatePresentationOptions: env["E2E_MOCK_DUPLICATE_PRESENTATION_OPTIONS"] == "1",
                    emptyPresentationOptions: env["E2E_MOCK_NO_MATCHES"] == "1",
                    transactionCodeRequired: env["E2E_MOCK_TX_CODE_REQUIRED"] == "1",
                    paymentConsent: {
                        #if DEBUG
                        return try await MockWalletClient.uiTestPaymentConsent(env["E2E_MOCK_PAYMENT"])
                        #else
                        return nil
                        #endif
                    },
                    paymentTransaction: env["E2E_MOCK_PAYMENT"] != "none",
                    responseEncryptionRequired: env["E2E_MOCK_UNENCRYPTED_RESPONSE"] != "1",
                    mdocMetadata: env["E2E_MOCK_MDOC_METADATA"] == "1",
                    sampleCredentialDataJSON: imageCredential?.dataJSON,
                    samplePortraitDisclosureValueJSON: imageCredential?.portraitValueJSON,
                    credentialStatusSnapshot: statusSnapshot
                ),
                identityDocumentRegistrationUpdate: registrationUpdate
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
                signingProtectionMode: signingProtectionMode,
                backgroundStatusScheduler: .shared
            )
        }
        return WalletViewModel(
            walletID: walletID,
            transactionDataProfilesUrl: transactionDataProfilesUrl,
            signingProtectionMode: signingProtectionMode,
            backgroundStatusScheduler: .shared
        )
    }()

    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ContentView(viewModel: viewModel)
        #if DEBUG
        .overlay(alignment: .bottom) {
            MockInitialCredentialReadControl(gate: Self.initialCredentialReadGate)
                .padding(.bottom, 80)
        }
        #endif
        .environment(\.walletDemoBranding, .default)
        .tint(WalletDemoBranding.default.primary)
        .onChange(of: pendingURL) { _ in handlePendingURL() }
        .onReceive(NotificationCenter.default.publisher(for: .backgroundCredentialStatusDidFinish)) { _ in
            viewModel.invalidateBackgroundStatusSnapshot()
        }
        .onReceive(NotificationCenter.default.publisher(for: WalletDemoCredentialStatusNotifications.openHome)) { _ in
            handleStatusNotification()
        }
        .task {
            handlePendingURL()
            handleStatusNotification()
            guard scenePhase == .active else { return }
            viewModel.handleApplicationBecameActive()
        }
        .onChange(of: scenePhase) { phase in
            // Reconciling requests authorization on a first run, and afterwards picks up a status
            // the user changed in Settings - Apple sends no notification either way. Becoming
            // active is the first moment the app can act on it, so it reconciles here rather
            // than polling. The same foreground signal also drives the one-shot biometric
            // unlock prompt, because PinView's scenePhase can stay inactive on a cold launch.
            guard phase == .active else {
                if phase == .background { viewModel.handleApplicationEnteredBackground() }
                return
            }
            handlePendingURL()
            handleStatusNotification()
            viewModel.handleApplicationBecameActive()
            if #available(iOS 26.0, *) {
                Task { await DemoIdentityDocumentRegistration.updateFromPlatformCallback() }
            }
        }
    }

    private func handleStatusNotification() {
        if WalletDemoCredentialStatusNotifications.shared.takeHomeRequest() {
            viewModel.handleCredentialStatusNotification()
        }
    }

    private func handlePendingURL() {
        guard let url = pendingURL else { return }
        pendingURL = nil
        viewModel.handleDeepLink(url)
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
                  "age_over_18": true,
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

private extension Notification.Name {
    static let backgroundCredentialStatusDidFinish = Notification.Name("wallet.credential-status.maintenance.finished")
}

@MainActor
private final class NativeBackgroundStatusDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        WalletDemoCredentialStatusNotifications.shared.register()
        #if DEBUG
        let environment = ProcessInfo.processInfo.environment
        if environment["E2E_MOCK_WALLET"] == "1", environment["E2E_MOCK_STATUS_NOTIFICATION"] == "1" {
            WalletDemoCredentialStatusNotifications.shared.simulateTapForUITest()
        }
        #endif
        WalletDemoBackgroundStatusScheduler.shared.register { binding, finish in
            let task = Task { @MainActor in
                let namespace = IdentityDocumentNamespace.nativeDemo
                guard let keychain = namespace.keychainAccessGroup else {
                    finish(.deferred)
                    return
                }
                do {
                    let result = try await Wallet.refreshDueStatusesForExistingWallet(
                        binding: binding, crossProcessAccess: WalletCrossProcessAccess(
                            appGroupIdentifier: namespace.appGroupIdentifier, keychainAccessGroup: keychain
                        )
                    )
                    switch result {
                    case .finished(_, _, _, let hasNewNotice): finish(hasNewNotice ? .finishedWithNotice : .finished)
                    case .deferred: finish(.deferred)
                    case .ineligible: finish(.ineligible)
                    }
                } catch {
                    finish(.deferred)
                }
            }
            return { task.cancel() }
        } onResult: {
            NotificationCenter.default.post(name: .backgroundCredentialStatusDidFinish, object: nil)
        }
        return true
    }
}

#if DEBUG
@MainActor
private final class MockInitialCredentialReadGate: ObservableObject {
    @Published private(set) var isPending = false
    private var continuation: CheckedContinuation<Void, Never>?

    func wait() async {
        await withCheckedContinuation { continuation in
            self.continuation = continuation
            isPending = true
        }
    }

    func complete() {
        guard let continuation else { return }
        self.continuation = nil
        isPending = false
        continuation.resume()
    }
}

private struct MockInitialCredentialReadControl: View {
    @ObservedObject var gate: MockInitialCredentialReadGate

    var body: some View {
        if gate.isPending {
            Button("Complete mock credential read") { gate.complete() }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("wallet.test.completeInitialRead")
        }
    }
}
#endif

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
    static let transactionDataProfilesURL = "https://wallet2.demo.walt.id/transaction-data-profiles"
}
