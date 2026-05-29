import XCTest

final class LocalEnterpriseBackendUITests: XCTestCase {
    private let client = WalletE2EClient()

    func testReceiveAndPresentAgainstLocalEnterpriseBackend() async throws {
        let config = LocalEnterpriseConfig.fromEnvironment()
        let attested = (ProcessInfo.processInfo.environment["E2E_ATTESTED"] ?? "false").lowercased() == "true"

        let token = try await getAdminToken(config: config)
        let offerURL = try await createOffer(config: config, token: token)

        let app = XCUIApplication()
        let ui = await WalletE2EUI(app: app)

        if attested {
            let attestationBaseURL = ProcessInfo.processInfo.environment["E2E_ATTESTATION_BASE_URL"] ?? "http://localhost:7500"
            await ui.launch(attestation: [
                "ATTESTATION_BASE_URL": attestationBaseURL,
                "ATTESTATION_ATTESTER_PATH": "\(config.tenantPath).client-attester",
                "ATTESTATION_BEARER_TOKEN": token,
                "ATTESTATION_HOST_HEADER": "\(config.organization).enterprise.localhost",
            ])
        } else {
            await ui.launch()
        }

        let readyStatus = await ui.waitForStatus(
            prefixes: ["Wallet ready", "Bootstrap failed"],
            timeout: 120
        )
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        let offerInput = app.textFields["wallet.offerInput"]
        await ui.replaceText(in: offerInput, value: offerURL)
        app.buttons["wallet.receiveButton"].tap()

        let receiveStatus = await ui.waitForStatus(
            prefixes: ["Received", "Receive failed", "Bootstrap failed"],
            timeout: 220
        )
        XCTAssertTrue(receiveStatus?.starts(with: "Received") == true, "Receive failed, status: \(receiveStatus ?? "nil")")

        XCTAssertFalse(app.staticTexts["No credentials"].exists)

        let verifier = try await createVerifierSession(config: config, token: token)
        let presentInput = app.textFields["wallet.presentationInput"]
        await ui.replaceText(in: presentInput, value: verifier.bootstrapAuthorizationRequestURL)
        app.buttons["wallet.presentButton"].tap()

        let presentStatus = await ui.waitForStatus(
            prefixes: ["Presentation sent", "Presentation finished", "Present failed", "Receive failed", "Bootstrap failed"],
            timeout: 220
        )
        XCTAssertNotNil(presentStatus)
        XCTAssertFalse(presentStatus!.starts(with: "Present failed"), "Present failed: \(presentStatus!)")
        XCTAssertFalse(presentStatus!.starts(with: "Receive failed"), "Receive failed after present: \(presentStatus!)")

        let verifierStatus = try await waitForVerifierStatus(config: config, sessionID: verifier.sessionID, timeoutSeconds: 220)
        XCTAssertEqual(verifierStatus, "SUCCESSFUL", "Verifier status was \(verifierStatus)")
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
