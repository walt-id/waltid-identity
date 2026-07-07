import XCTest
@testable import iosApp
import TestHelpers
import WalletSDK

final class EnterpriseMobileWalletIntegrationTests: XCTestCase {

    private let verifierPollingTimeout: TimeInterval = 90

    func testReceiveCredentialsFromEnterpriseIssuer2() async throws {
        let fixture = try makeFixture()
        for scenario in try await fixture.scenarios() {
            let offer = try await fixture.createOffer(scenario: scenario, platform: .ios)
            let walletId = "ios-enterprise-receive-\(scenario.id)-\(UUID().uuidString)"
            await clearTestData(walletId: walletId)

            let wallet = try await makeWallet(walletId: walletId, attestation: offer.attestation)
            _ = try await wallet.bootstrap()

            let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
            let credentialIDs = try await wallet.receive(offer: offerURL, txCode: offer.txCode)

            XCTAssertFalse(credentialIDs.isEmpty, "Should receive \(scenario.displayName) from Enterprise issuer2")
        }
    }

    func testReceiveAndPresentEnterpriseIssuer2Verifier2Flow() async throws {
        let fixture = try makeFixture()
        let scenarios = try await fixture.scenarios()
        for scenario in scenarios {
            guard scenario.supportsPresentation else { continue }
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
    }

    func testEnterpriseCredentialPersistsAcrossControllerRecreation() async throws {
        let fixture = try makeFixture()
        let scenarios = try await fixture.scenarios()
        let scenario = scenarios.first { $0.supportsPresentation && !$0.requiresClientAttestation }
        let selectedScenario = try XCTUnwrap(scenario)
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

    private func makeFixture() throws -> EnterpriseMobileFixture {
        guard let value = ProcessInfo.processInfo.environment["ENTERPRISE_MOBILE_FIXTURE_BASE_URL"],
              let url = URL(string: value), !value.isEmpty else {
            throw XCTSkip("Set ENTERPRISE_MOBILE_FIXTURE_BASE_URL to run Enterprise mobile integration tests")
        }
        return EnterpriseMobileFixture(baseURL: url)
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
}
