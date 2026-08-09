import WalletDemoIdentityDocumentSupport
import XCTest

/// Proves the native demo's host app and provider extension share one wallet.
///
/// This runs in the host app's process, which carries the same App Group and Keychain entitlements as
/// the extension, so the shared assertions exercise the real entitlement configuration rather than a
/// stand-in.
final class CrossProcessWalletTests: XCTestCase {
    func testNativeDemoWalletIsSharedWithItsProviderExtension() async throws {
        try await assertWalletIsSharedAcrossProcesses(
            namespace: .nativeDemo,
            walletID: "native-cross-process-\(UUID().uuidString)"
        )
    }
}
