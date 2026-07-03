import XCTest
@testable import iosApp
import TestHelpers
import WalletSDK

/// iOS integration tests for the mobile wallet library.
///
/// Tests the Swift package facade that iOS apps consume directly.
/// Uses real iOS Keychain crypto, SQLDelight persistence, and OID4VCI/VP protocol
/// against the public EUDI test backend.
///
/// These are integration tests (not E2E UI tests) - they test the library directly
/// without UI automation.
final class MobileWalletIntegrationTests: XCTestCase {

    private let testWalletId = "ios-unit-test-wallet"

    // Timeouts (aligned with Android for cross-platform consistency)
    private let verifierPollingTimeout: TimeInterval = 30  // 30 sec - backend verification

    // MARK: - Test Lifecycle

    override func setUp() async throws {
        try await super.setUp()

        // Clean up test state before each test to ensure isolation
        // This prevents flakiness from state bleed between tests
        await clearTestData()
    }

    /// Clears all test data (database only) to ensure test isolation
    private func clearTestData() async {
        let fileManager = FileManager.default
        if let appSupport = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
            let databaseDirectories = [
                appSupport,
                appSupport.appendingPathComponent("databases", isDirectory: true)
            ]
            let dbFiles = [
                "wallet_\(testWalletId).db",
                "wallet_\(testWalletId).db-shm",
                "wallet_\(testWalletId).db-wal"
            ]
            for directory in databaseDirectories {
                for dbFile in dbFiles {
                    let dbPath = directory.appendingPathComponent(dbFile)
                    try? fileManager.removeItem(at: dbPath)
                }
            }
        }

        // Note: We intentionally do NOT delete keychain keys here.
        // Deleting ALL kSecClassKey items would wipe out keys created during the test itself.
        // The wallet tracks key references in the database, so deleting the database
        // orphans any old keys - they remain in keychain but won't be used.
        // This is acceptable for tests (keychain clears between simulator resets).
    }

    private func makeWallet() async throws -> Wallet {
        try await Wallet(
            configuration: WalletConfiguration(walletID: testWalletId)
        )
    }

    // MARK: - Tests (mirror Android MobileWalletIntegrationTest.kt)

    func testBootstrapCreatesKeyAndDid() async throws {
        let wallet = try await makeWallet()

        let result = try await wallet.bootstrap()

        XCTAssertTrue(result.did.starts(with: "did:"), "DID should start with 'did:', got: \(result.did)")
    }

    func testSdkManagedEncryptedWalletBootstrapsAcrossRecreation() async throws {
        let wallet1 = try await makeWallet()

        let first = try await wallet1.bootstrap()
        XCTAssertTrue(first.did.starts(with: "did:"), "DID should start with 'did:', got: \(first.did)")

        let wallet2 = try await makeWallet()
        let second = try await wallet2.bootstrap()

        XCTAssertEqual(second.did, first.did, "Encrypted wallet state should survive wallet facade recreation")
        XCTAssertEqual(second.keyID, first.keyID, "Encrypted wallet key reference should survive wallet facade recreation")
    }

    func testDeleteLocalDataRemovesSdkManagedEncryptedWalletState() async throws {
        let wallet1 = try await makeWallet()
        let first = try await wallet1.bootstrap()

        try await wallet1.deleteLocalData()

        let wallet2 = try await makeWallet()
        let second = try await wallet2.bootstrap()

        XCTAssertNotEqual(second.did, first.did, "Deleting local data should remove the persisted DID state")
        XCTAssertNotEqual(second.keyID, first.keyID, "Deleting local data should remove the persisted platform key reference")
    }

    func testReceiveCredentialFromEudi() async throws {
        let wallet = try await makeWallet()
        _ = try await wallet.bootstrap()

        let offer = try await EudiTestBackend.shared.generateOffer()
        let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
        let credentialIDs = try await wallet.receive(offer: offerURL)

        XCTAssertFalse(credentialIDs.isEmpty, "Should receive at least one credential")
    }

    func testReceiveAndPresentFullFlow() async throws {
        let wallet = try await makeWallet()

        let bootstrapResult = try await wallet.bootstrap()
        let did = bootstrapResult.did

        let offer = try await EudiTestBackend.shared.generateOffer()
        let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
        let credentialIDs = try await wallet.receive(offer: offerURL)
        XCTAssertFalse(credentialIDs.isEmpty, "Should receive at least one credential")

        let credentials = try await wallet.credentials()
        XCTAssertFalse(credentials.isEmpty, "Should have stored credentials")

        let credentialId = await EudiTestBackend.shared.extractCredentialIdFromOfferUrl(offerUrl: offer.offerUrl)
        let transaction = try await EudiTestBackend.shared.createVerifierTransaction(credentialId: credentialId)
        let presentationURL = try XCTUnwrap(URL(string: transaction.authorizationRequestUri))

        let presentResult = try await wallet.present(
            request: presentationURL,
            did: did
        )

        XCTAssertTrue(
            presentResult.success,
            "Presentation should succeed. Credentials: \(credentials), Result: \(presentResult)"
        )

        // Wait for verifier to confirm receipt
        try await TestHelpers.waitForVerifierSuccess(transactionID: transaction.transactionId, timeoutSeconds: verifierPollingTimeout)
    }

    func testCredentialPersistsAcrossControllerRecreation() async throws {
        let wallet1 = try await makeWallet()

        let bootstrapResult = try await wallet1.bootstrap()
        let did = bootstrapResult.did

        let offer = try await EudiTestBackend.shared.generateOffer()
        let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
        let credentialIDs = try await wallet1.receive(offer: offerURL)
        XCTAssertFalse(credentialIDs.isEmpty, "Should receive at least one credential")

        // Recreate wallet facade (simulates app restart)
        let wallet2 = try await makeWallet()

        _ = try await wallet2.bootstrap()
        let credentials = try await wallet2.credentials()
        XCTAssertFalse(credentials.isEmpty, "Credentials should persist across controller recreation")

        let credentialId = await EudiTestBackend.shared.extractCredentialIdFromOfferUrl(offerUrl: offer.offerUrl)
        let transaction = try await EudiTestBackend.shared.createVerifierTransaction(credentialId: credentialId)
        let presentationURL = try XCTUnwrap(URL(string: transaction.authorizationRequestUri))

        let presentResult = try await wallet2.present(
            request: presentationURL,
            did: did
        )

        XCTAssertTrue(
            presentResult.success,
            "Should present from persisted credentials. Credentials: \(credentials), Result: \(presentResult)"
        )

        try await TestHelpers.waitForVerifierSuccess(transactionID: transaction.transactionId, timeoutSeconds: verifierPollingTimeout)
    }
}
