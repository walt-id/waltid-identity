import XCTest

final class EudiPublicBackendUITests: XCTestCase {
    private let client = WalletE2EClient()

    func testReceiveAndPresentAgainstEudiPublicBackends() async throws {
        let config = EudiPublicConfig.fromEnvironment()
        let offerURL: String
        if let configuredOffer = config.offerURL, !configuredOffer.isEmpty {
            offerURL = configuredOffer
        } else {
            offerURL = try await generatePreAuthorizedOffer(credentialID: config.credentialID)
        }

        let app = XCUIApplication()
        let ui = await WalletE2EUI(app: app)
        await ui.launch()

        let readyStatus = await ui.waitForStatus(
            prefixes: ["Wallet ready", "Bootstrap failed"],
            timeout: 60
        )
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        let offerInput = app.textFields["wallet.offerInput"]
        await ui.replaceText(in: offerInput, value: offerURL)
        app.buttons["wallet.receiveButton"].tap()

        let receiveStatus = await ui.waitForStatus(
            prefixes: ["Received", "Receive failed", "Bootstrap failed"],
            timeout: 90
        )
        XCTAssertTrue(receiveStatus?.starts(with: "Received") == true, "Receive failed, status: \(receiveStatus ?? "nil")")

        let verifier = try await createVerifierTransaction(credentialID: config.credentialID)
        let presentInput = app.textFields["wallet.presentationInput"]
        await ui.replaceText(in: presentInput, value: verifier.authorizationRequestURI)
        app.buttons["wallet.presentButton"].tap()

        let presentStatus = await ui.waitForStatus(
            prefixes: ["Presentation sent", "Presentation finished", "Present failed", "Receive failed", "Bootstrap failed"],
            timeout: 90
        )
        XCTAssertNotNil(presentStatus)
        XCTAssertFalse(presentStatus!.starts(with: "Present failed"), "Present failed: \(presentStatus!)")
        XCTAssertFalse(
            presentStatus!.starts(with: "Presentation finished"),
            "Presentation finished without verifier confirmation: \(presentStatus!)"
        )

        let verifierEvent = try await waitForVerifierResult(transactionID: verifier.transactionID, timeoutSeconds: 90)
        XCTAssertNotNil(verifierEvent, "Verifier did not confirm wallet response")
    }

    private func generatePreAuthorizedOffer(credentialID: String) async throws -> String {
        let flow = EudiOfferFlow(client: client)
        return try await flow.generate(credentialID: credentialID)
    }

    private func createVerifierTransaction(credentialID: String) async throws -> (transactionID: String, authorizationRequestURI: String) {
        let payload: [String: Any] = [
            "dcql_query": buildDcqlQuery(credentialID: credentialID),
            "nonce": UUID().uuidString,
            "request_uri_method": "post",
            "profile": "openid4vp",
            "authorization_request_uri": "openid4vp://",
        ]

        let response = try await client.jsonRequest(
            url: URL(string: "https://verifier-backend.eudiw.dev/ui/presentations/v2")!,
            method: "POST",
            headers: ["Content-Type": "application/json"],
            body: Data(try jsonString(payload).utf8)
        )

        guard let tx = response["transaction_id"] as? String,
              let request = response["authorization_request_uri"] as? String,
              !tx.isEmpty,
              !request.isEmpty else {
            throw NSError(domain: "WalletE2E", code: 200, userInfo: [NSLocalizedDescriptionKey: "Invalid verifier response: \(response)"])
        }

        return (tx, request)
    }

    private func waitForVerifierResult(transactionID: String, timeoutSeconds: TimeInterval) async throws -> [String: Any]? {
        let deadline = Date().addingTimeInterval(timeoutSeconds)

        while Date() < deadline {
            let response = try await client.jsonRequest(
                url: URL(string: "https://verifier-backend.eudiw.dev/ui/presentations/\(transactionID)/events")!
            )

            let events = response["events"] as? [[String: Any]] ?? []

            for event in events {
                let name = (event["event"] as? String ?? "") + " " + (event["cause"] as? String ?? "")
                let normalized = name.lowercased()
                if normalized.contains("failed") || normalized.contains("error") || normalized.contains("invalid") || normalized.contains("timed out") {
                    throw NSError(domain: "WalletE2E", code: 201, userInfo: [NSLocalizedDescriptionKey: "Verifier failure event: \(event)"])
                }
            }

            for event in events {
                let eventName = (event["event"] as? String ?? "").lowercased()
                if eventName.contains("wallet response posted") || eventName.contains("successful") || eventName.contains("verified") {
                    return event
                }
            }

            try await Task.sleep(nanoseconds: 2_000_000_000)
        }

        return nil
    }

    private func buildDcqlQuery(credentialID: String) -> [String: Any] {
        let normalized = credentialID.lowercased()
        if normalized.contains("sd_jwt") {
            return [
                "credentials": [[
                    "id": "query_0",
                    "format": "dc+sd-jwt",
                    "meta": [
                        "vct_values": ["urn:eudi:pid:1", "eu.europa.ec.eudi.pid.1"],
                    ],
                ]],
            ]
        }

        let docType = normalized.contains("mdl") ? "org.iso.18013.5.1.mDL" : "eu.europa.ec.eudi.pid.1"
        return [
            "credentials": [[
                "id": "query_0",
                "format": "mso_mdoc",
                "meta": ["doctype_value": docType],
            ]],
        ]
    }
}

private final class EudiOfferFlow {
    private let client: WalletE2EClient

    init(client: WalletE2EClient) {
        self.client = client
    }

    func generate(credentialID: String) async throws -> String {
        let entrypoint = URL(string: "https://issuer.eudiw.dev/credential_offer")!
        let backendGenerate = URL(string: "https://backend.issuer.eudiw.dev/credential_offer")!
        let backendAuthorize = URL(string: "https://backend.issuer.eudiw.dev/form_authorize_generate")!

        let entry = try await client.textRequest(url: entrypoint, allow3xx: true)
        let redirectAction = resolveURL(base: entry.finalURL, action: extractFormAction(page: entry.body, formID: "redirect_form"))
        let redirectPayload = extractPayload(page: entry.body)
        _ = try await client.formRequest(url: redirectAction, fields: ["payload": redirectPayload])

        let preauthRedirect = try await client.formRequest(url: backendGenerate, fields: [
            credentialID: credentialID,
            "Authorization Code Grant": "pre_auth_code",
            "credential_offer_URI": "openid-credential-offer://",
            "proceed": "Submit",
        ])

        let displayAction = resolveURL(base: preauthRedirect.finalURL, action: extractFormAction(page: preauthRedirect.body, formID: "redirect_form"))
        let displayPayload = extractPayload(page: preauthRedirect.body)
        let displayPage = try await client.formRequest(url: displayAction, fields: ["payload": displayPayload])

        let countryAction = resolveURL(base: displayPage.finalURL, action: extractFormAction(page: displayPage.body, formID: "selectCountryForm"))
        let authorizeRedirect = try await client.formRequest(url: countryAction, fields: [
            "birthdate": "1990-01-01",
            "family_name": "Tester",
            "given_name": "Alice",
            "nationalities[0][country_code]": "DE",
            "place_of_birth[0][country]": "DE",
            "place_of_birth[0][region]": "Berlin",
            "place_of_birth[0][locality]": "Berlin",
            "proceed": "Confirm",
        ])

        let authAction = resolveURL(base: authorizeRedirect.finalURL, action: extractFormAction(page: authorizeRedirect.body, formID: "redirect_form"))
        let authPayload = extractPayload(page: authorizeRedirect.body)
        let authPage = try await client.formRequest(url: authAction, fields: ["payload": authPayload])

        let userID = extractUserID(page: authPage.body)
        let offerRedirect = try await client.formRequest(url: backendAuthorize, fields: [
            "user_id": userID,
            "proceed": "Authorize",
        ])

        let payloadRaw = extractPayload(page: offerRedirect.body)
        guard let payloadData = payloadRaw.data(using: .utf8),
              let payloadJSON = try JSONSerialization.jsonObject(with: payloadData) as? [String: Any] else {
            throw NSError(domain: "WalletE2E", code: 210, userInfo: [NSLocalizedDescriptionKey: "Invalid final payload"])
        }

        let txCodeRaw = payloadJSON["tx_code"]
        guard let txCodeValue = txCodeRaw as? String ?? (txCodeRaw as? NSNumber)?.stringValue,
              let urlData = payloadJSON["url_data"] as? String else {
            throw NSError(domain: "WalletE2E", code: 211, userInfo: [NSLocalizedDescriptionKey: "Missing tx_code or url_data in final payload"])
        }
        guard let offerURI = URL(string: urlData),
              let components = URLComponents(url: offerURI, resolvingAgainstBaseURL: false),
              let credentialOfferRaw = components.queryItems?.first(where: { $0.name == "credential_offer" })?.value,
              let credentialData = credentialOfferRaw.data(using: .utf8),
              var offerJSON = try JSONSerialization.jsonObject(with: credentialData) as? [String: Any] else {
            throw NSError(domain: "WalletE2E", code: 211, userInfo: [NSLocalizedDescriptionKey: "Missing credential_offer payload"])
        }

        guard var grants = offerJSON["grants"] as? [String: Any],
              var preAuth = grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"] as? [String: Any] else {
            throw NSError(domain: "WalletE2E", code: 212, userInfo: [NSLocalizedDescriptionKey: "Missing pre-authorized grant"])
        }

        var txCode = (preAuth["tx_code"] as? [String: Any]) ?? [:]
        txCode["value"] = txCodeValue
        preAuth["tx_code"] = txCode
        grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"] = preAuth
        offerJSON["grants"] = grants

        let updated = try jsonString(offerJSON)
        var out = URLComponents()
        out.scheme = offerURI.scheme ?? "openid-credential-offer"
        out.host = "credential_offer"
        out.queryItems = [URLQueryItem(name: "credential_offer", value: updated)]

        guard let finalURL = out.url?.absoluteString else {
            throw NSError(domain: "WalletE2E", code: 213, userInfo: [NSLocalizedDescriptionKey: "Failed to build offer URL"])
        }
        return finalURL
    }

    private func extractPayload(page: String) -> String {
        guard let raw = firstMatch(page, pattern: "name=\\\"payload\\\"\\s+value='(.*?)'\\s*>") else {
            fatalError("Hidden payload input not found")
        }
        return unescapedHTML(raw)
    }

    private func extractFormAction(page: String, formID: String) -> String {
        let pattern = "<form\\s+id=\\\"\(NSRegularExpression.escapedPattern(for: formID))\\\"[^>]*action=\\\"([^\\\"]+)\\\""
        guard let raw = firstMatch(page, pattern: pattern) else {
            fatalError("Form action not found for id=\(formID)")
        }
        return unescapedHTML(raw)
    }

    private func extractUserID(page: String) -> String {
        if let v = firstMatch(page, pattern: "name=\\\"user_id\\\"\\s+value=\\\"([^\\\"]+)\\\"") {
            return v
        }
        if let v = firstMatch(page, pattern: "value=\\\"([a-f0-9\\-]{36})\\\"\\s+name=\\\"user_id\\\"") {
            return v
        }
        fatalError("user_id not found on authorization page")
    }
}
