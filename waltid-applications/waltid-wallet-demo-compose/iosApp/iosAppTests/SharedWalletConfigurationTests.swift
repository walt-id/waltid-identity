import WalletDemoIdentityDocumentSupport
import XCTest

/// Proves the Compose demo's shared wallet configuration is the one its provider extension resolves.
///
/// The Compose host is the interesting case: its wallet is created from Kotlin, so a namespace or
/// entitlement mismatch here would surface only as a failed presentation on a device. Running in the
/// host app's process means the assertions see the real App Group and Keychain entitlements. It does
/// not run a second process; extension-process reachability is settled by the built entitlements and
/// by acceptance on a physical device.
final class SharedWalletConfigurationTests: XCTestCase {
    func testComposeDemoProviderWalletReopensTheHostAppsStateAndSigningKey() async throws {
        try await assertWalletReopensSharedStateAndSigningKey(
            namespace: .composeDemo,
            walletID: "compose-shared-configuration-\(UUID().uuidString)"
        )
    }

    func testComposeDemoProviderResolvesThePublishedWalletIDRatherThanAssumingDefault() async throws {
        try await assertProviderResolvesThePublishedWalletID(
            namespace: .composeDemo,
            walletID: "compose-published-wallet-\(UUID().uuidString)"
        )
    }
}
