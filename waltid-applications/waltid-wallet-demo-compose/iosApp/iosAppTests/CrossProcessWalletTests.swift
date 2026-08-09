import WalletDemoIdentityDocumentSupport
import XCTest

/// Proves the Compose demo's host app and provider extension share one wallet.
///
/// The Compose host is the interesting case: its wallet is created from Kotlin, so a namespace or
/// entitlement mismatch here would surface only as a failed presentation on a device. Running in the
/// host app's process means the assertions see the real App Group and Keychain entitlements.
final class CrossProcessWalletTests: XCTestCase {
    func testComposeDemoWalletIsSharedWithItsProviderExtension() async throws {
        try await assertWalletIsSharedAcrossProcesses(
            namespace: .composeDemo,
            walletID: "compose-cross-process-\(UUID().uuidString)"
        )
    }
}
