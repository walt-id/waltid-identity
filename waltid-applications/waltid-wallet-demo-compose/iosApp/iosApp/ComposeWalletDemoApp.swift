import SwiftUI
import WalletDemoIdentityDocumentSupport
@preconcurrency import sharedUI
import UIKit
import WalletDemoBackgroundStatus

@main
struct ComposeWalletDemoApp: App {
    @UIApplicationDelegateAdaptor(ComposeBackgroundStatusDelegate.self) private var backgroundStatusDelegate
    @Environment(\.scenePhase) private var scenePhase
    @State private var activated = false
    @State private var enteredForeground = false

    private let walletId: String
    private let attestationBaseUrl: String
    private let attestationAttesterPath: String
    private let attestationBearerToken: String
    private let attestationHostHeader: String
    private let transactionDataProfilesUrl: String
    private let signingProtectionMode: String

    init() {
        let env = ProcessInfo.processInfo.environment
        let defaults = UserDefaults.standard
        walletId = env["WALLET_ID"] ?? defaults.string(forKey: "WALLET_ID") ?? "default"
        attestationBaseUrl = env["ATTESTATION_BASE_URL"] ?? defaults.string(forKey: "ATTESTATION_BASE_URL") ?? DemoBackendDefaults.attestationBaseURL
        attestationAttesterPath = env["ATTESTATION_ATTESTER_PATH"] ?? defaults.string(forKey: "ATTESTATION_ATTESTER_PATH") ?? DemoBackendDefaults.attestationAttesterPath
        attestationBearerToken = env["ATTESTATION_BEARER_TOKEN"] ?? defaults.string(forKey: "ATTESTATION_BEARER_TOKEN") ?? DemoBackendDefaults.attestationBearerToken
        attestationHostHeader = env["ATTESTATION_HOST_HEADER"] ?? defaults.string(forKey: "ATTESTATION_HOST_HEADER") ?? DemoBackendDefaults.attestationHostHeader
        transactionDataProfilesUrl = env["TRANSACTION_DATA_PROFILES_URL"] ?? defaults.string(forKey: "TRANSACTION_DATA_PROFILES_URL") ?? DemoBackendDefaults.transactionDataProfilesURL
        signingProtectionMode = walletSigningProtectionMode(environment: env, defaults: defaults)
    }

    var body: some Scene {
        WindowGroup {
            Group {
                if activated || scenePhase == .active {
                    foregroundWallet
                }
            }
            .onChange(of: scenePhase) { if $0 == .active { activated = true } }
            .onOpenURL { url in
                sharedUI.WalletDemoIosKt.handleWalletDemoDeepLink(url: url.absoluteString)
            }
        }
    }

    private var foregroundWallet: some View {
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
            },
            signingProtectionMode: signingProtectionMode,
            onBackgroundStatusEnrollmentChanged: { WalletDemoBackgroundStatusScheduler.shared.reconcile(binding: $0) },
            backgroundStatusSchedulingAvailable: { WalletDemoBackgroundStatusScheduler.shared.canSchedule }
        )
        .ignoresSafeArea()
        .task { await enterForeground() }
        .onChange(of: scenePhase) { phase in
            if phase == .background {
                enteredForeground = false
                sharedUI.WalletDemoIosKt.handleWalletDemoApplicationBackgrounded()
            }
            if phase == .active { Task { await enterForeground() } }
        }
    }

    private func enterForeground() async {
        guard scenePhase == .active else { return }
        activated = true
        sharedUI.WalletDemoIosKt.setWalletDemoBackgroundStatusSchedulingAvailable(
            available: WalletDemoBackgroundStatusScheduler.shared.canSchedule
        )
        guard !enteredForeground else { return }
        enteredForeground = true
        sharedUI.WalletDemoIosKt.handleWalletDemoApplicationForegrounded()
        await Self.reconcileRegistrations()
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

@MainActor
private final class ComposeBackgroundStatusDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        WalletDemoBackgroundStatusScheduler.shared.register { binding, finish in
            let namespace = IdentityDocumentNamespace.composeDemo
            guard let keychain = namespace.keychainAccessGroup else {
                finish(false)
                return {}
            }
            let run = sharedUI.WalletDemoIosKt.startWalletDemoBackgroundStatus(
                binding: binding, appGroupIdentifier: namespace.appGroupIdentifier, keychainAccessGroup: keychain,
                onFinished: { success in
                    MainActor.assumeIsolated { finish(success.boolValue) }
                }
            )
            return { run.cancel() }
        } onResult: {
            sharedUI.WalletDemoIosKt.handleWalletDemoBackgroundStatusCompleted()
        }
        return true
    }
}

private func walletSigningProtectionMode(environment: [String: String], defaults: UserDefaults) -> String {
    let mode = environment["WALLET_SIGNING_PROTECTION_MODE"]
        ?? defaults.string(forKey: "WALLET_SIGNING_PROTECTION_MODE")
        ?? "optional"
    let normalized = mode.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    precondition(["required", "optional", "disabled"].contains(normalized))
    return normalized
}

private enum DemoBackendDefaults {
    static let attestationBaseURL = ""
    static let attestationAttesterPath = ""
    static let attestationBearerToken = ""
    static let attestationHostHeader = ""
    static let transactionDataProfilesURL = "https://wallet2.demo.walt.id/transaction-data-profiles"
}
