import SwiftUI
import UIKit
import sharedUI

struct ContentView: UIViewControllerRepresentable {
    let walletId: String
    let attestationBaseUrl: String
    let attestationAttesterPath: String
    let attestationBearerToken: String
    let attestationHostHeader: String
    let transactionDataProfilesUrl: String
    /// App Group the Compose wallet shares with the document-provider extension.
    let appGroupIdentifier: String
    /// Build-expanded shared Keychain access group; empty when this build has no such entitlement.
    let keychainAccessGroup: String
    /// Called from Kotlin after the wallet's credential set changed, so this process can reconcile
    /// Apple's registration store. Only the app may call `IdentityDocumentServices`.
    let onDigitalCredentialRegistryChanged: () -> Void
    let signingProtectionMode: String

    func makeUIViewController(context: Context) -> UIViewController {
        let nfcHost = ComposeNfcHostPlatformAdapter()
        return sharedUI.WalletDemoIosKt.walletDemoViewController(
            appGroupIdentifier: appGroupIdentifier,
            keychainAccessGroup: keychainAccessGroup,
            nfcHostPlatformAdapter: nfcHost,
            systemPresentationActive: { KotlinBoolean(bool: nfcHost.isPresenting) },
            requestNfcPresentment: { Task { await nfcHost.present() } },
            onDigitalCredentialRegistryChanged: onDigitalCredentialRegistryChanged,
            walletId: walletId,
            attestationBaseUrl: attestationBaseUrl,
            attestationAttesterPath: attestationAttesterPath,
            attestationBearerToken: attestationBearerToken,
            attestationHostHeader: attestationHostHeader,
            transactionDataProfilesUrl: transactionDataProfilesUrl,
            signingProtectionMode: signingProtectionMode
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

// Preview reusable content in WalletDemoSharingUI or the shared Compose component
// previews. Constructing this host initializes a real wallet and is not a preview fixture.
