import ExtensionKit
import Foundation
import IdentityDocumentServices
import IdentityDocumentServicesUI
import SwiftUI
import WalletDemoIdentityDocumentSupport

/// Compose demo's IdentityDocumentServices provider.
///
/// Deliberately native SwiftUI and identical to the native demo's provider apart from the namespace:
/// the Compose runtime has no place in a document-provider extension, and the wallet logic it would
/// need is reached through WalletSDK anyway. Everything substantive lives in the shared support
/// module, so the two providers cannot drift apart.
@main
struct ComposeIdentityDocumentProvider: IdentityDocumentProvider {
    private static let namespace = IdentityDocumentNamespace.composeDemo

    var body: some IdentityDocumentRequestScene {
        ISO18013MobileDocumentRequestScene { context in
            BasicAnnexCReviewView(context: context) {
                try await Self.namespace.providerWallet()
            }
        }
    }

    func performRegistrationUpdates() async {
        await IdentityDocumentRegistrationCoordinator(namespace: Self.namespace)
            .reconcileFromPlatformCallback()
    }
}
