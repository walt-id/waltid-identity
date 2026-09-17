#if os(iOS)
import Foundation
import XCTest
import WalletSDK
import WalletSDKKeychainRecovery

/// Mirrors the Kotlin Keychain adapter contract using the same record bytes and namespace rules.
final class KeychainRecoveryContractTests: XCTestCase {
    func testCompatibleAccessibilityClassesPreserveTheRecordContract() async throws {
        for accessibility: SynchronizableKeychainAccessibility in [.whenUnlocked, .afterFirstUnlock] {
            let namespace = "contract-\(UUID())"
            let provider = KeychainIdentityRecovery(namespace: namespace, accessibility: accessibility)
            let isolated = KeychainIdentityRecovery(namespace: "other-\(UUID())")
            let recordID = "record-1"
            let data = Data("synthetic-parity-fixture".utf8)
            do {
                XCTAssertEqual(provider.id, "keychain:\(namespace)")
                let first = try await provider.store(recordID: recordID, data: data)
                let repeated = try await provider.store(recordID: recordID, data: data)
                XCTAssertEqual(first, .acceptedLocally)
                XCTAssertEqual(repeated, .acceptedLocally)
                let ids = try await provider.list()
                XCTAssertEqual(ids, [recordID])
                let unrelated = try await isolated.retrieve(recordID: recordID)
                XCTAssertNil(unrelated)
                let reopened = KeychainIdentityRecovery(namespace: namespace, accessibility: accessibility)
                let restored = try await reopened.retrieve(recordID: recordID)
                XCTAssertEqual(restored, data)
                do {
                    _ = try await provider.store(recordID: recordID, data: Data([1]))
                    XCTFail("Different data must not replace an existing record")
                } catch let error as IdentityProviderError { XCTAssertEqual(error, .conflict) }
                let retained = try await reopened.retrieve(recordID: recordID)
                XCTAssertEqual(retained, data)
                _ = try await provider.delete(recordID: recordID)
                _ = try await provider.delete(recordID: recordID)
                let removed = try await reopened.retrieve(recordID: recordID)
                XCTAssertNil(removed)
            } catch { _ = try? await provider.delete(recordID: recordID); throw error }
        }
    }
}
#endif
