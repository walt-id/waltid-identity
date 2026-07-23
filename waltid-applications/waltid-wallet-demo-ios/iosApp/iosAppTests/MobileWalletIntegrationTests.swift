import XCTest
@testable import iosApp
import TestHelpers
import WalletSDK

/// iOS integration tests for the mobile wallet library.
///
/// Tests the Swift package facade that iOS apps consume directly.
/// Uses real iOS Keychain crypto, SQLDelight persistence, and OID4VCI/VP protocol
/// against public walt.id demo and EUDI test backends.
///
/// These are integration tests (not E2E UI tests) - they test the library directly
/// without UI automation.
final class MobileWalletIntegrationTests: XCTestCase {

    private let testWalletId = "ios-unit-test-wallet"
    private static let eudiPidSdJwtCredentialID = "eu.europa.ec.eudi.pid_vc_sd_jwt"
    private static let eudiEhicSdJwtCredentialID = "eu.europa.ec.eudi.ehic_sd_jwt_vc"
    private static let demoTransactionDataProfiles: [WalletTransactionDataProfile] = [
        WalletTransactionDataProfile(
            type: "org.waltid.transaction-data.payment-authorization",
            displayName: "Payment Authorization",
            fields: ["amount", "currency", "payee"]
        ),
        WalletTransactionDataProfile(
            type: "org.waltid.transaction-data.account-access",
            displayName: "Account Access",
            fields: ["account_identifier", "access_scope"]
        )
    ]

    // Timeouts (aligned with Android for cross-platform consistency)
    private let verifierPollingTimeout: TimeInterval = 30  // 30 sec - backend verification

    // MARK: - Test Lifecycle

    override func setUp() async throws {
        try await super.setUp()

        // Clean up test state before each test to ensure isolation
        // This prevents flakiness from state bleed between tests
        await clearTestData(walletId: testWalletId)
    }

    /// Clears all test data (database only) to ensure test isolation
    private func clearTestData(walletId: String) async {
        let fileManager = FileManager.default
        if let appSupport = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
            let databaseDirectories = [
                appSupport,
                appSupport.appendingPathComponent("databases", isDirectory: true)
            ]
            let dbFiles = [
                "wallet_\(walletId).db",
                "wallet_\(walletId).db-shm",
                "wallet_\(walletId).db-wal"
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

    private func makeWallet(walletId: String? = nil) async throws -> Wallet {
        try await Wallet(
            configuration: WalletConfiguration(
                walletID: walletId ?? testWalletId,
                requestObjectTrustAnchorPEMCertificates: [EudiTestBackend.verifierTrustAnchorPEM],
                transactionDataProfiles: Self.demoTransactionDataProfiles
            )
        )
    }

    private func makeWallet(persistence: WalletPersistence) async throws -> Wallet {
        try await Wallet(
            configuration: WalletConfiguration(
                walletID: testWalletId,
                persistence: persistence,
                requestObjectTrustAnchorPEMCertificates: [EudiTestBackend.verifierTrustAnchorPEM],
                transactionDataProfiles: Self.demoTransactionDataProfiles
            )
        )
    }

    // MARK: - Tests (mirror Android MobileWalletIntegrationTest.kt)

    func testBootstrapCreatesKeyAndDid() async throws {
        let wallet = try await makeWallet()

        let result = try await wallet.bootstrap()

        XCTAssertTrue(result.did.starts(with: "did:"), "DID should start with 'did:', got: \(result.did)")
    }

    func testManagedEncryptedWalletBootstrapsAcrossRecreation() async throws {
        let wallet1 = try await makeWallet()

        let first = try await wallet1.bootstrap()
        XCTAssertTrue(first.did.starts(with: "did:"), "DID should start with 'did:', got: \(first.did)")

        let wallet2 = try await makeWallet()
        let second = try await wallet2.bootstrap()

        XCTAssertEqual(second.did, first.did, "Encrypted wallet state should survive wallet facade recreation")
        XCTAssertEqual(second.keyID, first.keyID, "Encrypted wallet key reference should survive wallet facade recreation")
    }

    func testDeleteLocalDataRemovesManagedEncryptedWalletState() async throws {
        let wallet1 = try await makeWallet()
        let first = try await wallet1.bootstrap()

        try await wallet1.deleteLocalData()

        let wallet2 = try await makeWallet()
        let second = try await wallet2.bootstrap()

        XCTAssertNotEqual(second.did, first.did, "Deleting local data should remove the persisted DID state")
        XCTAssertNotEqual(second.keyID, first.keyID, "Deleting local data should remove the persisted platform key reference")
    }

    func testCustomCredentialStoreRetainsPlatformSigningKeys() async throws {
        let store = RecordingWalletCredentialStore()
        let persistence = WalletPersistence(
            stores: WalletStores(credentials: store)
        )
        let wallet = try await makeWallet(persistence: persistence)

        let bootstrap = try await wallet.bootstrap()
        let credentials = try await wallet.credentials()
        let reopenedWallet = try await makeWallet(persistence: persistence)
        let reopenedBootstrap = try await reopenedWallet.bootstrap()
        let reopenedCredentials = try await reopenedWallet.credentials()
        let listCredentialsCalls = await store.listCredentialsCalls

        XCTAssertTrue(bootstrap.did.starts(with: "did:"), "DID should start with 'did:', got: \(bootstrap.did)")
        XCTAssertTrue(credentials.isEmpty)
        XCTAssertEqual(reopenedBootstrap.did, bootstrap.did, "Default DID store should survive wallet facade recreation")
        XCTAssertEqual(reopenedBootstrap.keyID, bootstrap.keyID, "Platform signing-key reference should survive wallet facade recreation")
        XCTAssertTrue(reopenedCredentials.isEmpty)
        XCTAssertEqual(listCredentialsCalls, 2)

        try await wallet.deleteLocalData()
    }

    func testProvidedDatabaseKeyProviderBootstrapsAcrossRecreationAndDeletesProviderKey() async throws {
        let provider = RecordingWalletDatabaseKeyProvider()
        let persistence = WalletPersistence(databaseKey: .provided(provider))
        let wallet1 = try await makeWallet(persistence: persistence)

        let first = try await wallet1.bootstrap()

        let wallet2 = try await makeWallet(persistence: persistence)
        let second = try await wallet2.bootstrap()
        let requestedKeys = await provider.requestedKeys

        XCTAssertEqual(second.did, first.did, "Provided database key should reopen encrypted wallet state")
        XCTAssertEqual(second.keyID, first.keyID, "Provided database key should preserve platform key references")
        XCTAssertEqual(
            requestedKeys,
            [
                "\(testWalletId):wallet_\(testWalletId)",
                "\(testWalletId):wallet_\(testWalletId)"
            ]
        )

        try await wallet2.deleteLocalData()
        let deletedKeys = await provider.deletedKeys

        XCTAssertEqual(deletedKeys, ["\(testWalletId):wallet_\(testWalletId)"])
    }

    func testReceiveEudiPidSdJwtFromEudi() async throws {
        let wallet = try await makeWallet()
        _ = try await wallet.bootstrap()

        let offer = try await EudiTestBackend.shared.generateOffer(credentialId: Self.eudiPidSdJwtCredentialID)
        let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
        let resolution = try await wallet.resolveOffer(offer: offerURL)
        XCTAssertFalse(resolution.issuer.credentialIssuer.isEmpty)
        XCTAssertFalse(resolution.offeredCredentials.isEmpty)
        XCTAssertTrue(resolution.offeredCredentials.allSatisfy {
            !$0.configurationID.isEmpty && !$0.format.isEmpty
        })
        XCTAssertNotNil(resolution.transactionCode, "EUDI offer should require a transaction code")
        let credentialIDs = try await wallet.receive(offer: offerURL, txCode: offer.txCode)

        XCTAssertFalse(credentialIDs.isEmpty, "Should receive at least one credential")
    }

    func testReceiveEudiPidSdJwtCredentialFromDemoIssuer2() async throws {
        try await receiveCredentialFromDemoIssuer2(scenarioID: "eudi-pid-sdjwt")
    }

    func testReceiveEudiPidMdocCredentialFromDemoIssuer2() async throws {
        try await receiveCredentialFromDemoIssuer2(scenarioID: "eudi-pid-mdoc")
    }

    func testReceiveIsoMdlCredentialFromDemoIssuer2() async throws {
        try await receiveCredentialFromDemoIssuer2(scenarioID: "iso-mdl")
    }

    func testReceiveAndPresentEudiEhicSdJwtAgainstEudi() async throws {
        try XCTSkipIf(
            true,
            "Pending native iOS PKIX/x509_hash Request Object authentication and EUDI trust-anchor refresh; tracked by https://github.com/walt-id/waltid-identity/pull/1940"
        )
        try await receiveAndPresentEudiCredential(credentialID: Self.eudiEhicSdJwtCredentialID)
    }

    func testPreviewAndSubmitEudiEhicSdJwtAgainstEudi() async throws {
        try XCTSkipIf(
            true,
            "Pending native iOS PKIX/x509_hash Request Object authentication and EUDI trust-anchor refresh; tracked by https://github.com/walt-id/waltid-identity/pull/1940"
        )
        try await previewAndSubmitEudiCredential(credentialID: Self.eudiEhicSdJwtCredentialID)
    }

    func testReceiveAndPresentEudiPidSdJwtAgainstEudi() async throws {
        try XCTSkipIf(
            true,
            "Upstream issue: https://github.com/eu-digital-identity-wallet/eudi-srv-web-issuing-eudiw-py/issues/172"
        )
        try await receiveAndPresentEudiCredential(credentialID: Self.eudiPidSdJwtCredentialID)
    }

    func testPreviewAndSubmitEudiPidSdJwtAgainstEudi() async throws {
        try XCTSkipIf(
            true,
            "Upstream issue: https://github.com/eu-digital-identity-wallet/eudi-srv-web-issuing-eudiw-py/issues/172"
        )
        try await previewAndSubmitEudiCredential(credentialID: Self.eudiPidSdJwtCredentialID)
    }

    func testPreviewAndSubmitEncryptedEudiPidMdocAgainstDemoIssuer2AndVerifier2() async throws {
        let scenario = try demoScenario("eudi-pid-mdoc")
        let wallet = try await makeWallet(walletId: "ios-demo-encrypted-mdoc-\(UUID().uuidString)")
        let bootstrapResult = try await wallet.bootstrap()
        let offer = try await DemoBackend.shared.createOffer(scenario: scenario)
        let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
        let credentialIDs = try await wallet.receive(offer: offerURL, txCode: offer.txCode)
        XCTAssertFalse(credentialIDs.isEmpty, "Should receive a demo EUDI PID mdoc")

        let session = try await DemoBackend.shared.createVerifierSession(
            scenario: scenario,
            encryptedResponse: true
        )
        let presentationURL = try XCTUnwrap(URL(string: session.authorizationRequestUri))
        let previewResult = try await wallet.previewPresentation(request: presentationURL)
        let preview = try requireReadyPreview(previewResult)
        XCTAssertEqual(preview.request.responseMode, "direct_post.jwt")
        guard case .required = preview.encryption else {
            return XCTFail("Expected encrypted response metadata: \(preview.encryption)")
        }
        XCTAssertFalse(preview.credentialOptions.isEmpty)
        XCTAssertTrue(preview.credentialOptions.allSatisfy { $0.format == "mso_mdoc" })

        let result = try await wallet.submitPresentation(
            request: presentationURL,
            selectedCredentialOptions: preview.credentialOptions.map(\.selection),
            did: bootstrapResult.did
        )
        assertTransmittedSuccess(result, "Encrypted demo EUDI PID mdoc presentation should succeed: \(result)")
        try await DemoBackend.shared.waitForVerifierSuccess(
            sessionID: session.sessionID,
            timeoutSeconds: verifierPollingTimeout
        )
    }

    func testReceiveAndPresentEudiPidSdJwtAgainstDemoIssuer2AndVerifier2() async throws {
        try await receiveAndPresentDemoCredential(scenarioID: "eudi-pid-sdjwt")
    }

    func testReceiveAndPresentEudiPidMdocAgainstDemoIssuer2AndVerifier2() async throws {
        try await receiveAndPresentDemoCredential(scenarioID: "eudi-pid-mdoc")
    }

    func testPreviewAndSubmitEudiPidSdJwtAgainstDemoIssuer2AndVerifier2() async throws {
        try await previewAndSubmitDemoCredential(scenarioID: "eudi-pid-sdjwt")
    }

    func testPreviewAndSubmitEudiPidMdocAgainstDemoIssuer2AndVerifier2() async throws {
        try await previewAndSubmitDemoCredential(scenarioID: "eudi-pid-mdoc")
    }

    func testRejectPresentationAgainstDemoVerifier2() async throws {
        let scenario = try demoPresentationScenario("eudi-pid-sdjwt")
        let walletId = "ios-demo-reject-\(scenario.id)-\(UUID().uuidString)"
        await clearTestData(walletId: walletId)

        let wallet = try await makeWallet(walletId: walletId)
        _ = try await wallet.bootstrap()
        let session = try await DemoBackend.shared.createResponseBoundVerifierSession(scenario: scenario)
        let presentationURL = try XCTUnwrap(URL(string: session.authorizationRequestUri))
        let previewResult = try await wallet.previewPresentation(request: presentationURL)
        let previewHandle: PresentationPreviewHandle
        switch previewResult {
        case .ready(let preview):
            previewHandle = preview.previewHandle
        case .invalid(let error):
            previewHandle = error.previewHandle
        }
        let result = try await wallet.rejectPresentation(previewHandle: previewHandle)

        assertTransmittedSuccess(result, "Wallet should deliver access_denied to public demo verifier2: \(result)")
        let info = try await DemoBackend.shared.waitForVerifierFailure(
            sessionID: session.sessionID,
            expectedError: "access_denied",
            timeoutSeconds: verifierPollingTimeout
        )
        let failure = try XCTUnwrap(info["failure"] as? [String: Any])
        XCTAssertEqual(failure["type"] as? String, "wallet_error_response")
    }

    func testInvalidTransactionDataCanBeReviewedAndReportedWithoutBackendSupport() async throws {
        let walletId = "ios-invalid-transaction-data-\(UUID().uuidString)"
        await clearTestData(walletId: walletId)
        let wallet = try await makeWallet(walletId: walletId)
        _ = try await wallet.bootstrap()
        let presentationURL = try invalidTransactionDataPresentationURL()

        let previewResult = try await wallet.previewPresentation(request: presentationURL)
        guard case .invalid(let error) = previewResult else {
            return XCTFail("Expected invalid_transaction_data, got \(previewResult)")
        }

        XCTAssertEqual(error.code, .invalidTransactionData)
        XCTAssertEqual(error.request.clientID, "redirect_uri:https://verifier.example/callback")

        let result = try await wallet.rejectPresentation(previewHandle: error.previewHandle)
        XCTAssertEqual(
            result,
            .prepared(.openURL(URL(string: "https://verifier.example/callback#error=invalid_transaction_data&state=state-123")!))
        )
    }

    func testReceiveAndPresentIsoMdlAgainstDemoIssuer2AndVerifier2() async throws {
        try await receiveAndPresentDemoCredential(scenarioID: "iso-mdl")
    }

    func testEudiPidSdJwtPersistsAcrossControllerRecreation() async throws {
        let walletId = "ios-eudi-pid-sd-jwt-persistence-\(UUID().uuidString)"
        await clearTestData(walletId: walletId)
        let wallet1 = try await makeWallet(walletId: walletId)
        _ = try await wallet1.bootstrap()

        let offer = try await EudiTestBackend.shared.generateOffer(credentialId: Self.eudiPidSdJwtCredentialID)
        let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
        let credentialIDs = try await wallet1.receive(offer: offerURL, txCode: offer.txCode)
        XCTAssertFalse(credentialIDs.isEmpty, "Should receive at least one credential")

        // Recreate wallet facade (simulates app restart)
        let wallet2 = try await makeWallet(walletId: walletId)

        _ = try await wallet2.bootstrap()
        let credentials = try await wallet2.credentials()
        XCTAssertFalse(credentials.isEmpty, "Credentials should persist across controller recreation")
    }

    func testDemoCredentialPersistsAcrossControllerRecreation() async throws {
        let scenario = DemoBackend.persistenceScenario
        let walletId = "ios-demo-persist-\(scenario.id)-\(UUID().uuidString)"
        await clearTestData(walletId: walletId)

        let wallet1 = try await makeWallet(walletId: walletId)
        let bootstrapResult = try await wallet1.bootstrap()
        let did = bootstrapResult.did

        let offer = try await DemoBackend.shared.createOffer(scenario: scenario)
        let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
        let credentialIDs = try await wallet1.receive(offer: offerURL)
        XCTAssertFalse(
            credentialIDs.isEmpty,
            "Should receive \(scenario.displayName) from public demo issuer2"
        )

        let wallet2 = try await makeWallet(walletId: walletId)
        _ = try await wallet2.bootstrap()
        let credentials = try await wallet2.credentials()
        XCTAssertFalse(credentials.isEmpty, "public demo credential should persist across controller recreation")
        try assertStoredCredentialDisplayData(scenario: scenario, credentials: credentials)

        let session = try await DemoBackend.shared.createVerifierSession(scenario: scenario)
        let presentationURL = try XCTUnwrap(URL(string: session.authorizationRequestUri))
        let presentResult = try await wallet2.present(
            request: presentationURL,
            did: did
        )

        assertTransmittedSuccess(
            presentResult,
            "Should present persisted public demo credential for \(scenario.displayName). Credentials: \(credentials), Result: \(presentResult)"
        )

        try await DemoBackend.shared.waitForVerifierSuccess(
            sessionID: session.sessionID,
            timeoutSeconds: verifierPollingTimeout
        )
    }

    private func receiveAndPresentEudiCredential(credentialID: String) async throws {
        let walletId = "ios-eudi-present-\(UUID().uuidString)"
        await clearTestData(walletId: walletId)
        let wallet = try await makeWallet(walletId: walletId)
        let bootstrapResult = try await wallet.bootstrap()

        let offer = try await EudiTestBackend.shared.generateOffer(credentialId: credentialID)
        let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
        let credentialIDs = try await wallet.receive(offer: offerURL, txCode: offer.txCode)
        XCTAssertFalse(credentialIDs.isEmpty, "Should receive EUDI credential \(credentialID)")

        let credentials = try await wallet.credentials()
        XCTAssertFalse(credentials.isEmpty, "Should store EUDI credential \(credentialID)")

        let offeredCredentialID = await EudiTestBackend.shared.extractCredentialIdFromOfferUrl(offerUrl: offer.offerUrl)
        let transaction = try await EudiTestBackend.shared.createVerifierTransaction(credentialId: offeredCredentialID)
        let presentationURL = try XCTUnwrap(URL(string: transaction.authorizationRequestUri))
        let result = try await wallet.present(request: presentationURL, did: bootstrapResult.did)
        assertTransmittedSuccess(
            result,
            "EUDI presentation should succeed for \(credentialID). Credentials: \(credentials), Result: \(result)"
        )

        try await TestHelpers.waitForVerifierSuccess(
            transactionID: transaction.transactionId,
            timeoutSeconds: verifierPollingTimeout
        )
    }

    private func previewAndSubmitEudiCredential(credentialID: String) async throws {
        let walletId = "ios-eudi-preview-submit-\(UUID().uuidString)"
        await clearTestData(walletId: walletId)
        let wallet = try await makeWallet(walletId: walletId)
        let bootstrapResult = try await wallet.bootstrap()

        let offer = try await EudiTestBackend.shared.generateOffer(credentialId: credentialID)
        let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
        let credentialIDs = try await wallet.receive(offer: offerURL, txCode: offer.txCode)
        XCTAssertFalse(credentialIDs.isEmpty, "Should receive EUDI credential \(credentialID)")

        let offeredCredentialID = await EudiTestBackend.shared.extractCredentialIdFromOfferUrl(offerUrl: offer.offerUrl)
        let transaction = try await EudiTestBackend.shared.createVerifierTransaction(credentialId: offeredCredentialID)
        let presentationURL = try XCTUnwrap(URL(string: transaction.authorizationRequestUri))
        let previewResult = try await wallet.previewPresentation(request: presentationURL)
        let preview = try requireReadyPreview(previewResult)
        XCTAssertFalse(
            preview.credentialOptions.isEmpty,
            "Should preview a matching EUDI credential for \(credentialID): \(preview)"
        )
        XCTAssertTrue(
            preview.credentialOptions.allSatisfy { credentialIDs.contains($0.credentialID) },
            "Preview should only offer credentials received in this test. Received: \(credentialIDs), Preview: \(preview)"
        )
        guard case .required = preview.request.responseEncryption else {
            return XCTFail("EUDI verifier should request an encrypted response: \(preview)")
        }

        let result = try await wallet.submitPresentation(
            previewHandle: preview.previewHandle,
            selectedCredentialOptions: preview.credentialOptions.map(\.selection),
            did: bootstrapResult.did
        )
        assertTransmittedSuccess(
            result,
            "EUDI stepwise presentation should succeed for \(credentialID). Preview: \(preview), Result: \(result)"
        )

        try await TestHelpers.waitForVerifierSuccess(
            transactionID: transaction.transactionId,
            timeoutSeconds: verifierPollingTimeout
        )
    }

    private func previewAndSubmitDemoCredential(scenarioID: String) async throws {
        let scenario = try demoPresentationScenario(scenarioID)
        let walletId = "ios-demo-preview-submit-\(scenario.id)-\(UUID().uuidString)"
        await clearTestData(walletId: walletId)

        let wallet = try await makeWallet(walletId: walletId)
        let bootstrapResult = try await wallet.bootstrap()
        let did = bootstrapResult.did

        let offer = try await DemoBackend.shared.createOffer(scenario: scenario)
        let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
        let credentialIDs = try await wallet.receive(offer: offerURL)
        XCTAssertFalse(
            credentialIDs.isEmpty,
            "Should receive \(scenario.displayName) from public demo issuer2"
        )

        let session = try await DemoBackend.shared.createVerifierSession(scenario: scenario)
        let presentationURL = try XCTUnwrap(URL(string: session.authorizationRequestUri))
        let previewResult = try await wallet.previewPresentation(request: presentationURL)
        let preview = try requireReadyPreview(previewResult)
        XCTAssertFalse(
            preview.credentialOptions.isEmpty,
            "Should preview at least one matching credential for \(scenario.displayName): \(preview)"
        )
        XCTAssertTrue(
            preview.credentialOptions.allSatisfy { credentialIDs.contains($0.credentialID) },
            "Preview should only offer credentials received in this test. Received: \(credentialIDs), Preview: \(preview)"
        )

        let result = try await wallet.submitPresentation(
            previewHandle: preview.previewHandle,
            selectedCredentialOptions: preview.credentialOptions.map(\.selection),
            did: did
        )

        assertTransmittedSuccess(
            result,
            "public demo verifier2 stepwise presentation should succeed for \(scenario.displayName). Preview: \(preview), Result: \(result)"
        )

        try await DemoBackend.shared.waitForVerifierSuccess(
            sessionID: session.sessionID,
            timeoutSeconds: verifierPollingTimeout
        )
    }

    private func requireReadyPreview(_ result: PresentationPreviewResult) throws -> PresentationPreview {
        switch result {
        case .ready(let preview):
            return preview
        case .invalid(let error):
            XCTFail("Expected a valid presentation preview, got \(error.code.rawValue): \(error.message)")
            throw NSError(
                domain: "MobileWalletIntegrationTests",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: error.message]
            )
        }
    }

    private func invalidTransactionDataPresentationURL() throws -> URL {
        let transactionData = try JSONSerialization.data(withJSONObject: [
            "type": "unsupported",
            "credential_ids": ["pid"]
        ])
        let encodedTransactionData = transactionData.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        let transactionDataParameter = try JSONSerialization.data(withJSONObject: [encodedTransactionData])
        let dcqlQuery = try JSONSerialization.data(withJSONObject: [
            "credentials": [[
                "id": "pid",
                "format": "dc+sd-jwt",
                "meta": [:]
            ]]
        ])
        var components = URLComponents()
        components.scheme = "openid4vp"
        components.host = "authorize"
        components.queryItems = [
            URLQueryItem(name: "client_id", value: "redirect_uri:https://verifier.example/callback"),
            URLQueryItem(name: "response_type", value: "vp_token"),
            URLQueryItem(name: "response_mode", value: "fragment"),
            URLQueryItem(name: "redirect_uri", value: "https://verifier.example/callback"),
            URLQueryItem(name: "client_metadata", value: "{}"),
            URLQueryItem(name: "nonce", value: "nonce"),
            URLQueryItem(name: "state", value: "state-123"),
            URLQueryItem(name: "dcql_query", value: String(decoding: dcqlQuery, as: UTF8.self)),
            URLQueryItem(name: "transaction_data", value: String(decoding: transactionDataParameter, as: UTF8.self))
        ]
        components.percentEncodedQuery = components.percentEncodedQuery?.replacingOccurrences(of: "+", with: "%2B")
        return try XCTUnwrap(components.url)
    }

    private func receiveCredentialFromDemoIssuer2(scenarioID: String) async throws {
        let scenario = try demoScenario(scenarioID)
        let walletId = "ios-demo-receive-\(scenario.id)-\(UUID().uuidString)"
        await clearTestData(walletId: walletId)

        let wallet = try await makeWallet(walletId: walletId)
        _ = try await wallet.bootstrap()

        let offer = try await DemoBackend.shared.createOffer(scenario: scenario)
        let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
        let credentialIDs = try await wallet.receive(offer: offerURL)

        XCTAssertFalse(
            credentialIDs.isEmpty,
            "Should receive \(scenario.displayName) from public demo issuer2"
        )

        let credentials = try await wallet.credentials()
        try assertStoredCredentialDisplayData(scenario: scenario, credentials: credentials)
    }

    private func receiveAndPresentDemoCredential(scenarioID: String) async throws {
        let scenario = try demoPresentationScenario(scenarioID)
        let walletId = "ios-demo-present-\(scenario.id)-\(UUID().uuidString)"
        await clearTestData(walletId: walletId)

        let wallet = try await makeWallet(walletId: walletId)
        let bootstrapResult = try await wallet.bootstrap()
        let did = bootstrapResult.did

        let offer = try await DemoBackend.shared.createOffer(scenario: scenario)
        let offerURL = try XCTUnwrap(URL(string: offer.offerUrl))
        let credentialIDs = try await wallet.receive(offer: offerURL)
        XCTAssertFalse(
            credentialIDs.isEmpty,
            "Should receive \(scenario.displayName) from public demo issuer2"
        )

        let credentials = try await wallet.credentials()
        XCTAssertFalse(credentials.isEmpty, "Should have stored \(scenario.displayName) credentials")
        try assertStoredCredentialDisplayData(scenario: scenario, credentials: credentials)

        let session = try await DemoBackend.shared.createVerifierSession(scenario: scenario)
        let presentationURL = try XCTUnwrap(URL(string: session.authorizationRequestUri))
        let presentResult = try await wallet.present(
            request: presentationURL,
            did: did
        )

        assertTransmittedSuccess(
            presentResult,
            "public demo verifier2 presentation should succeed for \(scenario.displayName). Credentials: \(credentials), Result: \(presentResult)"
        )

        try await DemoBackend.shared.waitForVerifierSuccess(
            sessionID: session.sessionID,
            timeoutSeconds: verifierPollingTimeout
        )
    }

    private func demoScenario(_ id: String) throws -> DemoCredentialScenario {
        try XCTUnwrap(DemoBackend.scenarios.first { $0.id == id })
    }

    private func demoPresentationScenario(_ id: String) throws -> DemoCredentialScenario {
        try XCTUnwrap(DemoBackend.presentationScenarios.first { $0.id == id })
    }

    private func assertStoredCredentialDisplayData(
        scenario: DemoCredentialScenario,
        credentials: [Credential]
    ) throws {
        let credential = try XCTUnwrap(
            credentials.first { $0.format == scenario.format } ?? credentials.first,
            "\(scenario.displayName) should be present in wallet credentials"
        )
        XCTAssertEqual(credential.format, scenario.format, "\(scenario.displayName) should expose the expected format")

        let data = try XCTUnwrap(credential.credentialDataJSON.data(using: .utf8))
        let json = try JSONSerialization.jsonObject(with: data)
        XCTAssertTrue(
            containsAnyUserFacingClaim(json),
            "\(scenario.displayName) display data should include readable user-facing claims: \(credential.credentialDataJSON)"
        )

        if let object = json as? [String: Any] {
            XCTAssertTrue(
                object.keys.contains { $0 != "_sd" },
                "\(scenario.displayName) display data should not expose only selective-disclosure commitments"
            )
        }
    }

    private func containsAnyUserFacingClaim(_ value: Any) -> Bool {
        if let object = value as? [String: Any] {
            return object.keys.contains { userFacingClaimNames.contains(normalizedClaimName($0)) } ||
                object.values.contains { containsAnyUserFacingClaim($0) }
        }
        if let array = value as? [Any] {
            return array.contains { containsAnyUserFacingClaim($0) }
        }
        return false
    }

    private func normalizedClaimName(_ name: String) -> String {
        name.filter { $0.isLetter || $0.isNumber }.lowercased()
    }

    private var userFacingClaimNames: Set<String> {
        [
            "birthdate",
            "birthplace",
            "documentnumber",
            "familyname",
            "familynamebirth",
            "givenname",
            "nationality",
            "portrait",
            "residentcity",
            "residentcountry",
            "residentstate",
            "residentstreet",
        ]
    }
}

private actor RecordingWalletCredentialStore: WalletCredentialStore {
    private(set) var listCredentialsCalls = 0

    func credential(id: String) async throws -> StoredCredential? {
        nil
    }

    func credentials() async throws -> [StoredCredential] {
        listCredentialsCalls += 1
        return []
    }

    func addCredential(_ credential: StoredCredential) async throws {}

    func removeCredential(id: String) async throws -> Bool {
        false
    }
}

private actor RecordingWalletDatabaseKeyProvider: WalletDatabaseKeyProvider {
    private let key = WalletDatabaseKey(
        keyID: "ios-unit-test-provider-key",
        material: Data((0..<32).map { UInt8($0 + 1) })
    )
    private(set) var requestedKeys: [String] = []
    private(set) var deletedKeys: [String] = []

    func databaseKey(walletID: String, databaseName: String) async throws -> WalletDatabaseKey {
        requestedKeys.append("\(walletID):\(databaseName)")
        return key
    }

    func deleteDatabaseKey(walletID: String, databaseName: String) async throws {
        deletedKeys.append("\(walletID):\(databaseName)")
    }
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
