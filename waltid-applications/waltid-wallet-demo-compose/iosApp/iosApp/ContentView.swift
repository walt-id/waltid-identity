import SwiftUI
import UIKit
import sharedUI

struct ContentView: UIViewControllerRepresentable {
    let attestationBaseUrl: String
    let attestationAttesterPath: String
    let attestationBearerToken: String
    let attestationHostHeader: String

    func makeUIViewController(context: Context) -> UIViewController {
        sharedUI.WalletDemoIosKt.walletDemoViewController(
            attestationBaseUrl: attestationBaseUrl,
            attestationAttesterPath: attestationAttesterPath,
            attestationBearerToken: attestationBearerToken,
            attestationHostHeader: attestationHostHeader
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

#Preview {
    ContentView(
        attestationBaseUrl: "",
        attestationAttesterPath: "",
        attestationBearerToken: "",
        attestationHostHeader: ""
    )
}
