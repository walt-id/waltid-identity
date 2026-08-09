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

    func makeUIViewController(context: Context) -> UIViewController {
        sharedUI.WalletDemoIosKt.walletDemoViewController(
            walletId: walletId,
            attestationBaseUrl: attestationBaseUrl,
            attestationAttesterPath: attestationAttesterPath,
            attestationBearerToken: attestationBearerToken,
            attestationHostHeader: attestationHostHeader,
            transactionDataProfilesUrl: transactionDataProfilesUrl,
            appGroupIdentifier: appGroupIdentifier,
            keychainAccessGroup: keychainAccessGroup
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
        keychainAccessGroup: ""
    )
}
