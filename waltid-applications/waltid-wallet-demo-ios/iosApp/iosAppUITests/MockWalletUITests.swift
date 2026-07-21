import XCTest

@MainActor
final class MockWalletUITests: XCTestCase {
    func testWalletLaunchesCredentialsFirstWithoutPermanentTabs() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10), "Wallet ready")
        XCTAssertTrue(app.descendants(matching: .any)["wallet.home"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["wallet.home.receive"].isHittable)
        XCTAssertTrue(app.buttons["wallet.home.present"].isHittable)
        XCTAssertFalse(app.tabBars.firstMatch.exists)
        XCTAssertTrue(app.staticTexts["No credentials yet"].exists)
    }

    func testManualReceiveStaysInOneSheetThroughConsentAndSuccess() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Wallet ready"], timeout: 10), "Wallet ready")

        ui.tapTab(label: "Receive")
        let input = ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL")
        ui.replaceText(in: input, value: "openid-credential-offer://mock")
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Continue")

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        XCTAssertTrue(app.staticTexts["Add this credential?"].exists)
        XCTAssertTrue(app.staticTexts["Example Issuer"].exists)
        XCTAssertTrue(app.staticTexts["Example credential"].exists)

        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Add credential")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )
        XCTAssertTrue(app.staticTexts["Credential added"].exists)
        app.buttons["Done"].tap()
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
    }

    func testWrongFlowOffersSafeSwitchWithoutDoubleResolution() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Wallet ready"], timeout: 10), "Wallet ready")

        ui.tapTab(label: "Receive")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL"),
            value: "openid4vp://mock"
        )
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Continue")

        XCTAssertTrue(app.staticTexts["This QR code is a presentation request, not a credential offer."].waitForExistence(timeout: 10))
        ui.tapButton(identifier: "wallet.switchFlow", fallbackLabel: "Switch to Present")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )
        XCTAssertTrue(app.staticTexts["Share this information?"].exists)
    }

    func testPresentationReviewShowsVerifierProtectionAndPinnedConsentActions() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Wallet ready"], timeout: 10), "Wallet ready")

        issueCredential(app: app, ui: ui)
        ui.tapTab(label: "Present")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL"),
            value: "openid4vp://mock"
        )
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Continue")

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )
        XCTAssertTrue(app.staticTexts["Verifier"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Response protection"].exists)
        XCTAssertTrue(app.buttons["wallet.presentationSubmitButton"].exists)
        XCTAssertTrue(app.buttons["wallet.presentationRejectButton"].exists)
        XCTAssertTrue(app.buttons["wallet.presentationCancelButton"].exists)
    }

    func testSecondDeepLinkRequiresExplicitReplacement() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: [
            "E2E_MOCK_WALLET": "1",
            "E2E_MOCK_WALLET_DELAY_MS": "1500",
            "E2E_INCOMING_REQUEST_URL": "openid4vp://mock",
        ])
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Wallet ready"], timeout: 10), "Wallet ready")

        ui.tapTab(label: "Receive")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL"),
            value: "openid-credential-offer://mock"
        )
        ui.tapButton(identifier: "wallet.e2eIncomingRequest", fallbackLabel: "Inject request")

        XCTAssertTrue(app.alerts["Replace current request?"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.alerts.buttons["Keep current"].exists)
        XCTAssertTrue(app.alerts.buttons["Replace"].exists)
        app.alerts.buttons["Keep current"].tap()
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Continue")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
    }

    private func issueCredential(app: XCUIApplication, ui: WalletE2EUI) {
        ui.tapTab(label: "Receive")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL"),
            value: "openid-credential-offer://mock"
        )
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Continue")
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Review credential offer"], timeout: 10), "Review credential offer")
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Add credential")
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Received"], timeout: 10), "Received 1 credential(s)")
        app.buttons["Done"].tap()
    }
}
