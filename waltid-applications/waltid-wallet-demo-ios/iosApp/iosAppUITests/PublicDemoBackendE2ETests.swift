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
        ui.launch()

        let readyStatus = ui.waitForStatus(
            prefixes: ["Wallet ready", "Bootstrap failed"],
            timeout: walletReadyTimeout
        )
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        ui.tapTab(label: "Receive")
        let offerInput = ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL")
        ui.replaceText(in: offerInput, value: offer.offerUrl)
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")

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
        ui.launch()

        let readyStatus = ui.waitForStatus(
            prefixes: ["Wallet ready", "Bootstrap failed"],
            timeout: walletReadyTimeout
        )
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        ui.tapTab(label: "Receive")
        let offerInput = ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL")
        ui.replaceText(in: offerInput, value: offer.offerUrl)
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")

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
        XCTAssertTrue(app.staticTexts["INV-2026-042"].waitForExistence(timeout: 10))

        let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        screenshot.name = "WAL-1077 native iOS transaction data preview"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    private func publicDemoScenario() throws -> DemoCredentialScenario {
        try XCTUnwrap(DemoBackend.presentationScenarios.first { $0.id == "eudi-pid-mdoc" })
    }
}

@MainActor
final class MockCredentialDisplayUITests: XCTestCase {

    func testMockCredentialDetailsRenderPortraitAndCredentialInfo() {
        let app = XCUIApplication()
        app.launchEnvironment["E2E_USE_MOCK_WALLET"] = "1"
        let ui = WalletE2EUI(app: app)
        ui.launch()

        let readyStatus = ui.waitForStatus(
            prefixes: ["Wallet ready", "Bootstrap failed"],
            timeout: 30
        )
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        let credentialCard = app.buttons["wallet.credentialCard.mock-credential"]
        XCTAssertTrue(credentialCard.waitForExistence(timeout: 10), "Mock credential card was not shown")
        credentialCard.tap()

        XCTAssertTrue(app.descendants(matching: .any)["wallet.credentialOverview.mock-credential"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["wallet.claimGroup.Personal_details"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.images["wallet.claimImage.portrait"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["wallet.claimGroup.About_this_credential"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["No credential details available"].exists)
    }
}
