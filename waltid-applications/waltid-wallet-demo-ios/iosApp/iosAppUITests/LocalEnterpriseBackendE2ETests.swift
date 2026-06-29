import XCTest
import TestHelpers

/// End-to-end UI test for the wallet demo app against local enterprise backend.
///
/// Tests the full user flow: launch app → receive credential → present credential
/// Uses XCUIApplication for UI interaction and TestHelpers for backend operations.
///
/// This is an E2E test (slow, requires UI automation + local infrastructure) - runs locally only.
/// Requires: enterprise stack running + ngrok tunnel
final class LocalEnterpriseBackendE2ETests: XCTestCase {
    private let client = WalletE2EClient()

    // Timeouts (aligned with Android for cross-platform consistency)
    private let walletReadyTimeout: TimeInterval = 60         // 1 min - wallet bootstrap
    private let credentialOperationTimeout: TimeInterval = 90 // 1.5 min - receive/present
    private let verifierPollingTimeout: TimeInterval = 30     // 30 sec - backend verification

    func testReceiveAndPresentAgainstLocalEnterpriseBackend() async throws {
        try LocalEnterpriseConfig.requireExplicitLocalEnterpriseRun()
        let config = try LocalEnterpriseConfig.fromEnvironment()
        let attested = LocalEnterpriseConfig.isAttested()

        let backend = try await prepareBackend(config: config)

        let app = XCUIApplication()
        let ui = await WalletE2EUI(app: app)

        if attested {
            let attestationBaseURL = LocalEnterpriseConfig.attestationBaseURL()
            await ui.launch(attestation: [
                "ATTESTATION_BASE_URL": attestationBaseURL,
                "ATTESTATION_ATTESTER_PATH": "\(config.tenantPath).client-attester",
                "ATTESTATION_BEARER_TOKEN": backend.token,
                "ATTESTATION_HOST_HEADER": "\(config.organization).enterprise.localhost",
            ])
        } else {
            await ui.launch()
        }

        let readyStatus = await ui.waitForStatus(
            prefixes: ["Wallet ready", "Bootstrap failed"],
            timeout: walletReadyTimeout
        )
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        let offerInput = app.textFields["wallet.offerInput"]
        await ui.replaceText(in: offerInput, value: backend.offerURL)
        app.buttons["wallet.receiveButton"].tap()

        let receiveStatus = await ui.waitForStatus(
            prefixes: ["Received", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertTrue(receiveStatus?.starts(with: "Received") == true, "Receive failed, status: \(receiveStatus ?? "nil")")

        XCTAssertFalse(app.staticTexts["No credentials"].exists)

        let presentInput = app.textFields["wallet.presentationInput"]
        await ui.replaceText(in: presentInput, value: backend.verifier.bootstrapAuthorizationRequestURL)
        app.buttons["wallet.presentButton"].tap()

        let presentStatus = await ui.waitForStatus(
            prefixes: ["Presentation sent", "Presentation finished", "Present failed", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertNotNil(presentStatus)
        XCTAssertFalse(presentStatus!.starts(with: "Present failed"), "Present failed: \(presentStatus!)")
        XCTAssertFalse(presentStatus!.starts(with: "Receive failed"), "Receive failed after present: \(presentStatus!)")

        let verifierStatus = try await waitForVerifierStatus(config: config, sessionID: backend.verifier.sessionID, timeoutSeconds: verifierPollingTimeout)
        XCTAssertEqual(verifierStatus, "SUCCESSFUL", "Verifier status was \(verifierStatus)")
    }

    func testCredentialsPersistAcrossAppRestart() async throws {
        try LocalEnterpriseConfig.requireExplicitLocalEnterpriseRun()
        let config = try LocalEnterpriseConfig.fromEnvironment()
        let attested = LocalEnterpriseConfig.isAttested()

        let backend = try await prepareBackend(config: config)

        let app = XCUIApplication()
        let ui = await WalletE2EUI(app: app)

        if attested {
            let attestationBaseURL = LocalEnterpriseConfig.attestationBaseURL()
            await ui.launch(attestation: [
                "ATTESTATION_BASE_URL": attestationBaseURL,
                "ATTESTATION_ATTESTER_PATH": "\(config.tenantPath).client-attester",
                "ATTESTATION_BEARER_TOKEN": backend.token,
                "ATTESTATION_HOST_HEADER": "\(config.organization).enterprise.localhost",
            ])
        } else {
            await ui.launch()
        }

        // Phase 1: Bootstrap + receive
        let readyStatus = await ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: walletReadyTimeout)
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet not ready: \(readyStatus ?? "nil")")

        let offerInput = app.textFields["wallet.offerInput"]
        await ui.replaceText(in: offerInput, value: backend.offerURL)
        app.buttons["wallet.receiveButton"].tap()

        let receiveStatus = await ui.waitForStatus(
            prefixes: ["Received", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertTrue(receiveStatus?.starts(with: "Received") == true, "Receive failed: \(receiveStatus ?? "nil")")
        XCTAssertFalse(app.staticTexts["No credentials"].exists)

        // Phase 2: Terminate the app
        app.terminate()
        try await Task.sleep(nanoseconds: 2_000_000_000)

        // Phase 3: Relaunch and verify credentials survived
        if attested {
            let attestationBaseURL = LocalEnterpriseConfig.attestationBaseURL()
            await ui.launch(attestation: [
                "ATTESTATION_BASE_URL": attestationBaseURL,
                "ATTESTATION_ATTESTER_PATH": "\(config.tenantPath).client-attester",
                "ATTESTATION_BEARER_TOKEN": backend.token,
                "ATTESTATION_HOST_HEADER": "\(config.organization).enterprise.localhost",
            ])
        } else {
            await ui.launch()
        }

        let readyAfterRestart = await ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: walletReadyTimeout)
        XCTAssertEqual(readyAfterRestart, "Wallet ready", "Wallet not ready after restart: \(readyAfterRestart ?? "nil")")
        XCTAssertFalse(app.staticTexts["No credentials"].exists, "Credentials not persisted — 'No credentials' shown after restart")

        // Phase 4: Present from persisted credential
        let presentInput = app.textFields["wallet.presentationInput"]
        await ui.replaceText(in: presentInput, value: backend.verifier.bootstrapAuthorizationRequestURL)
        app.buttons["wallet.presentButton"].tap()

        let presentStatus = await ui.waitForStatus(
            prefixes: ["Presentation sent", "Presentation finished", "Present failed", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertNotNil(presentStatus)
        XCTAssertFalse(presentStatus!.starts(with: "Present failed"), "Present failed after restart: \(presentStatus!)")

        let verifierStatus = try await waitForVerifierStatus(config: config, sessionID: backend.verifier.sessionID, timeoutSeconds: verifierPollingTimeout)
        XCTAssertEqual(verifierStatus, "SUCCESSFUL", "Verifier status after restart: \(verifierStatus)")
    }

    private struct PreparedBackend {
        let token: String
        let offerURL: String
        let verifier: (sessionID: String, bootstrapAuthorizationRequestURL: String)
    }

    private func prepareBackend(config: LocalEnterpriseConfig) async throws -> PreparedBackend {
        do {
            let token = try await getAdminToken(config: config)
            let offerURL = try await createOffer(config: config, token: token)
            let verifier = try await createVerifierSession(config: config, token: token)
            return PreparedBackend(token: token, offerURL: offerURL, verifier: verifier)
        } catch {
            throw WalletE2ESetupError(message: """
            Local Enterprise E2E environment is not ready.
            Expected quickstart baseline: org=\(config.organization), tenant=\(config.tenant), issuerProfile=\(config.issuerProfile), verifier=\(config.verifier).
            From waltid-enterprise-quickstart run docker compose up, then cd cli && npm install.
            Provision with HOST_ALIAS_DOMAIN=<your-ngrok-domain> npx tsx walt.ts --init-system,
            then HOST_ALIAS_DOMAIN=<your-ngrok-domain> npx tsx walt.ts --setup-all.
            For an existing database, rerun the --setup-all command.
            Create mobile helper resources explicitly with scripts/e2e-local-enterprise.sh --prepare-only.
            Then rerun this test through scripts/e2e-local-enterprise.sh so ngrok, issuer2-noattest, and verifier2-mobile are checked first.
            Original error: \(error.localizedDescription)
            """)
        }
    }

    private func getAdminToken(config: LocalEnterpriseConfig) async throws -> String {
        let body = try jsonString([
            "email": config.adminEmail,
            "password": config.adminPassword,
        ])
        let headers = ["Content-Type": "application/json", "ngrok-skip-browser-warning": "true"]
        let response = try await client.jsonRequest(
            url: config.apiBaseURL.appending(path: "/auth/account/emailpass"),
            method: "POST",
            headers: headers,
            body: Data(body.utf8)
        )
        guard let token = response["token"] as? String, !token.isEmpty else {
            throw NSError(domain: "WalletE2E", code: 100, userInfo: [NSLocalizedDescriptionKey: "Missing token in auth response: \(response)"])
        }
        return token
    }

    private func createOffer(config: LocalEnterpriseConfig, token: String) async throws -> String {
        let endpoint = config.ngrokBaseURL.appending(path: "/v2/\(config.tenantPath).\(config.issuerProfile)/issuer-service-api/credentials/offers")
        let body = try jsonString(["authMethod": "PRE_AUTHORIZED"])
        let response = try await client.jsonRequest(
            url: endpoint,
            method: "POST",
            headers: [
                "Authorization": "Bearer \(token)",
                "Content-Type": "application/json",
                "ngrok-skip-browser-warning": "true",
            ],
            body: Data(body.utf8)
        )

        guard let offer = response["credentialOffer"] as? String, !offer.isEmpty else {
            throw NSError(domain: "WalletE2E", code: 101, userInfo: [NSLocalizedDescriptionKey: "Missing credentialOffer in response: \(response)"])
        }
        return offer
    }

    private func createVerifierSession(config: LocalEnterpriseConfig, token: String) async throws -> (sessionID: String, bootstrapAuthorizationRequestURL: String) {
        let payload: [String: Any] = [
            "flow_type": "cross_device",
            "core_flow": [
                "dcql_query": [
                    "credentials": [[
                        "id": "my_mdl",
                        "format": "mso_mdoc",
                        "meta": ["doctype_value": "org.iso.18013.5.1.mDL"],
                        "claims": [
                            ["path": ["org.iso.18013.5.1", "family_name"]],
                            ["path": ["org.iso.18013.5.1", "given_name"]],
                        ],
                    ]],
                ],
            ],
        ]

        let endpoint = config.ngrokBaseURL.appending(path: "/v1/\(config.tenantPath).\(config.verifier)/verifier2-service-api/verification-session/create")
        let response = try await client.jsonRequest(
            url: endpoint,
            method: "POST",
            headers: [
                "Authorization": "Bearer \(token)",
                "Content-Type": "application/json",
                "ngrok-skip-browser-warning": "true",
            ],
            body: Data(try jsonString(payload).utf8)
        )

        guard let sessionID = response["sessionId"] as? String, !sessionID.isEmpty,
              let requestURL = response["bootstrapAuthorizationRequestUrl"] as? String, !requestURL.isEmpty else {
            throw NSError(domain: "WalletE2E", code: 102, userInfo: [NSLocalizedDescriptionKey: "Invalid verifier session response: \(response)"])
        }

        return (sessionID, requestURL)
    }

    private func waitForVerifierStatus(config: LocalEnterpriseConfig, sessionID: String, timeoutSeconds: TimeInterval) async throws -> String {
        let deadline = Date().addingTimeInterval(timeoutSeconds)
        var lastStatus = "UNKNOWN"

        while Date() < deadline {
            let token = try await getAdminToken(config: config)
            let endpoint = config.apiBaseURL.appending(path: "/v1/\(config.tenantPath).\(config.verifier).\(sessionID)/verifier2-service-api/verification-session/info")

            let response = try await client.jsonRequest(
                url: endpoint,
                headers: [
                    "Authorization": "Bearer \(token)",
                    "ngrok-skip-browser-warning": "true",
                ]
            )

            if let session = response["session"] as? [String: Any], let status = session["status"] as? String {
                lastStatus = status
                if status == "SUCCESSFUL" || status == "FAILED" || status == "EXPIRED" {
                    return status
                }
            }

            try await Task.sleep(nanoseconds: 2_000_000_000)
        }

        return lastStatus
    }
}
