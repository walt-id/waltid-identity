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

    func makeUIViewController(context: Context) -> UIViewController {
        sharedUI.WalletDemoIosKt.walletDemoViewController(
            appGroupIdentifier: appGroupIdentifier,
            keychainAccessGroup: keychainAccessGroup,
            onDigitalCredentialRegistryChanged: onDigitalCredentialRegistryChanged,
            walletId: walletId,
            attestationBaseUrl: attestationBaseUrl,
            attestationAttesterPath: attestationAttesterPath,
            attestationBearerToken: attestationBearerToken,
            attestationHostHeader: attestationHostHeader,
            transactionDataProfilesUrl: transactionDataProfilesUrl
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

#Preview {
    ContentView(
        walletId: "default",
        attestationBaseUrl: "",
        attestationAttesterPath: "",
        attestationBearerToken: "",
        attestationHostHeader: "",
        transactionDataProfilesUrl: "",
        appGroupIdentifier: "",
        keychainAccessGroup: "",
        onDigitalCredentialRegistryChanged: {}
    )
}
