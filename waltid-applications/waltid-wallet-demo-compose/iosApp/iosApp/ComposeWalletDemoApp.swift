import SwiftUI
import WalletDemoIdentityDocumentSupport
import sharedUI

@main
struct ComposeWalletDemoApp: App {
    @Environment(\.scenePhase) private var scenePhase

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
                transactionDataProfilesUrl: transactionDataProfilesUrl,
                appGroupIdentifier: Self.namespace.appGroupIdentifier,
                keychainAccessGroup: Self.requiredKeychainAccessGroup,
                // Issuance and deletion republish the wallet's desired projection, which is not
                // Apple's store; pushing it there is this process's privilege. Reconciling on the
                // notification rather than on the next foreground transition is what makes a freshly
                // issued credential presentable now.
                onDigitalCredentialRegistryChanged: {
                    Task { await Self.reconcileRegistrations() }
                }
            )
            .ignoresSafeArea()
            .onOpenURL { url in
                sharedUI.WalletDemoIosKt.handleWalletDemoDeepLink(url: url.absoluteString)
            }
            .task {
                // Startup reconciliation: the Compose wallet republishes its desired projection on
                // every mutation, but only Swift can push it into Apple's store, and the extension's
                // performRegistrationUpdates() may not have run since the last change.
                await Self.reconcileRegistrations()
            }
            .onChange(of: scenePhase) { phase in
                // Provider authorization is granted in Settings, outside this app, and Apple sends no
                // notification when it changes. Becoming active is the first moment the app can
                // observe the new status, so it reconciles here instead of polling.
                guard phase == .active else { return }
                Task { await Self.reconcileRegistrations() }
            }
        }
    }

    private static let namespace = IdentityDocumentNamespace.composeDemo

    /// The build-expanded shared Keychain group, or a crash naming what is missing.
    ///
    /// This target embeds a document-provider extension, so there is no meaningful degraded mode: with
    /// the wrong Keychain group the wallet stores its signing key where the extension cannot read it,
    /// and every presentation fails at the point of signing with no earlier symptom. Failing at launch
    /// points at the actual cause - an Info.plist key that the build did not expand - instead.
    private static var requiredKeychainAccessGroup: String {
        guard let keychainAccessGroup = namespace.keychainAccessGroup else {
            fatalError(
                IdentityDocumentSupportFailure
                    .unresolvedKeychainAccessGroup(IdentityDocumentNamespace.keychainAccessGroupInfoKey)
                    .localizedDescription
            )
        }
        return keychainAccessGroup
    }

    private static func reconcileRegistrations() async {
        guard #available(iOS 26.0, *) else { return }
        await IdentityDocumentRegistrationCoordinator(namespace: namespace)
            .reconcileFromPlatformCallback()
    }
}

private enum DemoBackendDefaults {
    static let attestationBaseURL = ""
    static let attestationAttesterPath = ""
    static let attestationBearerToken = ""
    static let attestationHostHeader = ""
    static let transactionDataProfilesURL = "https://wallet.demo.walt.id/wallet-api/transaction-data-profiles"
}
