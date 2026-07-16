import XCTest
import TestHelpers

/// End-to-end UI test for the SwiftUI wallet demo app against the public demo stack.
///
/// Tests the full user flow: launch app, receive credential, present credential,
/// and confirm verifier2 observed the presentation.
@MainActor
final class PublicDemoBackendE2ETests: XCTestCase {

    private let backend = DemoBackend.shared

    // Timeouts (aligned with Android for cross-platform consistency)
    private let walletReadyTimeout: TimeInterval = 60
    private let credentialOperationTimeout: TimeInterval = 90
    private let verifierPollingTimeout: TimeInterval = 30

    func testReceiveAndPresentAgainstPublicDemoIssuer2Verifier2() async throws {
        let scenario = try publicDemoScenario()
        let offer = try await backend.createOffer(scenario: scenario)

        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: publicDemoEnvironment())

        let readyStatus = ui.waitForStatus(
            prefixes: ["Wallet ready", "Bootstrap failed"],
            timeout: walletReadyTimeout
        )
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        ui.tapTab(label: "Receive")
        let offerInput = ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL")
        ui.replaceText(in: offerInput, value: offer.offerUrl)
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        let offerReadyStatus = ui.waitForStatus(
            prefixes: ["Review credential offer", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertEqual(offerReadyStatus, "Review credential offer", "Offer preview did not appear, status: \(offerReadyStatus ?? "nil")")
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")

        let receiveStatus = ui.waitForStatus(
            prefixes: ["Received", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertTrue(receiveStatus?.starts(with: "Received") == true, "Receive failed, status: \(receiveStatus ?? "nil")")

        ui.tapTab(label: "Credentials")
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        ui.assertExists(identifierPrefix: "wallet.credentialOverview.")
        XCTAssertTrue(app.staticTexts["Credential details"].waitForExistence(timeout: 20))
        ui.tapNavigationBack()

        let session = try await backend.createVerifierSession(scenario: scenario)
        ui.tapTab(label: "Present")
        let presentInput = ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL")
        ui.replaceText(in: presentInput, value: session.authorizationRequestUri)
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Preview")
        let previewStatus = ui.waitForStatus(
            prefixes: ["Review presentation request", "Preview failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertEqual(previewStatus, "Review presentation request", "Preview failed, status: \(previewStatus ?? "nil")")

        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        ui.assertExists(identifierPrefix: "wallet.credentialOverview.")
        XCTAssertTrue(app.staticTexts["Credential details"].waitForExistence(timeout: 20))
        ui.tapNavigationBack()

        ui.tapButton(identifier: "wallet.presentationSubmitButton", fallbackLabel: "Share")

        let presentStatus = ui.waitForStatus(
            prefixes: ["Presentation sent", "Presentation finished", "Present failed", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertNotNil(presentStatus)
        XCTAssertFalse(presentStatus!.starts(with: "Present failed"), "Present failed: \(presentStatus!)")
        XCTAssertFalse(presentStatus!.starts(with: "Receive failed"), "Receive failed during presentation: \(presentStatus!)")
        XCTAssertFalse(presentStatus!.starts(with: "Bootstrap failed"), "Bootstrap failed during presentation: \(presentStatus!)")

        try await backend.waitForVerifierSuccess(sessionID: session.sessionID, timeoutSeconds: verifierPollingTimeout)
    }

    func testTransactionDataPreviewAgainstPublicDemoIssuer2Verifier2() async throws {
        let scenario = DemoBackend.transactionDataPresentationScenario
        let offer = try await backend.createOffer(scenario: scenario)

        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: publicDemoEnvironment())

        let readyStatus = ui.waitForStatus(
            prefixes: ["Wallet ready", "Bootstrap failed"],
            timeout: walletReadyTimeout
        )
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        ui.tapTab(label: "Receive")
        let offerInput = ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL")
        ui.replaceText(in: offerInput, value: offer.offerUrl)
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        let offerReadyStatus2 = ui.waitForStatus(
            prefixes: ["Review credential offer", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertEqual(offerReadyStatus2, "Review credential offer", "Offer preview did not appear, status: \(offerReadyStatus2 ?? "nil")")
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")

        let receiveStatus = ui.waitForStatus(
            prefixes: ["Received", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertTrue(receiveStatus?.starts(with: "Received") == true, "Receive failed, status: \(receiveStatus ?? "nil")")

        let session = try await backend.createTransactionDataVerifierSession(scenario: scenario)
        ui.tapTab(label: "Present")
        let presentInput = ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL")
        ui.replaceText(in: presentInput, value: session.authorizationRequestUri)
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Preview")
        let previewStatus = ui.waitForStatus(
            prefixes: ["Review presentation request", "Preview failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertEqual(previewStatus, "Review presentation request", "Preview failed, status: \(previewStatus ?? "nil")")

        XCTAssertTrue(app.staticTexts["Payment Authorization"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["42.00"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["EUR"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["ACME Corp"].waitForExistence(timeout: 10))
        let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        screenshot.name = "WAL-1077 native iOS transaction data preview"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    func testTransactionCodePromptRejectsWrongCodeAndRetriesAgainstPublicDemoIssuer2() async throws {
        let scenario = try publicDemoScenario()
        let offer = try await backend.createOffer(
            scenario: scenario,
            withGeneratedTransactionCode: true
        )
        let transactionCode = try XCTUnwrap(offer.txCode)
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: publicDemoEnvironment())

        let readyStatus = ui.waitForStatus(
            prefixes: ["Wallet ready", "Bootstrap failed"],
            timeout: walletReadyTimeout
        )
        guard readyStatus == "Wallet ready" else {
            XCTFail("Wallet did not become ready, status: \(readyStatus ?? "nil")")
            return
        }

        ui.openDeepLink(offer.offerUrl)
        guard ui.waitForTextInputValue(
            identifier: "wallet.offerInput",
            fallbackLabel: "Credential offer URL",
            value: offer.offerUrl,
            timeout: 20
        ) else {
            XCTFail("Offer URL did not appear after opening the deep link")
            return
        }
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")

        let previewStatus = ui.waitForStatus(
            prefixes: ["Review credential offer", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        guard previewStatus == "Review credential offer" else {
            XCTFail("Offer preview did not appear, status: \(previewStatus ?? "nil")")
            return
        }

        let txCodeInput = ui.textInput(identifier: "wallet.txCodeInput", fallbackLabel: "Transaction code")
        guard txCodeInput.waitForExistence(timeout: 20) else {
            XCTFail("Transaction-code input did not appear in offer review")
            return
        }

        ui.replaceText(in: txCodeInput, value: incorrectCode(for: transactionCode))
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        let rejectedStatus = ui.waitForStatus(
            prefixes: ["Receive failed"],
            timeout: credentialOperationTimeout
        )
        guard rejectedStatus?.starts(with: "Receive failed") == true else {
            XCTFail("Incorrect transaction code was not rejected, status: \(rejectedStatus ?? "nil")")
            return
        }

        // After failure, offer review is dismissed — resolve the offer again
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        let previewStatusRetry = ui.waitForStatus(
            prefixes: ["Review credential offer", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        guard previewStatusRetry == "Review credential offer" else {
            XCTFail("Offer preview did not reappear for retry, status: \(previewStatusRetry ?? "nil")")
            return
        }

        let txCodeInputRetry = ui.textInput(identifier: "wallet.txCodeInput", fallbackLabel: "Transaction code")
        guard txCodeInputRetry.waitForExistence(timeout: 20) else {
            XCTFail("Transaction-code input did not reappear for retry")
            return
        }
        ui.replaceText(in: txCodeInputRetry, value: transactionCode)
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        let receivedStatus = ui.waitForStatus(
            prefixes: ["Received", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertTrue(
            receivedStatus?.starts(with: "Received") == true,
            "Receive did not succeed after correcting the transaction code, status: \(receivedStatus ?? "nil")"
        )
    }

    private func publicDemoScenario() throws -> DemoCredentialScenario {
        try XCTUnwrap(DemoBackend.presentationScenarios.first { $0.id == "eudi-pid-mdoc" })
    }

    private func publicDemoEnvironment() -> [String: String] {
        ["TRANSACTION_DATA_PROFILES_URL": DemoBackend.transactionDataProfilesURL.absoluteString]
    }

    private func incorrectCode(for code: String) -> String {
        precondition(!code.isEmpty, "Transaction code must not be empty")
        let replacement = code.last == "0" ? "1" : "0"
        return String(code.dropLast()) + replacement
    }
}

@MainActor
final class MockCredentialDisplayUITests: XCTestCase {

    func testMockCredentialDetailsRenderPortraitAndCredentialInfo() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        let readyStatus = ui.waitForStatus(
            prefixes: ["Wallet ready", "Bootstrap failed"],
            timeout: 30
        )
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        ui.tapTab(label: "Receive")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL"),
            value: "openid-credential-offer://mock"
        )
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )

        let credentialCard = app.buttons["wallet.credentialCard.cred-1"]
        XCTAssertTrue(credentialCard.waitForExistence(timeout: 10), "Mock credential card was not shown")
        credentialCard.tap()

        XCTAssertTrue(app.descendants(matching: .any)["wallet.credentialOverview.cred-1"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["wallet.claimGroup.Personal_details"].waitForExistence(timeout: 10))
        ui.assertExists(identifier: ui.claimImageIdentifier(path: "portrait.elementValue"), timeout: 10)
        XCTAssertTrue(app.staticTexts["wallet.claimGroup.About_this_credential"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["No credential details available"].exists)
    }
}
