import ExtensionKit
import Foundation
import IdentityDocumentServices
import IdentityDocumentServicesUI
import SwiftUI
import WalletDemoIdentityDocumentSupport

/// Native demo's IdentityDocumentServices provider.
///
/// Everything substantive - request mapping, wallet access, consent, the two-stage Annex C
/// submission, and registration reconciliation - lives in the shared support module, which the
/// Compose demo's provider uses too.
@main
struct WaltIdentityDocumentProvider: IdentityDocumentProvider {
    private static let namespace = IdentityDocumentNamespace.nativeDemo

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
