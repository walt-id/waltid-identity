import XCTest
import TestHelpers

final class EudiPublicBackendUITests: XCTestCase {
    private let client = WalletE2EClient()

    // Timeouts (aligned with Android for cross-platform consistency)
    private let walletReadyTimeout: TimeInterval = 60         // 1 min - wallet bootstrap
    private let credentialOperationTimeout: TimeInterval = 90 // 1.5 min - receive/present
    private let verifierPollingTimeout: TimeInterval = 30     // 30 sec - backend verification

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
            timeout: walletReadyTimeout
        )
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        let offerInput = app.textFields["wallet.offerInput"]
        await ui.replaceText(in: offerInput, value: offerURL)
        app.buttons["wallet.receiveButton"].tap()

        let receiveStatus = await ui.waitForStatus(
            prefixes: ["Received", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertTrue(receiveStatus?.starts(with: "Received") == true, "Receive failed, status: \(receiveStatus ?? "nil")")

        let verifier = try await createVerifierTransaction(credentialID: config.credentialID)
        let presentInput = app.textFields["wallet.presentationInput"]
        await ui.replaceText(in: presentInput, value: verifier.authorizationRequestURI)
        app.buttons["wallet.presentButton"].tap()

        let presentStatus = await ui.waitForStatus(
            prefixes: ["Presentation sent", "Presentation finished", "Present failed", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertNotNil(presentStatus)
        XCTAssertFalse(presentStatus!.starts(with: "Present failed"), "Present failed: \(presentStatus!)")
        XCTAssertFalse(
            presentStatus!.starts(with: "Presentation finished"),
            "Presentation finished without verifier confirmation: \(presentStatus!)"
        )

        try await TestHelpers.waitForVerifierSuccess(transactionID: verifier.transactionID, timeoutSeconds: verifierPollingTimeout)
    }

    private func generatePreAuthorizedOffer(credentialID: String) async throws -> String {
        let flow = EudiOfferFlow(client: client)
        return try await flow.generate(credentialID: credentialID)
    }

    private func createVerifierTransaction(credentialID: String) async throws -> (transactionID: String, authorizationRequestURI: String) {
        let payload: [String: Any] = [
            "dcql_query": TestHelpers.buildDcqlQuery(credentialID: credentialID),
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

}
