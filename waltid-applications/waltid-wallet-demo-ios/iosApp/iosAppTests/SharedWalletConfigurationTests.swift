import WalletDemoIdentityDocumentSupport
import XCTest

/// Proves the native demo's shared wallet configuration is the one its provider extension resolves.
///
/// This runs in the host app's process, which carries the same App Group and Keychain entitlements as
/// the extension, so the shared assertions exercise the real entitlement configuration rather than a
/// stand-in. It does not run a second process: whether the extension is *entitled* to reach what is
/// found here is decided by the built entitlements, which
/// `.github/scripts/mobile-ci/verify-ios-identity-document-provider.sh` asserts on the built products,
/// and finally by acceptance on a physical device.
final class SharedWalletConfigurationTests: XCTestCase {
    func testNativeDemoProviderWalletReopensTheHostAppsStateAndSigningKey() async throws {
        try await assertWalletReopensSharedStateAndSigningKey(
            namespace: .nativeDemo,
            walletID: "native-shared-configuration-\(UUID().uuidString)"
        )
    }

    func testNativeDemoProviderResolvesThePublishedWalletIDRatherThanAssumingDefault() async throws {
        try await assertProviderResolvesThePublishedWalletID(
            namespace: .nativeDemo,
            walletID: "native-published-wallet-\(UUID().uuidString)"
        )
    }
}
