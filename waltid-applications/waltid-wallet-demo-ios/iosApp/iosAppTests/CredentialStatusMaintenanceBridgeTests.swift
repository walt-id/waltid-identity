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

            let paused = try await wallet.reconcileCredentialStatusMaintenance(automaticChecks: false)
            XCTAssertEqual(paused?.preference, .paused)
            XCTAssertNil(paused?.binding)
            let retired = try await Wallet.refreshDueStatusesForExistingWallet(binding: original)
            XCTAssertEqual(retired, .ineligible)

            let interrupted = try await wallet.reconcileCredentialStatusMaintenance(automaticChecks: true)
            XCTAssertEqual(interrupted?.preference, .paused)
            XCTAssertNil(interrupted?.binding)
            let resumed = try await wallet.reconcileCredentialStatusMaintenance(automaticChecks: true, resumePaused: true)
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
