import XCTest
@testable import iosApp
import shared
import TestHelpers

/// iOS integration tests for the wallet-client library.
///
/// Tests the WalletDemoBridgeController (iOS bridge to the KMP wallet-client).
/// Uses real iOS Keychain crypto, SQLDelight persistence, and OID4VCI/VP protocol
/// against the public EUDI test backend.
///
/// These are integration tests (not E2E UI tests) - they test the library directly
/// without UI automation. Run on every PR for fast feedback.
final class WalletClientIntegrationTests: XCTestCase {

    private let testWalletId = "ios-unit-test-wallet"

    // Timeouts (aligned with Android for cross-platform consistency)
    private let verifierPollingTimeout: TimeInterval = 30  // 30 sec - backend verification

    // MARK: - Tests (mirror Android WalletClientDeviceTest.kt)

    func testBootstrapCreatesKeyAndDid() async throws {
        let controller = WalletDemoBridgeController(
            attestationBaseUrl: nil,
            attestationAttesterPath: nil,
            attestationBearerToken: nil,
            attestationHostHeader: nil
        )

        let result = try await controller.bootstrap()

        XCTAssertTrue(result.success, "bootstrap should succeed")
        XCTAssertTrue(result.message.starts(with: "did:"), "DID should start with 'did:', got: \(result.message)")
    }

    func testReceiveCredentialFromEudi() async throws {
        let controller = WalletDemoBridgeController(
            attestationBaseUrl: nil,
            attestationAttesterPath: nil,
            attestationBearerToken: nil,
            attestationHostHeader: nil
        )
        _ = try await controller.bootstrap()

        let offer = try await EudiTestBackend.shared.generateOffer()
        let result = try await controller.receiveCredential(offerUrl: offer.offerUrl)

        XCTAssertTrue(result.success, "Should receive credential: \(result.message)")
        XCTAssertTrue(result.message.contains("Received"), "Message should confirm receipt: \(result.message)")
    }

    func testReceiveAndPresentFullFlow() async throws {
        let controller = WalletDemoBridgeController(
            attestationBaseUrl: nil,
            attestationAttesterPath: nil,
            attestationBearerToken: nil,
            attestationHostHeader: nil
        )

        let bootstrapResult = try await controller.bootstrap()
        XCTAssertTrue(bootstrapResult.success, "Bootstrap should succeed")
        let did = bootstrapResult.message // DID is returned in the message

        let offer = try await EudiTestBackend.shared.generateOffer()
        let receiveResult = try await controller.receiveCredential(offerUrl: offer.offerUrl)
        XCTAssertTrue(receiveResult.success, "Should receive credential: \(receiveResult.message)")

        let credentials = try await controller.listCredentials()
        XCTAssertFalse(credentials.isEmpty, "Should have stored credentials")

        let credentialId = await EudiTestBackend.shared.extractCredentialIdFromOfferUrl(offerUrl: offer.offerUrl)
        let transaction = try await EudiTestBackend.shared.createVerifierTransaction(credentialId: credentialId)

        let presentResult = try await controller.presentCredential(
            requestUrl: transaction.authorizationRequestUri,
            did: did
        )

        XCTAssertTrue(
            presentResult.success,
            "Presentation should succeed. Credentials: \(credentials), Result: \(presentResult.message)"
        )

        // Wait for verifier to confirm receipt
        try await TestHelpers.waitForVerifierSuccess(transactionID: transaction.transactionId, timeoutSeconds: verifierPollingTimeout)
    }

    func testCredentialPersistsAcrossControllerRecreation() async throws {
        let controller1 = WalletDemoBridgeController(
            attestationBaseUrl: nil,
            attestationAttesterPath: nil,
            attestationBearerToken: nil,
            attestationHostHeader: nil
        )

        let bootstrapResult = try await controller1.bootstrap()
        XCTAssertTrue(bootstrapResult.success, "Bootstrap should succeed")
        let did = bootstrapResult.message

        let offer = try await EudiTestBackend.shared.generateOffer()
        let receiveResult = try await controller1.receiveCredential(offerUrl: offer.offerUrl)
        XCTAssertTrue(receiveResult.success, "Should receive credential: \(receiveResult.message)")

        // Recreate controller (simulates app restart)
        let controller2 = WalletDemoBridgeController(
            attestationBaseUrl: nil,
            attestationAttesterPath: nil,
            attestationBearerToken: nil,
            attestationHostHeader: nil
        )

        _ = try await controller2.bootstrap()
        let credentials = try await controller2.listCredentials()
        XCTAssertFalse(credentials.isEmpty, "Credentials should persist across controller recreation")

        let credentialId = await EudiTestBackend.shared.extractCredentialIdFromOfferUrl(offerUrl: offer.offerUrl)
        let transaction = try await EudiTestBackend.shared.createVerifierTransaction(credentialId: credentialId)

        // Pass the did parameter
        let presentResult = try await controller2.presentCredential(
            requestUrl: transaction.authorizationRequestUri,
            did: did
        )

        XCTAssertTrue(
            presentResult.success,
            "Should present from persisted credentials. Credentials: \(credentials), Result: \(presentResult.message)"
        )

        try await TestHelpers.waitForVerifierSuccess(transactionID: transaction.transactionId, timeoutSeconds: verifierPollingTimeout)
    }
}
