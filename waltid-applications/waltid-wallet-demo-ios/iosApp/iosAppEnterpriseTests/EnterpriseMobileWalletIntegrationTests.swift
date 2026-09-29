import XCTest
@testable import iosApp
import TestHelpers
import WalletSDK

final class EnterpriseMobileWalletIntegrationTests: XCTestCase {

    private let verifierPollingTimeout: TimeInterval = 90
    private let fixtureBaseURL = URL(
        string: ProcessInfo.processInfo.environment["ENTERPRISE_MOBILE_FIXTURE_BASE_URL"] ?? "http://localhost:33335"
    )!

    func testReceiveEnterpriseMdlFromEnterpriseIssuer2() async throws {
        try await receiveCredentialFromEnterpriseIssuer2(scenarioID: "enterprise-mdl")
    }

    func testReceiveEnterpriseMdlWithClientAttestationFromEnterpriseIssuer2() async throws {
        try await receiveCredentialFromEnterpriseIssuer2(scenarioID: "enterprise-mdl-client-attestation")
    }

    func testReceiveAndPresentEnterpriseMdlIssuer2Verifier2Flow() async throws {
        try await receiveAndPresentEnterpriseCredential(scenarioID: "enterprise-mdl")
    }

    func testReceiveAndPresentEnterpriseMdlWithClientAttestationIssuer2Verifier2Flow() async throws {
        try await receiveAndPresentEnterpriseCredential(scenarioID: "enterprise-mdl-client-attestation")
    }

    func testEnterpriseCredentialPersistsAcrossControllerRecreation() async throws {
        let fixture = makeFixture()
        let selectedScenario = try await enterpriseScenario(fixture: fixture, scenarioID: "enterprise-mdl")
        let offer = try await fixture.createOffer(scenario: selectedScenario, platform: .ios)
        let walletId = "ios-enterprise-persist-\(selectedScenario.id)-\(UUID().uuidString)"
        await clearTestData(walletId: walletId)

        let wallet1 = try await makeWallet(walletId: walletId, attestation: offer.attestation)
        let bootstrapResult = try await initializeSigningIdentity(wallet1)
        let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
        let credentialIDs = try await receiveCredential(wallet: wallet1, offerURL: offerURL, transactionCode: offer.txCode)
        XCTAssertFalse(credentialIDs.isEmpty, "Should receive \(selectedScenario.displayName)")

        let wallet2 = try await makeWallet(walletId: walletId, attestation: offer.attestation)
        _ = try await initializeSigningIdentity(wallet2)
        let credentials = try await wallet2.credentials()
        XCTAssertFalse(credentials.isEmpty, "Enterprise credential should persist across controller recreation")

        let session = try await fixture.createVerifierSession(scenario: selectedScenario, platform: .ios)
        let presentationURL = try XCTUnwrap(URL(string: session.authorizationRequestUri))
        let presentResult = try await wallet2.present(request: presentationURL, did: bootstrapResult.did)
        assertTransmittedSuccess(
            presentResult,
            "Should present persisted Enterprise credential for \(selectedScenario.displayName). Credentials: \(credentials), Result: \(presentResult)"
        )
        try await fixture.waitForVerifierSuccess(sessionID: session.sessionID, timeoutSeconds: verifierPollingTimeout)
    }

    func testBatchHolderBindingsPresentEachCopyAfterWalletRecreation() async throws {
        let fixture = makeFixture()
        let scenario = try await enterpriseScenario(fixture: fixture, scenarioID: "enterprise-mdl")
        let offer = try await fixture.createOffer(scenario: scenario, platform: .ios)
        let walletID = "ios-enterprise-batch-\(UUID().uuidString)"
        let wallet = try await makeWallet(walletId: walletID, attestation: nil)
        let identity = try await initializeSigningIdentity(wallet)
        let session = try await wallet.startIssuance(
            IssuanceRequest(offer: try XCTUnwrap(URL(string: offer.offerUrl)), redirectURI: URL(string: "openid://")!)
        )
        let holders = try await wallet.createIssuanceHolderKeys(count: 2)
        XCTAssertEqual(Set(holders.map(\.keyID)).count, 2)
        XCTAssertFalse(holders.contains { $0.keyID == identity.keyID })
        let outcome = try await wallet.continuePreAuthorizedIssuance(
            sessionID: session.id,
            credentials: [.init(configurationID: try XCTUnwrap(session.offer.credentials.first).configurationID,
                                holderBindings: holders)]
        )
        guard case let .stored(_, credentialIDs) = outcome else {
            return XCTFail("Expected two stored credentials, got \(outcome)")
        }
        XCTAssertEqual(Set(credentialIDs).count, 2)

        let reopened = try await makeWallet(walletId: walletID, attestation: nil)
        _ = try await initializeSigningIdentity(reopened)
        let stored = try await reopened.credentials()
        XCTAssertEqual(Set(stored.map(\.id)), Set(credentialIDs))
        for credentialID in credentialIDs {
            let verification = try await fixture.createVerifierSession(scenario: scenario, platform: .ios)
            let previewResult = try await reopened.previewPresentation(
                request: try XCTUnwrap(URL(string: verification.authorizationRequestUri))
            )
            guard case let .ready(preview) = previewResult else {
                return XCTFail("Expected a presentation preview")
            }
            let option = try XCTUnwrap(preview.credentialOptions.first { $0.credentialID == credentialID })
            // No holder-key override: the reopened wallet must use each credential's persisted binding.
            let result = try await reopened.submitPresentation(
                previewHandle: preview.previewHandle, selectedCredentialOptions: [option.selection]
            )
            assertTransmittedSuccess(result, "Presentation failed for a stored batch copy")
            try await fixture.waitForVerifierSuccess(sessionID: verification.sessionID, timeoutSeconds: verifierPollingTimeout)
        }
    }

    private func makeFixture() -> EnterpriseMobileFixture {
        EnterpriseMobileFixture(baseURL: fixtureBaseURL)
    }

    /// Interactive physical-device check: cancel the second holder's system signing prompt.
    func testBatchSigningCancellationStoresNoCredential() async throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["WALLET_BATCH_SIGNING_CANCELLATION"] == "1",
                          "Requires a physical device and an operator cancelling the signing prompt")
        let fixture = makeFixture()
        let scenario = try await enterpriseScenario(fixture: fixture, scenarioID: "enterprise-mdl")
        let offer = try await fixture.createOffer(scenario: scenario, platform: .ios)
        let walletID = "ios-enterprise-batch-cancel-\(UUID().uuidString)"
        let wallet = try await makeWallet(walletId: walletID, attestation: nil)
        _ = try await initializeSigningIdentity(wallet)
        let session = try await wallet.startIssuance(
            IssuanceRequest(offer: try XCTUnwrap(URL(string: offer.offerUrl)), redirectURI: URL(string: "openid://")!)
        )
        let first = try await wallet.createIssuanceHolderKeys(count: 1, policy: .none)
        let second = try await wallet.createIssuanceHolderKeys(count: 1, policy: .deviceCredential(timeoutSeconds: 0))
        let outcome = try await wallet.continuePreAuthorizedIssuance(
            sessionID: session.id,
            credentials: [.init(configurationID: try XCTUnwrap(session.offer.credentials.first).configurationID,
                                holderBindings: first + second)]
        )
        guard case let .failed(_, failure, storedIDs, deferred) = outcome else {
            return XCTFail("Expected a signing failure after operator cancellation, got \(outcome)")
        }
        XCTAssertEqual(failure.code, .crypto)
        XCTAssertTrue(storedIDs.isEmpty)
        XCTAssertTrue(deferred.isEmpty)
        let reopened = try await makeWallet(walletId: walletID, attestation: nil)
        let stored = try await reopened.credentials()
        XCTAssertTrue(stored.isEmpty, "Cancelling one proof must not store a partial copy collection")
    }

    private func receiveCredential(
        wallet: Wallet,
        offerURL: URL,
        transactionCode: String?
    ) async throws -> [String] {
        let session = try await wallet.startIssuance(
            IssuanceRequest(offer: offerURL, redirectURI: URL(string: "openid://")!)
        )
        let outcome = try await wallet.continuePreAuthorizedIssuance(
            sessionID: session.id,
            transactionCode: transactionCode
        )
        guard case let .stored(_, credentialIDs) = outcome else {
            if case let .failed(_, failure, _, _) = outcome {
                XCTFail("Issuance failed [\(failure.code)]: \(failure.message)")
            }
            throw EnterpriseIssuanceError.unexpectedOutcome
        }
        return credentialIDs
    }

    private func makeWallet(
        walletId: String,
        attestation: EnterpriseMobileAttestation?
    ) async throws -> Wallet {
        try await Wallet(
            configuration: WalletConfiguration(
                walletID: walletId,
                attestation: attestation.map {
                    WalletAttestationConfiguration(
                        baseURL: $0.baseUrl,
                        attesterPath: $0.attesterPath,
                        bearerToken: $0.bearerToken,
                        hostHeader: $0.hostHeader
                    )
                },
                defaultKeyUseAuthorizationPolicy: .none
            )
        )
    }

    private func clearTestData(walletId: String) async {
        let fileManager = FileManager.default
        if let appSupport = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
            let databaseDirectories = [
                appSupport,
                appSupport.appendingPathComponent("databases", isDirectory: true),
            ]
            let dbFiles = [
                "wallet_\(walletId).db",
                "wallet_\(walletId).db-shm",
                "wallet_\(walletId).db-wal",
            ]
            for directory in databaseDirectories {
                for dbFile in dbFiles {
                    try? fileManager.removeItem(at: directory.appendingPathComponent(dbFile))
                }
            }
        }
    }

    private func receiveCredentialFromEnterpriseIssuer2(scenarioID: String) async throws {
        let fixture = makeFixture()
        let scenario = try await enterpriseScenario(fixture: fixture, scenarioID: scenarioID)
        let offer = try await fixture.createOffer(scenario: scenario, platform: .ios)
        let walletId = "ios-enterprise-receive-\(scenario.id)-\(UUID().uuidString)"
        await clearTestData(walletId: walletId)

        let wallet = try await makeWallet(walletId: walletId, attestation: offer.attestation)
        _ = try await initializeSigningIdentity(wallet)

        let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
        let credentialIDs = try await receiveCredential(wallet: wallet, offerURL: offerURL, transactionCode: offer.txCode)

        XCTAssertFalse(credentialIDs.isEmpty, "Should receive \(scenario.displayName) from Enterprise issuer2")
    }

    private func receiveAndPresentEnterpriseCredential(scenarioID: String) async throws {
        let fixture = makeFixture()
        let scenario = try await enterpriseScenario(fixture: fixture, scenarioID: scenarioID)
        XCTAssertTrue(scenario.supportsPresentation, "\(scenario.displayName) should support presentation")

        let offer = try await fixture.createOffer(scenario: scenario, platform: .ios)
        let walletId = "ios-enterprise-present-\(scenario.id)-\(UUID().uuidString)"
        await clearTestData(walletId: walletId)

        let wallet = try await makeWallet(walletId: walletId, attestation: offer.attestation)
        let bootstrapResult = try await initializeSigningIdentity(wallet)

        let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
        let credentialIDs = try await receiveCredential(wallet: wallet, offerURL: offerURL, transactionCode: offer.txCode)
        XCTAssertFalse(credentialIDs.isEmpty, "Should receive \(scenario.displayName)")

        let credentials = try await wallet.credentials()
        XCTAssertFalse(credentials.isEmpty, "Should have stored \(scenario.displayName) credentials")

        let session = try await fixture.createVerifierSession(scenario: scenario, platform: .ios)
        let presentationURL = try XCTUnwrap(URL(string: session.authorizationRequestUri))
        let presentResult = try await wallet.present(request: presentationURL, did: bootstrapResult.did)
        assertTransmittedSuccess(
            presentResult,
            "Enterprise verifier2 presentation should succeed for \(scenario.displayName). Credentials: \(credentials), Result: \(presentResult)"
        )

        try await fixture.waitForVerifierSuccess(
            sessionID: session.sessionID,
            timeoutSeconds: verifierPollingTimeout
        )
    }

    private func enterpriseScenario(
        fixture: EnterpriseMobileFixture,
        scenarioID: String
    ) async throws -> EnterpriseMobileScenario {
        let scenarios = try await fixture.scenarios()
        return try XCTUnwrap(scenarios.first { $0.id == scenarioID })
    }
}

private enum EnterpriseIssuanceError: Error {
    case unexpectedOutcome
}

private func assertTransmittedSuccess(
    _ result: PresentationResult,
    _ message: @autoclosure () -> String,
    file: StaticString = #filePath,
    line: UInt = #line
) {
    guard case .transmitted(.succeeded) = result else {
        XCTFail(message(), file: file, line: line)
        return
    }
}

private func initializeSigningIdentity(_ wallet: Wallet) async throws -> SigningIdentity {
    guard case .active(let identity) = try await wallet.signingIdentity.initialize() else {
        throw WalletError.invalidInput("Expected an active test identity")
    }
    return identity
}
