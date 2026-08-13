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
                // The wallet republishes its desired projection, which is not Apple's store; only this
                // process may write that.
                onDigitalCredentialRegistryChanged: {
                    Task { await Self.reconcileRegistrations() }
                }
            )
            .ignoresSafeArea()
            .onOpenURL { url in
                sharedUI.WalletDemoIosKt.handleWalletDemoDeepLink(url: url.absoluteString)
            }
            .task {
                await Self.reconcileRegistrations()
            }
            .onChange(of: scenePhase) { phase in
                // Reconciling requests authorization on a first run, and afterwards picks up a status
                // the user changed in Settings - neither carries a notification, so becoming active
                // is the first moment this app can act on it.
                guard phase == .active else { return }
                Task { await Self.reconcileRegistrations() }
            }
        }
    }

    private static let namespace = IdentityDocumentNamespace.composeDemo

    /// The build-expanded shared Keychain group, or a crash naming what is missing.
    ///
    /// With the wrong group the wallet stores its signing key where the extension cannot read it, and
    /// every presentation fails at signing with no earlier symptom.
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
