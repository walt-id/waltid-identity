#if canImport(WalletCore) && os(iOS)
import XCTest
@preconcurrency import WalletCore
@testable import WalletSDK

final class SigningIdentityBridgeTests: XCTestCase {
    func testRecoveryReceiptsPreserveWhoConfirmed() {
        XCTAssertEqual(WalletCore.RecoveryReceipt.acceptedLocally.toSwiftReceipt(), .acceptedLocally)
        XCTAssertEqual(WalletCore.RecoveryReceipt.confirmedByProvider.toSwiftReceipt(), .confirmedByProvider)
        XCTAssertEqual(WalletRecoveryReceipt.acceptedLocally.toKMPReceipt(), .acceptedLocally)
        XCTAssertEqual(WalletRecoveryReceipt.confirmedByProvider.toKMPReceipt(), .confirmedByProvider)
    }

    func testConfirmationAndLocalRetentionVaryIndependently() throws {
        let local = try WalletSDK.SigningIdentityConfiguration(
            recoveryConfirmation: .localAcceptance, localRecoveryMaterial: .discardAfterConfirmation
        ).toKMPSigningIdentityConfiguration()
        XCTAssertEqual(local.recoveryConfirmation, .localAcceptance)
        XCTAssertEqual(local.localRecoveryMaterial, .discardAfterConfirmation)

        let provider = try WalletSDK.SigningIdentityConfiguration(
            recoveryConfirmation: .providerConfirmation, localRecoveryMaterial: .retain
        ).toKMPSigningIdentityConfiguration()
        XCTAssertEqual(provider.recoveryConfirmation, .providerConfirmation)
        XCTAssertEqual(provider.localRecoveryMaterial, .retain)
    }
}
#endif
