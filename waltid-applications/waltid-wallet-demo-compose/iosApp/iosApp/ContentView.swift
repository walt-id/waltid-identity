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

    func makeUIViewController(context: Context) -> UIViewController {
        sharedUI.WalletDemoIosKt.walletDemoViewController(
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
        transactionDataProfilesUrl: ""
    )
}
