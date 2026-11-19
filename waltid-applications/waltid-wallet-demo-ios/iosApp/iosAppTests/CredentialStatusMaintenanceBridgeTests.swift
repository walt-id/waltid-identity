import Foundation
import WalletSDK
import XCTest

final class CredentialStatusMaintenanceBridgeTests: XCTestCase {
    func testRealSwiftFacadeUsesExistingEncryptedStorageAndRetiresAnOldBinding() async throws {
        let wallet = try await Wallet(configuration: WalletConfiguration(walletID: "status-maintenance-\(UUID())"))
        do {
            let initial = try await wallet.credentialStatusMaintenanceEnrollment()
            XCTAssertEqual(initial?.preference, .off)
            let enabled = try await wallet.setCredentialStatusMaintenance(.enabled)
            let original = try XCTUnwrap(enabled?.binding)
            let finished = try await Wallet.refreshDueStatusesForExistingWallet(binding: original)
            XCTAssertEqual(finished, .finished(checked: 0, unchanged: 0, remaining: 0))

            let paused = try await wallet.setCredentialStatusMaintenance(.paused)
            XCTAssertEqual(paused?.preference, .paused)
            XCTAssertNil(paused?.binding)
            let retired = try await Wallet.refreshDueStatusesForExistingWallet(binding: original)
            XCTAssertEqual(retired, .ineligible)

            let resumed = try await wallet.setCredentialStatusMaintenance(.enabled)
            let replacement = try XCTUnwrap(resumed?.binding)
            XCTAssertNotEqual(replacement, original)
            try await wallet.deleteLocalData()
            let absent = try await Wallet.refreshDueStatusesForExistingWallet(binding: replacement)
            XCTAssertEqual(absent, .ineligible)
        } catch {
            try? await wallet.deleteLocalData()
            throw error
        }
    }
}
