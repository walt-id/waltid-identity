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
        let bootstrapResult = try await wallet1.bootstrap()
        let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
        let credentialIDs = try await wallet1.receive(offer: offerURL, txCode: offer.txCode)
        XCTAssertFalse(credentialIDs.isEmpty, "Should receive \(selectedScenario.displayName)")

        let wallet2 = try await makeWallet(walletId: walletId, attestation: offer.attestation)
        _ = try await wallet2.bootstrap()
        let credentials = try await wallet2.credentials()
        XCTAssertFalse(credentials.isEmpty, "Enterprise credential should persist across controller recreation")

        let session = try await fixture.createVerifierSession(scenario: selectedScenario, platform: .ios)
        let presentationURL = try XCTUnwrap(URL(string: session.authorizationRequestUri))
        let presentResult = try await wallet2.present(request: presentationURL, did: bootstrapResult.did)
        XCTAssertTrue(
            presentResult.success,
            "Should present persisted Enterprise credential for \(selectedScenario.displayName). Credentials: \(credentials), Result: \(presentResult)"
        )
        try await fixture.waitForVerifierSuccess(sessionID: session.sessionID, timeoutSeconds: verifierPollingTimeout)
    }

    private func makeFixture() -> EnterpriseMobileFixture {
        EnterpriseMobileFixture(baseURL: fixtureBaseURL)
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
                }
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
        _ = try await wallet.bootstrap()

        let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
        let credentialIDs = try await wallet.receive(offer: offerURL, txCode: offer.txCode)

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
        let bootstrapResult = try await wallet.bootstrap()

        let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
        let credentialIDs = try await wallet.receive(offer: offerURL, txCode: offer.txCode)
        XCTAssertFalse(credentialIDs.isEmpty, "Should receive \(scenario.displayName)")

        let credentials = try await wallet.credentials()
        XCTAssertFalse(credentials.isEmpty, "Should have stored \(scenario.displayName) credentials")

        let session = try await fixture.createVerifierSession(scenario: scenario, platform: .ios)
        let presentationURL = try XCTUnwrap(URL(string: session.authorizationRequestUri))
        let presentResult = try await wallet.present(request: presentationURL, did: bootstrapResult.did)
        XCTAssertTrue(
            presentResult.success,
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
