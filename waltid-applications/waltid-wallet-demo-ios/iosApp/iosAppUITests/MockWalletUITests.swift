import CryptoKit
import XCTest

@MainActor
final class MockWalletUITests: XCTestCase {
    private static let didClientID = "decentralized_identifier:did:jwk:abc"

    func testUnsignedPaymentShowsLocalizedFieldsAndRequiresSeparateConfirmation() {
        continueAfterFailure = false
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1", "E2E_MOCK_PAYMENT": "unsigned"])
        receiveMockCredential(app: app, ui: ui)
        ui.openWalletLink("openid4vp://mock")
        ui.assertExists(identifier: "payment-unsigned-warning")
        XCTAssertTrue(app.staticTexts["Zahlung prüfen"].exists)
        XCTAssertTrue(app.staticTexts["11.56 EUR"].exists)
        XCTAssertTrue(app.staticTexts["Super Store"].exists)
        XCTAssertFalse(app.staticTexts["txn-1"].exists)
        XCTAssertFalse(app.staticTexts["bound-but-hidden"].exists)
        XCTAssertFalse(app.descendants(matching: .any)["payment-security-hint"].exists)
        ui.tapElement(identifier: "payment-details-toggle")
        XCTAssertTrue(app.staticTexts["txn-1"].waitForExistence(timeout: 5))
        let details = XCTAttachment(screenshot: app.screenshot())
        details.name = "payment-localized-details"
        details.lifetime = .keepAlways
        add(details)
        ui.tapButton(identifier: "wallet.presentationSubmitButton", fallbackLabel: "Zahlen")
        let alert = app.alerts["Unsigned payment request"]
        XCTAssertTrue(alert.waitForExistence(timeout: 5))
        let warning = XCTAttachment(screenshot: app.screenshot())
        warning.name = "payment-unsigned-confirmation"
        warning.lifetime = .keepAlways
        add(warning)
        alert.buttons["payment-unsigned-back"].firstMatch.tap()
        XCTAssertTrue(app.buttons["wallet.presentationSubmitButton"].exists)
        ui.tapButton(identifier: "wallet.presentationSubmitButton", fallbackLabel: "Zahlen")
        XCTAssertTrue(alert.waitForExistence(timeout: 5))
        alert.buttons["payment-unsigned-confirm"].firstMatch.tap()
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Presentation sent", "Present failed"], timeout: 10), "Presentation sent")
    }

    func testMissingPaymentMetadataBlocksSharing() {
        continueAfterFailure = false
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1", "E2E_MOCK_PAYMENT": "blocked"])
        receiveMockCredential(app: app, ui: ui)
        ui.openWalletLink("openid4vp://mock")
        ui.assertExists(identifier: "payment-consent-blocked")
        XCTAssertFalse(app.buttons["wallet.presentationSubmitButton"].isEnabled)
        XCTAssertFalse(app.staticTexts["bound-but-hidden"].exists)
        XCTAssertFalse(app.alerts.firstMatch.exists)
    }

    func testPinCreationRequiresSixDigitsAndMatchingConfirmationOnOneScreen() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        app.launchEnvironment = ["E2E_WALLET_ID": "pin-form-\(UUID().uuidString)", "E2E_MOCK_WALLET": "1",
            "WALLET_SIGNING_PROTECTION_MODE": "disabled"]
        app.launch()
        let input = ui.textInput(identifier: "wallet.pinInput", fallbackLabel: "PIN")
        XCTAssertTrue(input.waitForExistence(timeout: 10))
        XCTAssertTrue(app.secureTextFields["wallet.pinConfirmationInput"].exists)
        XCTAssertTrue(app.switches["wallet.pinBiometricToggle"].exists)
        ui.replaceText(in: input, value: "1234")
        XCTAssertFalse(app.buttons["wallet.pinSubmitButton"].isEnabled)
        ui.replaceText(in: input, value: "123456")
        XCTAssertFalse(app.buttons["wallet.pinSubmitButton"].isEnabled)
        let confirmation = ui.textInput(identifier: "wallet.pinConfirmationInput", fallbackLabel: "Confirm PIN")
        XCTAssertTrue(confirmation.waitForExistence(timeout: 5))
        XCTAssertTrue(input.exists)
        ui.replaceText(in: confirmation, value: "654321")
        ui.tapButton(identifier: "wallet.pinSubmitButton", fallbackLabel: "Continue")
        XCTAssertTrue(app.staticTexts["PIN confirmation does not match"].waitForExistence(timeout: 5))
        ui.replaceText(in: confirmation, value: "123456")
        ui.tapButton(identifier: "wallet.pinSubmitButton", fallbackLabel: "Continue")
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10), "Wallet ready")
    }

    func testWalletHomeScannerRoutesOfferAndBackCancelsReview() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10), "Wallet ready")
        XCTAssertFalse(app.tabBars.firstMatch.exists)
        XCTAssertTrue(app.buttons["wallet.proximityStartButton"].exists)
        ui.tapButton(identifier: "wallet.scanButton", fallbackLabel: "Scan or paste a link")
        let input = ui.textInput(identifier: "wallet.scanInput", fallbackLabel: "Credential offer or request")
        ui.replaceText(in: input, value: "FIDO:/0123456789")
        XCTAssertFalse(app.buttons["wallet.scanContinue"].isEnabled)
        ui.replaceText(in: input, value: "openid-credential-offer://mock")
        ui.tapButton(identifier: "wallet.scanContinue", fallbackLabel: "Continue")
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10), "Review credential offer")
        ui.assertExists(identifier: "wallet.offerAcceptButton")
        XCTAssertFalse(app.textFields["wallet.offerInput"].exists)
        ui.tapButton(identifier: "wallet.flowBack", fallbackLabel: "Back to wallet")
        XCTAssertTrue(app.buttons["wallet.scanButton"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["No credentials yet"].exists)
    }

    func testCredentialsStayLoadingUntilTheInitialReadCompletes() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1", "E2E_MOCK_WALLET_DELAY_MS": "8000"])
        let loading = app.descendants(matching: .any)["wallet.credentials.loading"].firstMatch
        XCTAssertTrue(loading.waitForExistence(timeout: 3))
        XCTAssertFalse(app.descendants(matching: .any)["wallet.credentials.empty"].exists)
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 20), "Wallet ready")
        XCTAssertTrue(app.descendants(matching: .any)["wallet.credentials.empty"].waitForExistence(timeout: 3))
        XCTAssertFalse(loading.exists)
    }

    func testReaderTrustSettingsExposePolicyAndFileImport() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )
        ui.tapButton(identifier: "wallet.settingsButton", fallbackLabel: "Settings")
        ui.tapElement(identifier: "wallet.settingsProximityPresentation")
        ui.assertExists(identifier: "wallet.settingsReaderAuthentication")
        ui.tapElement(identifier: "wallet.settingsReaderAuthentication")

        let allowUntrusted = app.descendants(matching: .any)[
            "wallet.settingsReaderPolicyAllowUntrusted"
        ]
        let requireTrusted = app.descendants(matching: .any)[
            "wallet.settingsReaderPolicyRequireTrusted"
        ]
        XCTAssertTrue(allowUntrusted.waitForExistence(timeout: 10))
        XCTAssertTrue(requireTrusted.waitForExistence(timeout: 10))
        XCTAssertTrue(
            app.buttons["wallet.settingsReaderTrustImport"].waitForExistence(timeout: 10)
        )

        allowUntrusted.tap()
        XCTAssertTrue(allowUntrusted.isSelected)
        XCTAssertEqual(allowUntrusted.value as? String, "Selected")
        requireTrusted.tap()
        XCTAssertTrue(requireTrusted.isSelected)
        XCTAssertEqual(requireTrusted.value as? String, "Selected")
        XCTAssertEqual(allowUntrusted.value as? String, "Not selected")

        // List creates off-screen rows lazily; the strict-policy warning can push Reset below the viewport.
        ui.assertExists(identifier: "wallet.settingsReaderTrustReset")
        ui.tapButton(
            identifier: "wallet.settingsReaderTrustReset",
            fallbackLabel: "Reset reader trust"
        )
        let resetConfirmation = app.buttons["wallet.readerTrustResetConfirm"].firstMatch
        let resetReady = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == true AND hittable == true"),
            object: resetConfirmation
        )
        XCTAssertEqual(XCTWaiter.wait(for: [resetReady], timeout: 10), .completed)
        resetConfirmation.tap()
        for _ in 0..<4 where !allowUntrusted.isHittable { app.swipeDown() }
        let resetApplied = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "value == %@", "Selected"),
            object: allowUntrusted
        )
        XCTAssertEqual(XCTWaiter.wait(for: [resetApplied], timeout: 10), .completed)
        XCTAssertEqual(allowUntrusted.value as? String, "Selected")
        XCTAssertFalse(app.buttons["wallet.settingsReaderTrustReset"].exists)
    }

    func testSettingsExposeAllProximityTransportProfiles() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )
        ui.tapButton(identifier: "wallet.settingsButton", fallbackLabel: "Settings")
        XCTAssertFalse(app.buttons["wallet.scanButton"].isHittable)
        let root = XCTAttachment(screenshot: app.screenshot())
        root.name = "settings-root"
        root.lifetime = .keepAlways
        add(root)
        ui.assertExists(identifier: "wallet.settingsProximityPresentation")
        ui.tapElement(identifier: "wallet.settingsProximityPresentation")
        app.buttons["wallet.settingsConnectionMethod"].tap()
        ui.assertExists(identifier: "wallet.settingsProximityDefault")
        ui.assertExists(identifier: "wallet.settingsProximityNfcV2Hybrid")
        ui.assertExists(identifier: "wallet.settingsProximityNfcV2Direct")

        ui.tapElement(identifier: "wallet.settingsProximityNfcV2Direct")
        XCTAssertEqual(
            app.buttons["wallet.settingsProximityNfcV2Direct"].value as? String,
            "Selected"
        )

        ui.tapElement(identifier: "wallet.settingsProximityDefault")
        XCTAssertEqual(
            app.buttons["wallet.settingsProximityDefault"].value as? String,
            "Selected"
        )
    }

    func testSettingsReturnsToOriginatingFlowAndLocksWallet() {
        continueAfterFailure = false
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])
        ui.openWalletLink("openid-credential-offer://mock")
        ui.tapButton(identifier: "wallet.settingsButton", fallbackLabel: "Settings")
        XCTAssertFalse(app.buttons["wallet.flowBack"].isHittable)
        ui.tapElement(identifier: "wallet.settingsProximityPresentation")
        ui.tapElement(identifier: "wallet.settingsReaderAuthentication")
        XCTAssertFalse(app.buttons["wallet.settingsBack"].exists)
        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(app.buttons["wallet.settingsConnectionMethod"].waitForExistence(timeout: 5))
        app.navigationBars.buttons.firstMatch.tap()
        ui.tapElement(identifier: "wallet.settingsBack")
        ui.assertExists(identifier: "wallet.receiveTabContent")
        ui.tapButton(identifier: "wallet.settingsButton", fallbackLabel: "Settings")
        ui.tapButton(identifier: "wallet.settingsLock", fallbackLabel: "Lock wallet")
        XCTAssertTrue(app.secureTextFields["wallet.pinInput"].waitForExistence(timeout: 10))
    }

    func testProximityPresentationCanBeDismissedAndStartedAgain() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )
        receiveMockCredential(app: app, ui: ui)
        ui.returnToWallet()

        let proximityScreen = app.descendants(matching: .any)["wallet.proximityScreen"]
        for _ in 0..<2 {
            ui.tapButton(identifier: "wallet.proximityStartButton", fallbackLabel: "Present to nearby reader")
            XCTAssertTrue(proximityScreen.waitForExistence(timeout: 10))
            XCTAssertTrue(app.buttons["wallet.settingsButton"].exists)
            ui.tapButton(identifier: "wallet.proximityDoneButton", fallbackLabel: "Done")

            let dismissed = XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "exists == false"),
                object: proximityScreen
            )
            XCTAssertEqual(XCTWaiter.wait(for: [dismissed], timeout: 10), .completed)
            ui.returnToWallet()
        }
    }

    func testStatusBannerPrecedesContentAcrossFlows() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10), "Wallet ready")
        let status = app.descendants(matching: .any)["wallet.status"]
        XCTAssertLessThan(status.frame.minY, app.staticTexts["No credentials yet"].frame.minY)
        ui.openWalletLink("openid-credential-offer://mock")
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10), "Review credential offer")
        ui.assertExists(identifier: "wallet.offerCredentialsSection")
        XCTAssertLessThan(status.frame.minY, app.staticTexts["wallet.offerCredentialsSection"].frame.minY)
        ui.openWalletLink("openid4vp://mock")
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10), "Review presentation request")
        ui.assertExists(identifier: "wallet.presentTabContent")
        XCTAssertTrue(app.buttons["wallet.presentationRejectButton"].isEnabled)
    }

    func testWalletHomeOffersScanAndNearbySharing() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])
        XCTAssertTrue(app.buttons["wallet.scanButton"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["wallet.proximityStartButton"].isHittable)
        XCTAssertEqual(app.buttons["wallet.proximityStartButton"].label, "Share nearby")
        XCTAssertFalse(app.tabBars.firstMatch.exists)
        ui.openScanner()
        XCTAssertFalse(app.buttons["wallet.scanContinue"].isEnabled)
        ui.returnToWallet()
        XCTAssertTrue(app.buttons["wallet.proximityStartButton"].isHittable)
    }

    func testDeepLinksRouteToReceiveAndPresentFlows() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        let offerUrl = "openid-credential-offer://mock"
        ui.openDeepLink(offerUrl)
        ui.assertExists(identifier: "wallet.receiveTabContent")
        XCTAssertTrue(
            ui.waitForTextInputValue(
                identifier: "wallet.offerInput",
                fallbackLabel: "Credential offer URL",
                value: offerUrl,
                timeout: 10
            )
        )
        XCTAssertTrue(app.buttons["wallet.receiveButton"].isEnabled)
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        XCTAssertTrue(app.staticTexts["Example Issuer"].waitForExistence(timeout: 10))
        ui.assertExists(identifier: "wallet.offerCredentialsSection")
        XCTAssertTrue(app.staticTexts["Example"].waitForExistence(timeout: 10))
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")

        let presentationUrl = "openid4vp://mock"
        ui.openDeepLink(presentationUrl)
        ui.assertExists(identifier: "wallet.presentTabContent")
        XCTAssertTrue(
            ui.waitForTextInputValue(
                identifier: "wallet.presentationInput",
                fallbackLabel: "OpenID4VP request URL",
                value: presentationUrl,
                timeout: 10
            )
        )
    }

    func testTransactionCodeOfferCanBeDeclinedWithoutCode() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: [
            "E2E_MOCK_WALLET": "1",
            "E2E_MOCK_TX_CODE_REQUIRED": "1",
        ])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.openWalletLink("openid-credential-offer://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        XCTAssertTrue(app.secureTextFields["wallet.txCodeInput"].waitForExistence(timeout: 10))
        let transactionCodeSection = app.staticTexts["wallet.offerTransactionCodeSection"]
        XCTAssertTrue(transactionCodeSection.waitForExistence(timeout: 10))
        XCTAssertLessThan(
            app.staticTexts["wallet.status"].frame.minY,
            transactionCodeSection.frame.minY,
            "Receive status should precede the offer review"
        )

        let accept = app.buttons["Accept"]
        let decline = app.buttons["Decline"]
        XCTAssertTrue(accept.waitForExistence(timeout: 10))
        XCTAssertFalse(accept.isEnabled)
        XCTAssertTrue(decline.waitForExistence(timeout: 10))
        XCTAssertTrue(decline.isEnabled)
        decline.tap()

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Credential offer declined", "Receive failed"], timeout: 10),
            "Credential offer declined"
        )
        let declinedOfferInput = ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL")
        XCTAssertTrue(declinedOfferInput.waitForExistence(timeout: 10))
        XCTAssertTrue(declinedOfferInput.isEnabled)
        XCTAssertTrue(["", "Credential offer URL"].contains(declinedOfferInput.value as? String))
        XCTAssertFalse(app.buttons["wallet.receiveButton"].isEnabled)
        XCTAssertFalse(app.buttons["wallet.receiveNewButton"].exists)
    }

    func testPresentationDeclineSendsProtocolRejection() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10), "Wallet ready")

        ui.openWalletLink("openid-credential-offer://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10), "Received 1 credential(s)")

        ui.openWalletLink("openid4vp://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )

        ui.tapButton(identifier: "wallet.presentationRejectButton", fallbackLabel: "Reject")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Presentation rejected", "Reject failed"], timeout: 10),
            "Presentation rejected"
        )
        XCTAssertFalse(app.buttons["wallet.presentationRejectButton"].exists)
        XCTAssertFalse(app.buttons["wallet.presentationNewButton"].exists)
        let rejectedPresentationInput = ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL")
        XCTAssertTrue(rejectedPresentationInput.waitForExistence(timeout: 10))
        XCTAssertTrue(rejectedPresentationInput.isEnabled)
        XCTAssertTrue(["", "OpenID4VP request URL"].contains(rejectedPresentationInput.value as? String))
        XCTAssertFalse(app.buttons["wallet.presentButton"].isEnabled)
    }

    func testOfferShowsCredentialWithoutUnissuedClaims() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: [
            "E2E_MOCK_WALLET": "1",
            "E2E_MOCK_MDOC_METADATA": "1",
        ])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.openWalletLink("openid-credential-offer://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )

        ui.assertExists(identifier: "wallet.offerCredentialsSection")
        XCTAssertTrue(app.staticTexts["Example"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["wallet.offerSupportedClaims"].exists)
        XCTAssertFalse(app.staticTexts["mso_mdoc"].exists)
        XCTAssertFalse(app.staticTexts["18 or older"].exists)
    }

    func testRequestCanBeReviewedAndDeclinedWithoutStoredCredentials() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])
        ui.openWalletLink("openid4vp://mock")
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10), "Review presentation request")
        XCTAssertTrue(app.buttons["wallet.presentationRejectButton"].isEnabled)
        XCTAssertFalse(app.buttons["wallet.presentationSubmitButton"].isEnabled)
        ui.tapButton(identifier: "wallet.presentationRejectButton", fallbackLabel: "Decline")
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Presentation rejected", "Reject failed"], timeout: 10), "Presentation rejected")
    }

    func testCredentialOfferDeepLinksResetReceiveDetailStackWhenUrlIsUnchanged() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        let offerUrl = "openid-credential-offer://mock"
        ui.openDeepLink(offerUrl)
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
        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.descendants(matching: .any)["wallet.credentialDetailsScreen"].waitForExistence(timeout: 10))

        ui.openDeepLink(offerUrl)
        ui.assertExists(identifier: "wallet.receiveTabContent")
        XCTAssertFalse(app.descendants(matching: .any)["wallet.credentialDetailsScreen"].exists)
        XCTAssertTrue(
            ui.waitForTextInputValue(
                identifier: "wallet.offerInput",
                fallbackLabel: "Credential offer URL",
                value: offerUrl,
                timeout: 10
            )
        )
        XCTAssertTrue(app.buttons["wallet.receiveButton"].isEnabled)
    }

    func testPresentationDeepLinksResetPresentDetailStackWhenUrlIsUnchanged() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.openWalletLink("openid-credential-offer://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )

        let presentationUrl = "openid4vp://mock"
        ui.openWalletLink(presentationUrl)
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )
        ui.tapElement(identifierPrefix: "wallet.presentationClaimsToggle.")
        XCTAssertTrue(app.staticTexts["Requested disclosures"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.descendants(matching: .any)["wallet.credentialDetailsScreen"].exists)

        ui.openDeepLink(presentationUrl)
        ui.assertExists(identifier: "wallet.presentTabContent")
        XCTAssertFalse(app.staticTexts["Requested disclosures"].exists)
        XCTAssertTrue(
            ui.waitForTextInputValue(
                identifier: "wallet.presentationInput",
                fallbackLabel: "OpenID4VP request URL",
                value: presentationUrl,
                timeout: 10
            )
        )
    }

    func testPresentationDisclosureImagesRenderAsImages() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.openWalletLink("openid-credential-offer://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )

        ui.openWalletLink("openid4vp://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )

        XCTAssertFalse(app.staticTexts["Requested disclosures"].exists)
        app.swipeUp()
        ui.tapElement(identifierPrefix: "wallet.presentationClaimsToggle.")
        XCTAssertTrue(app.staticTexts["Requested disclosures"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Portrait"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["$.portrait"].exists)
        ui.assertExists(identifierPrefix: "wallet.claimImage.", timeout: 10)
        ui.tapElement(identifierPrefix: "wallet.claimImage.")
        ui.assertExists(identifierPrefix: "wallet.claimImageViewer.", timeout: 10)
        XCTAssertTrue(app.images["Full-screen credential image"].waitForExistence(timeout: 10))
        ui.tapElement(identifierPrefix: "wallet.claimImageViewerClose.")
        XCTAssertFalse(app.images["Full-screen credential image"].waitForExistence(timeout: 1))
        XCTAssertTrue(app.staticTexts["Requested disclosures"].waitForExistence(timeout: 10))
        ui.assertExists(identifierPrefix: "wallet.claimImage.", timeout: 10)
        XCTAssertFalse(app.descendants(matching: .any)["wallet.credentialDetailsScreen"].exists)
    }

    func testStoredCredentialDetailsCloseBeforeOpeningScanner() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])
        receiveMockCredential(app: app, ui: ui)
        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        ui.assertExists(identifier: "wallet.claim.given_name")
        ui.openScanner()
        XCTAssertFalse(app.descendants(matching: .any)["wallet.credentialDetailsScreen"].exists)
        ui.returnToWallet()
        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        ui.assertExists(identifier: "wallet.claim.given_name")
    }

    func testReceiveAndPresentDisableUrlControlsWhileLoading() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: [
            "E2E_MOCK_WALLET": "1",
            "E2E_MOCK_WALLET_DELAY_MS": "1500",
        ])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.openWalletLink("openid-credential-offer://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Resolving credential offer", "Receive failed"], timeout: 10),
            "Resolving credential offer..."
        )
        XCTAssertFalse(ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL").isEnabled)
        XCTAssertFalse(app.buttons["wallet.receiveButton"].isEnabled)
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Receiving credential", "Receive failed"], timeout: 10),
            "Receiving credential..."
        )
        XCTAssertFalse(app.buttons["wallet.offerAcceptButton"].isEnabled)
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )

        ui.openWalletLink("openid4vp://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Resolving presentation", "Preview failed"], timeout: 10),
            "Resolving presentation..."
        )
        XCTAssertFalse(ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL").isEnabled)
        XCTAssertFalse(app.buttons["wallet.presentButton"].isEnabled)
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )
    }

    func testCredentialImageOpensAndClosesFullScreenViewer() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.openWalletLink("openid-credential-offer://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )

        let card = app.buttons["wallet.credentialCard.cred-1"]
        XCTAssertTrue(card.waitForExistence(timeout: 10))
        XCTAssertTrue(card.isHittable)
        card.tap()
        ui.assertExists(identifier: "wallet.credentialDetailsScreen", timeout: 10)
        ui.assertExists(identifier: ui.claimImageIdentifier(path: "portrait"), timeout: 10)
        ui.assertExists(identifier: ui.claimImageIdentifier(path: "signature_usual_mark"), timeout: 10)
        let artifactImageIdentifier = ui.claimImageIdentifier(path: "verification_artifact")
        ui.assertExists(identifier: artifactImageIdentifier, timeout: 10)
        ui.tapElement(identifier: artifactImageIdentifier)
        ui.assertExists(identifierPrefix: "wallet.claimImageViewer.", timeout: 10)
        XCTAssertTrue(app.images["Full-screen credential image"].waitForExistence(timeout: 10))
        ui.tapElement(identifierPrefix: "wallet.claimImageViewerClose.")
        XCTAssertFalse(app.images["Full-screen credential image"].waitForExistence(timeout: 1))
        ui.assertExists(identifier: "wallet.credentialDetailsScreen", timeout: 10)
        ui.assertExists(identifier: artifactImageIdentifier, timeout: 10)
    }

    func testScanReceiveDetailsAndShareUsesMockWallet() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.returnToWallet()
        XCTAssertTrue(app.staticTexts["No credentials yet"].waitForExistence(timeout: 10))

        ui.openWalletLink("openid-credential-offer://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "issuance-details-ExampleCredential", fallbackLabel: "Credential information")
        XCTAssertTrue(app.staticTexts["Values have not been received yet."].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["The issuer has not supplied claim definitions."].exists)
        ui.tapButton(identifier: "wallet-detail-close", fallbackLabel: "Close")
        XCTAssertEqual(app.switches["issuance-select-ExampleCredential"].value as? String, "1")
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )
        XCTAssertFalse(app.tabBars.firstMatch.exists)
        XCTAssertFalse(app.buttons["wallet.receiveNewButton"].exists)
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Example Credential"].waitForExistence(timeout: 10))
        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Example Credential"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Issuer: Example Issuer"].waitForExistence(timeout: 10))
        ui.assertExists(identifier: "wallet.claim.given_name")
        ui.assertExists(identifierPrefix: "wallet.claimImage.", timeout: 10)
        ui.assertExists(identifier: "wallet.claim.valid_to")
        XCTAssertTrue(app.staticTexts["2026-06-17"].exists)
        ui.tapButton(identifier: "credential-technical-details", fallbackLabel: "Technical details")
        ui.assertExists(identifier: "wallet.claim.system_format")
        XCTAssertTrue(app.staticTexts["jwt_vc_json"].waitForExistence(timeout: 10))

        ui.openScanner()
        let resetOfferInput = ui.textInput(identifier: "wallet.scanInput", fallbackLabel: "Credential offer or request")
        XCTAssertTrue(resetOfferInput.waitForExistence(timeout: 10))
        XCTAssertTrue(resetOfferInput.isEnabled)
        XCTAssertTrue(["", "Credential offer or request"].contains(resetOfferInput.value as? String))
        XCTAssertFalse(app.buttons["wallet.scanContinue"].isEnabled)

        ui.returnToWallet()
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Example Credential"].waitForExistence(timeout: 10))

        ui.openWalletLink("openid4vp://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )
        XCTAssertFalse(ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL").exists)
        XCTAssertTrue(app.staticTexts["Example Verifier"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["wallet.presentationRequesterDetailsToggle"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["Required"].exists)
        XCTAssertFalse(app.staticTexts["ECDH-ES"].exists)
        XCTAssertTrue(app.staticTexts["Payment Authorization"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Amount"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["129.90"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Currency"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["EUR"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Payee"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Example Merchant"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["wallet.verifierTechnicalDetailsToggle"].exists)
        ui.assertExists(identifierPrefix: "wallet.presentationCredential.")
        assertPresentationActionsFollowReviewContent(app: app)

        ui.tapElement(identifierPrefix: "wallet.presentationClaimsToggle.")
        XCTAssertTrue(app.otherElements["wallet.presentationClaimsDialog"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Example Credential"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Given name"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.descendants(matching: .any)["wallet.credentialDetailsScreen"].exists)
        ui.tapButton(identifier: "wallet.presentationClaimsClose", fallbackLabel: "Close")

        ui.tapButton(identifier: "wallet.presentationSubmitButton", fallbackLabel: "Share")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Presentation sent", "Present failed"], timeout: 10),
            "Presentation sent"
        )
        ui.returnToWallet()
        XCTAssertFalse(app.staticTexts["Presentation sent"].isHittable)
        ui.openScanner()
        // Completed consent and feedback must not carry into a new flow.
        XCTAssertFalse(app.staticTexts["Presentation sent"].exists)
        XCTAssertFalse(app.buttons["wallet.presentationSubmitButton"].exists)
        XCTAssertFalse(app.buttons["wallet.presentationRejectButton"].exists)
        XCTAssertFalse(app.buttons["wallet.presentationNewButton"].exists)
        XCTAssertFalse(app.staticTexts["Example Verifier"].exists)
        let resetPresentationInput = ui.textInput(identifier: "wallet.scanInput", fallbackLabel: "Credential offer or request")
        XCTAssertTrue(resetPresentationInput.waitForExistence(timeout: 10))
        XCTAssertTrue(resetPresentationInput.isEnabled)
        XCTAssertTrue(["", "Credential offer or request"].contains(resetPresentationInput.value as? String))
        XCTAssertFalse(app.buttons["wallet.scanContinue"].isEnabled)
    }

    func testPresentationShowsUnencryptedResponseState() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: [
            "E2E_MOCK_WALLET": "1",
            "E2E_MOCK_UNENCRYPTED_RESPONSE": "1",
        ])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )
        receiveMockCredential(app: app, ui: ui)
        ui.openWalletLink("openid4vp://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )

        XCTAssertFalse(app.staticTexts["Not requested"].exists)
        XCTAssertFalse(app.staticTexts["Key management algorithm"].exists)
        XCTAssertFalse(app.staticTexts["Verifier key thumbprint"].exists)
    }

    func testPresentationWithoutVerifierDisplayKeepsClientIDInTechnicalDetails() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: [
            "E2E_MOCK_WALLET": "1",
            "E2E_MOCK_DID_VERIFIER": "1",
        ])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.openWalletLink("openid-credential-offer://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )

        ui.openWalletLink("openid4vp://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )

        XCTAssertTrue(app.descendants(matching: .any)["wallet.presentationVerifierSection"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts[Self.didClientID].exists)
        ui.tapButton(identifier: "wallet.presentationRequesterDetailsToggle", fallbackLabel: "Show Verifier details")
        XCTAssertTrue(app.staticTexts[Self.didClientID].waitForExistence(timeout: 10))
    }

    func testNewOfferDoesNotExposePreviousCredentialValues() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])
        receiveMockCredential(app: app, ui: ui)
        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        ui.assertExists(identifier: "wallet.claim.given_name")
        ui.openWalletLink("openid-credential-offer://mock")
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10), "Review credential offer")
        XCTAssertFalse(app.descendants(matching: .any)["wallet.credentialDetailsScreen"].exists)
        XCTAssertFalse(app.descendants(matching: .any)["wallet.claim.given_name"].exists)
        ui.returnToWallet()
        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        ui.assertExists(identifier: "wallet.claim.given_name")
    }

    func testSharingDetailsDoNotCarryIntoANewRequest() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.openWalletLink("openid-credential-offer://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )

        ui.openWalletLink("openid4vp://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )

        ui.tapElement(identifierPrefix: "wallet.presentationClaimsToggle.")
        XCTAssertTrue(app.staticTexts["Requested disclosures"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.descendants(matching: .any)["wallet.credentialDetailsScreen"].exists)
        ui.tapButton(identifier: "wallet.presentationClaimsClose", fallbackLabel: "Close")

        ui.returnToWallet()
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        XCTAssertFalse(app.descendants(matching: .any)["wallet.credentialDetailsScreen"].exists)

        ui.openWalletLink("openid4vp://mock")
        XCTAssertTrue(app.staticTexts["Example Verifier"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.otherElements["wallet.presentationClaimsDialog"].exists)
        XCTAssertFalse(app.descendants(matching: .any)["wallet.credentialDetailsScreen"].exists)
    }

    func testPresentationDetailsResolveDuplicateCredentialOptionsIndependently() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: [
            "E2E_MOCK_WALLET": "1",
            "E2E_MOCK_DUPLICATE_PRESENTATION_OPTIONS": "1",
        ])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.openWalletLink("openid-credential-offer://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )

        ui.openWalletLink("openid4vp://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )

        XCTAssertFalse(app.switches["wallet.presentationDisclosureToggle.8:identity6:cred-112:$.given_name"].exists)
        XCTAssertFalse(app.switches["wallet.presentationDisclosureToggle.3:age6:cred-113:$.age_over_18"].exists)

        ui.tapElement(identifierPrefix: "wallet.presentationClaimsToggle.3:age6:cred-1")
        XCTAssertTrue(app.staticTexts["Age disclosure"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Over 18"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["Identity disclosure"].exists)
        XCTAssertFalse(app.descendants(matching: .any)["wallet.credentialDetailsScreen"].exists)
    }

    private func assertPresentationActionsFollowReviewContent(app: XCUIApplication) {
        let verifier = app.descendants(matching: .any)["wallet.presentationVerifierSection"].firstMatch
        let credential = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH %@", "wallet.presentationCredential."))
            .firstMatch
        let share = app.buttons["wallet.presentationSubmitButton"]

        XCTAssertTrue(verifier.waitForExistence(timeout: 10), "Verifier name is missing")
        XCTAssertTrue(credential.waitForExistence(timeout: 10), "Shared credential card is missing")
        XCTAssertTrue(share.waitForExistence(timeout: 10), "Share action is missing")
        XCTAssertFalse(app.descendants(matching: .any)["wallet.presentationResponseProtectionSection"].firstMatch.exists)
        XCTAssertFalse(app.descendants(matching: .any)["wallet.presentationTechnicalDetailsSection"].firstMatch.exists)
        XCTAssertLessThan(
            verifier.frame.minY,
            credential.frame.minY,
            "Credential should follow verifier name"
        )
        XCTAssertLessThan(
            credential.frame.minY,
            share.frame.minY,
            "Share action should be below shared credential details so the credential is reviewed before consent"
        )
    }

    private func receiveMockCredential(app: XCUIApplication, ui: WalletE2EUI) {
        ui.openWalletLink("openid-credential-offer://mock")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )
    }
}

@MainActor
final class WalletIdentitySetupUITests: XCTestCase {
    func testBackupResetRestoreAndRestartKeepTheOriginalDid() {
        continueAfterFailure = false
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        let environment = ["E2E_WALLET_ID": "recovery-ui-\(UUID().uuidString)"]
        ui.launch(environment: environment, initializeSigningIdentity: false)
        let next = app.buttons["wallet.keySetupContinue"]
        XCTAssertTrue(next.waitForExistence(timeout: 30))
        ui.tapButton(identifier: "wallet.keySetupEdit.recovery", fallbackLabel: "Recovery")
        ui.tapButton(identifier: "wallet.keySetupChoice.recovery.1", fallbackLabel: "Back up with iCloud Keychain")
        next.tap()
        ui.tapButton(identifier: "wallet.keySetupEdit.storage", fallbackLabel: "Key storage")
        XCTAssertTrue(app.staticTexts["Key storage"].firstMatch.waitForExistence(timeout: 10))
        XCTAssertEqual(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Secure Enclave")).count, 0)
        capture("recoverable-key-storage", app: app)
        next.tap()
        XCTAssertTrue(app.buttons["wallet.keySetupEdit.approval"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["wallet.settingsButton"].exists)
        next.tap()
        ui.tapButton(identifier: "wallet.settingsButton", fallbackLabel: "Settings")
        app.buttons["wallet.settingsTechnicalDetails"].tap()
        let did = app.staticTexts["wallet.settingsDid"].label
        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(did.hasPrefix("did:jwk:"))
        app.buttons["wallet.settingsSigningKey"].tap()
        XCTAssertTrue(app.staticTexts["Saved on this device. Delivery to another device is not confirmed."].waitForExistence(timeout: 10))
        capture("backup-receipt", app: app)
        app.navigationBars.buttons.firstMatch.tap()
        for _ in 0..<8 where !app.buttons["wallet.settingsReset"].isHittable { app.swipeUp() }
        ui.tapButton(identifier: "wallet.settingsReset", fallbackLabel: "Reset wallet")
        capture("reset-warning", app: app)
        app.buttons["Cancel"].tap()
        XCTAssertTrue(app.buttons["wallet.settingsReset"].exists)
        ui.tapButton(identifier: "wallet.settingsReset", fallbackLabel: "Reset wallet")
        app.alerts.buttons["wallet.settingsResetConfirm"].firstMatch.tap()
        XCTAssertTrue(app.descendants(matching: .any)["wallet.pinInput"].waitForExistence(timeout: 30))
        app.terminate()
        ui.launch(environment: environment, initializeSigningIdentity: false)
        XCTAssertTrue(next.waitForExistence(timeout: 30))
        ui.tapButton(identifier: "wallet.keySetupEdit.recovery", fallbackLabel: "Recovery")
        let restore = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", SHA256.hash(data: Data(did.utf8)).prefix(6).map { String(format: "%02x", $0) }.joined())).firstMatch
        for _ in 0..<20 {
            if restore.exists && restore.frame.minY < next.frame.minY - 120 && restore.frame.maxY > 240 { break }
            app.swipeUp()
        }
        XCTAssertTrue(restore.isHittable, "Original recovery record must be selectable")
        // Scroll to the matching key and wait for the list to settle before selecting it.
        var previousFrame = CGRect.zero
        let settled = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            let frame = restore.frame
            defer { previousFrame = frame }
            return frame == previousFrame
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [settled], timeout: 5), .completed)
        let visibleTop = max(restore.frame.minY, 180)
        let visibleBottom = min(restore.frame.maxY, next.frame.minY - 20)
        XCTAssertGreaterThan(visibleBottom, visibleTop)
        app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(
            dx: restore.frame.midX, dy: (visibleTop + visibleBottom) / 2
        )).tap()
        XCTAssertTrue(restore.isSelected)
        capture("selected-recovery-record", app: app)
        next.tap()
        XCTAssertTrue(app.buttons["wallet.keySetupEdit.storage"].waitForExistence(timeout: 10))
        XCTAssertEqual(next.label, "Restore signing key")
        next.tap()
        ui.tapButton(identifier: "wallet.settingsButton", fallbackLabel: "Settings")
        app.buttons["wallet.settingsTechnicalDetails"].tap()
        XCTAssertEqual(app.staticTexts["wallet.settingsDid"].label, did)
        app.navigationBars.buttons.firstMatch.tap()
        capture("restored-key", app: app)
        app.terminate()
        ui.launch(environment: environment)
        ui.tapButton(identifier: "wallet.settingsButton", fallbackLabel: "Settings")
        app.buttons["wallet.settingsTechnicalDetails"].tap()
        XCTAssertEqual(app.staticTexts["wallet.settingsDid"].label, did)
        app.navigationBars.buttons.firstMatch.tap()
        app.buttons["wallet.settingsSigningKey"].tap()
        app.buttons["Delete key backup"].tap()
        capture("delete-recovery-warning", app: app)
        app.buttons["Cancel"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Delete key backup"].exists)
        app.buttons["Delete key backup"].tap()
        app.alerts.buttons["Delete key backup"].firstMatch.tap()
        let removed = app.staticTexts["Backup deletion requested. Keys already restored on other devices are not deleted."]
        XCTAssertTrue(removed.waitForExistence(timeout: 20))
        app.navigationBars.buttons.firstMatch.tap()
        for _ in 0..<8 where !app.buttons["wallet.settingsReset"].isHittable { app.swipeUp() }
        ui.tapButton(identifier: "wallet.settingsReset", fallbackLabel: "Reset wallet")
        app.alerts.buttons["wallet.settingsResetConfirm"].firstMatch.tap()
        XCTAssertTrue(app.descendants(matching: .any)["wallet.pinInput"].waitForExistence(timeout: 30))
    }

    private func capture(_ name: String, app: XCUIApplication) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "wal749-\(name)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testSigningKeyCustomizationReturnsToSummaryWithoutCreatingAKey() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(initializeSigningIdentity: false)
        let create = app.buttons["wallet.keySetupContinue"]
        XCTAssertTrue(create.waitForExistence(timeout: 20))
        for setting in ["recovery", "storage", "approval"] {
            ui.tapButton(identifier: "wallet.keySetupEdit.\(setting)", fallbackLabel: setting)
            XCTAssertEqual(create.label, "Done")
            XCTAssertFalse(app.buttons["wallet.settingsButton"].exists)
            create.tap()
            XCTAssertTrue(app.buttons["wallet.keySetupEdit.\(setting)"].waitForExistence(timeout: 10))
            XCTAssertEqual(create.label, "Create signing key")
        }
        XCTAssertFalse(app.buttons["wallet.settingsButton"].exists)
    }

    func testNativeIdentitySetupAndProtectionDetails() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(initializeSigningIdentity: false)
        let create = app.buttons["wallet.keySetupContinue"]
        XCTAssertTrue(create.waitForExistence(timeout: 20))
        let setup = XCTAttachment(screenshot: app.screenshot())
        setup.name = "wal749-native-ios-identity-setup"
        setup.lifetime = .keepAlways
        add(setup)
        XCTAssertTrue(app.buttons["wallet.keySetupEdit.recovery"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["wallet.keySetupEdit.storage"].exists)
        XCTAssertTrue(app.buttons["wallet.keySetupEdit.approval"].exists)
        XCTAssertEqual(create.label, "Create signing key")
        create.tap()
        XCTAssertTrue(app.buttons["wallet.settingsButton"].waitForExistence(timeout: 20))
        app.buttons["wallet.settingsButton"].tap()
        let details = app.buttons["wallet.settingsSigningKey"]
        XCTAssertTrue(details.waitForExistence(timeout: 10))
        details.tap()
        XCTAssertTrue(app.staticTexts["No key backup submitted."].waitForExistence(timeout: 10))
        let active = XCTAttachment(screenshot: app.screenshot())
        active.name = "wal749-native-ios-identity-details"
        active.lifetime = .keepAlways
        add(active)
        app.navigationBars.buttons.firstMatch.tap()
        capture("settings-root", app: app)
        ui.tapButton(identifier: "wallet.settingsTechnicalDetails", fallbackLabel: "Technical details")
        let did = app.staticTexts["wallet.settingsDid"]
        XCTAssertTrue(did.waitForExistence(timeout: 10))
        XCTAssertTrue(did.label.hasPrefix("did:jwk:"))
        ui.tapButton(identifier: "wallet.settingsDidCopy", fallbackLabel: "Copy wallet DID")
        XCTAssertTrue(app.staticTexts["Copied"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.staticTexts["wallet.settingsPublicJwk"].exists)
        capture("technical-copy", app: app)
        app.buttons["Show public key"].tap()
        XCTAssertTrue(app.staticTexts["wallet.settingsPublicJwk"].waitForExistence(timeout: 3))
        capture("technical-expanded", app: app)
        app.navigationBars.buttons.firstMatch.tap()
        ui.tapButton(identifier: "wallet.settingsDigitalCredentialsApi", fallbackLabel: "Digital Credentials API")
        XCTAssertTrue(app.descendants(matching: .any)["wallet.settingsShowDcApiPreview"].waitForExistence(timeout: 5))
        capture("digital-credentials-api", app: app)
        app.navigationBars.buttons.firstMatch.tap()
        ui.tapButton(identifier: "wallet.settingsProximityPresentation", fallbackLabel: "Nearby sharing")
        capture("nearby-sharing", app: app)
        ui.tapButton(identifier: "wallet.settingsReaderAuthentication", fallbackLabel: "Reader authentication")
        XCTAssertTrue(app.descendants(matching: .any)["wallet.settingsReaderPolicyAllowUntrusted"].waitForExistence(timeout: 5))
        capture("reader-authentication", app: app)
        app.navigationBars.buttons.firstMatch.tap()
        ui.tapButton(identifier: "wallet.settingsConnectionMethod", fallbackLabel: "Connection method")
        capture("connection-method", app: app)
        #if targetEnvironment(simulator)
        // A physical device may have rotation lock enabled. Exercise layout
        // rotation on the simulator; the device run verifies the real key flow.
        XCUIDevice.shared.orientation = .landscapeLeft
        defer { XCUIDevice.shared.orientation = .portrait }
        let landscape = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            app.frame.width > app.frame.height
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [landscape], timeout: 5), .completed)
        XCTAssertTrue(app.descendants(matching: .any)["wallet.settingsProximityDefault"].waitForExistence(timeout: 5))
        capture("connection-landscape", app: app)
        #endif

    }
}
