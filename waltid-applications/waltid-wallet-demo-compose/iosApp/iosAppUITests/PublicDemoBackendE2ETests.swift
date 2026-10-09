import CryptoKit
import XCTest

/// End-to-end UI tests for the Compose wallet demo app against the public demo stack.
///
/// Tests the full user flow: launch app, receive credential, present credential,
/// and keep received credentials across app restart.
@MainActor
final class PublicDemoBackendE2ETests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }


    func testSettingsCopyControlsAreAccessible() throws {
        guard #available(iOS 17.0, *) else { throw XCTSkip("Accessibility audit requires iOS 17") }
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: isolatedWalletEnvironment())
        XCTAssertEqual(ui.waitUntilWalletReady(timeout: walletReadyTimeout), "Wallet ready")
        ui.tapButton(identifier: "wallet.settingsButton", fallbackLabel: "Settings")
        XCTAssertTrue(app.buttons["wallet.settingsTechnicalDetails"].waitForExistence(timeout: 10))
        try app.performAccessibilityAudit(for: [.elementDetection, .hitRegion, .sufficientElementDescription, .trait])

        ui.tapButton(identifier: "wallet.settingsTechnicalDetails", fallbackLabel: "Technical details")
        let copy = app.buttons["wallet.settingsDidCopy"]
        XCTAssertTrue(copy.waitForExistence(timeout: 10))
        XCTAssertEqual(copy.label, "Copy wallet DID")
        XCTAssertEqual(app.buttons["wallet.settingsKeyIdCopy"].label, "Copy key ID")
        XCTAssertEqual(app.buttons["wallet.settingsPublicJwkCopy"].label, "Copy public key as JWK")
        // Auditing the native tree also catches unlabeled tooltip wrappers beside labeled buttons.
        try app.performAccessibilityAudit(for: [.elementDetection, .hitRegion, .sufficientElementDescription, .trait])
        copy.tap()
        let copied = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == %@", "Wallet DID copied"), object: copy)
        XCTAssertEqual(XCTWaiter.wait(for: [copied], timeout: 5), .completed)
        XCTAssertEqual(copy.label, "Copy wallet DID")
    }

    private let backend = DemoBackend.shared

    // Timeouts (aligned with Android for cross-platform consistency)
    private let walletReadyTimeout: TimeInterval = 60
    private let credentialOperationTimeout: TimeInterval = 90
    private let presentationOperationTimeout: TimeInterval = 180
    private let verifierPollingTimeout: TimeInterval = 30

    func testNearbyConnectionOptionsReturnToTheTaskAndCloseOnce() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: isolatedWalletEnvironment())
        XCTAssertEqual(ui.waitUntilWalletReady(timeout: walletReadyTimeout), "Wallet ready")
        ui.tapButton(identifier: "wallet.proximityStartButton", fallbackLabel: "Share nearby")
        XCTAssertTrue(app.descendants(matching: .any)["wallet.proximityScreen"].waitForExistence(timeout: 10))
        ui.tapButton(identifier: "proximity-connection-options", fallbackLabel: "Connection options")
        XCTAssertTrue(app.descendants(matching: .any)["wallet.settingsProximityDefault"].waitForExistence(timeout: 10))
        captureHost("Nearby connection options")
        ui.tapButton(identifier: "wallet.settingsBack", fallbackLabel: "Back")
        XCTAssertTrue(app.descendants(matching: .any)["wallet.proximityScreen"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.textFields["wallet.presentationInput"].exists)
        captureHost("Nearby options return")
        ui.tapButton(identifier: "wallet.proximityCancelButton", fallbackLabel: "Close nearby sharing")
        let closed = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"),
            object: app.descendants(matching: .any)["wallet.proximityScreen"])
        XCTAssertEqual(XCTWaiter.wait(for: [closed], timeout: 10), .completed)
        assertHome(app)
    }

    func testScannerModesKeepDraftAndActionsAboveTheKeyboard() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: isolatedWalletEnvironment())
        XCTAssertEqual(ui.waitUntilWalletReady(timeout: walletReadyTimeout), "Wallet ready")
        ui.tapButton(identifier: "wallet.scanButton", fallbackLabel: "Scan QR code")
        let title = app.descendants(matching: .any)["wallet.screen.title"]
        XCTAssertTrue(title.waitForExistence(timeout: 10))
        XCTAssertEqual(title.label, "Scan QR code")
        let preview = app.descendants(matching: .any)["wallet.scanPreview"]
        XCTAssertTrue(preview.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertGreaterThan(preview.frame.height, 100)
        let cameraPermission = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.firstMatch
        if cameraPermission.waitForExistence(timeout: 5),
           cameraPermission.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "camera")).firstMatch.exists {
            let allow = cameraPermission.buttons["Allow"].exists
                ? cameraPermission.buttons["Allow"] : cameraPermission.buttons["OK"]
            XCTAssertTrue(allow.exists, "Camera permission prompt must have an approval action")
            allow.tap()
        }
        XCTAssertFalse(app.buttons["wallet.scanContinue"].exists)
        ui.tapButton(identifier: "wallet.scanMode", fallbackLabel: "Enter a link")
        let input = ui.textInput(identifier: "wallet.scanInput", fallbackLabel: "Credential offer or request")
        XCTAssertTrue(input.waitForExistence(timeout: 10))
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
        ui.replaceText(in: input, value: "openid4vp://fixture", dismiss: false)
        let action = app.buttons["wallet.scanContinue"]
        let paste = app.buttons["wallet.scanPaste"]
        XCTAssertTrue(action.isEnabled && action.isHittable, app.debugDescription)
        XCTAssertTrue(paste.exists && paste.isHittable, app.debugDescription)
        XCTAssertTrue(app.frame.contains(action.frame), app.debugDescription)
        XCTAssertLessThanOrEqual(action.frame.maxY, app.keyboards.firstMatch.frame.minY + 1)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "scanner-manual-keyboard-clearance"
        attachment.lifetime = .keepAlways
        add(attachment)

        app.buttons["wallet.scanMode"].tap()
        XCTAssertEqual(title.label, "Scan QR code")
        XCTAssertTrue(preview.waitForExistence(timeout: 5))
        XCTAssertFalse(input.exists)
        let keyboardHidden = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: app.keyboards.firstMatch)
        XCTAssertEqual(XCTWaiter.wait(for: [keyboardHidden], timeout: 5), .completed)
        ui.tapButton(identifier: "wallet.scanMode", fallbackLabel: "Enter a link")
        XCTAssertTrue(ui.waitForTextInputValue(identifier: "wallet.scanInput", fallbackLabel: "Credential offer or request",
            value: "openid4vp://fixture", timeout: 5))
        ui.tapButton(identifier: "wallet.flowBack", fallbackLabel: "Close scanner")
        XCTAssertTrue(app.buttons["wallet.scanButton"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.textFields["wallet.presentationInput"].exists)
    }

    /// Run explicitly on enrolled physical hardware with WALLET_SCA_OPERATOR=approve.
    func testScaPaymentWithNativeAuthorization() async throws {
        continueAfterFailure = false
        #if targetEnvironment(simulator)
        throw XCTSkip("SCA acceptance requires physical Secure Enclave and enrolled biometrics")
        #else
        guard ProcessInfo.processInfo.environment["WALLET_SCA_OPERATOR"] == "approve" else {
            throw XCTSkip("Select the operator-assisted SCA lane explicitly")
        }
        let offer = try await backend.createOffer(scenario: DemoBackend.scaPaymentScenario)
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: isolatedWalletEnvironment().merging(["WALLET_SIGNING_PROTECTION_MODE": "required"]) { _, new in new },
                  initializeSigningIdentity: false)
        let next = app.buttons["wallet.keySetupContinue"]
        XCTAssertTrue(app.buttons["wallet.keySetupEdit.Storage"].waitForExistence(timeout: 20))
        ui.tapButton(identifier: "wallet.keySetupEdit.Storage", fallbackLabel: "Key storage")
        let storage = app.staticTexts["Secure Enclave"]
        XCTAssertTrue(storage.waitForExistence(timeout: 10))
        storage.tap()
        next.tap()
        ui.tapButton(identifier: "wallet.keySetupEdit.Approval", fallbackLabel: "Signing approval")
        let approval = app.staticTexts["Current biometrics only"]
        XCTAssertTrue(approval.waitForExistence(timeout: 10))
        approval.tap()
        next.tap()
        print("SCA_OPERATOR: approve iPhone key setup and issuance prompts")
        next.tap()
        XCTAssertEqual(ui.waitUntilWalletReady(timeout: 180), "Wallet ready")

        ui.openWalletLink(offer.offerUrl)
        XCTAssertTrue(ui.waitForOfferReview(timeout: 90), app.debugDescription)
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertTrue(app.staticTexts["Saved · 1"].waitForExistence(timeout: 180), app.debugDescription)
        ui.returnToWallet()
        XCTAssertTrue(app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "wallet.credentialCard.")).firstMatch.waitForExistence(timeout: 20))

        let session = try await backend.createScaPaymentVerifierSession()
        ui.openWalletLink(session.authorizationRequestUri)
        XCTAssertTrue(ui.waitForPresentationReview(timeout: 60), app.debugDescription)
        let payment = app.descendants(matching: .any).matching(identifier: "payment-consent").firstMatch
        XCTAssertTrue(payment.waitForExistence(timeout: 60), app.debugDescription)
        for value in ["Super Store", "11.56", "EUR", "Confirm this payment", "Payee", "Payee ID", "Currency", "Amount",
                      "Check the payee and amount before confirming."] {
            let text = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", value)).firstMatch
            // Static Compose text need not expose an XCTest activation point.
            func isVisible() -> Bool {
                guard text.exists else { return false }
                let frame = text.frame
                return !frame.isEmpty && app.frame.contains(frame)
            }
            for _ in 0..<8 { if isVisible() { break }; app.swipeDown() }
            for _ in 0..<12 { if isVisible() { break }; app.swipeUp() }
            XCTAssertTrue(isVisible(), "Missing visible payment value: \(value)")
        }
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "SCA payment review before native signing"
        attachment.lifetime = .keepAlways
        add(attachment)
        print("SCA_OPERATOR: approve iPhone payment signing")
        XCTAssertTrue(app.buttons["Confirm payment"].exists, "Missing issuer affirmative action")
        XCTAssertTrue(app.buttons["Cancel payment"].exists, "Missing issuer denial action")
        ui.tapButton(identifier: "wallet.presentationSubmitButton", fallbackLabel: "Share")
        let status = ui.waitForStatus(prefixes: ["Presentation sent", "Presentation finished", "Present failed"], timeout: 180)
        XCTAssertNotNil(status)
        XCTAssertFalse(status?.hasPrefix("Present failed") ?? true, status ?? "No presentation outcome")
        try await backend.verifyScaPayment(sessionID: session.sessionID, timeoutSeconds: verifierPollingTimeout)
        print("SCA_DEVICE_E2E nativeApproved=true paymentReviewed=true verifier=SUCCESSFUL session=\(session.sessionID)")
        #endif
    }

    func testPinKeyboardOpensOnColdLaunchWithoutInput() {
        let app = XCUIApplication()
        app.launchEnvironment = isolatedWalletEnvironment()
        app.launch()
        let ui = WalletE2EUI(app: app)
        XCTAssertTrue(ui.textInput(identifier: "wallet.pinInput", fallbackLabel: "PIN").waitForExistence(timeout: 10))
        assertPinKeyboardAboveAction(app)
        let capture = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        capture.name = "pin-keyboard-on-cold-launch"; capture.lifetime = .keepAlways; add(capture)
    }

    func testPinPersistsAcrossAppRestart() throws {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        let environment = isolatedWalletEnvironment()
        app.launchEnvironment = environment.merging(["WALLET_SIGNING_PROTECTION_MODE": "disabled"]) { _, new in new }
        app.launch()
        let input = ui.textInput(identifier: "wallet.pinInput", fallbackLabel: "PIN")
        XCTAssertTrue(input.waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Step 1 of 2"].exists)
        assertPinKeyboardAboveAction(app)
        input.typeText("1234") // No tap: the screen owns focus.
        let confirmation = ui.textInput(identifier: "wallet.pinConfirmationInput", fallbackLabel: "Confirm PIN")
        XCTAssertTrue(confirmation.waitForExistence(timeout: 10))
        assertPinKeyboardAboveAction(app)
        confirmation.typeText("123")
        app.buttons["wallet.pinBackButton"].tap()
        XCTAssertTrue(input.waitForExistence(timeout: 10))
        assertPinKeyboardAboveAction(app)
        XCTAssertEqual(input.value as? String, "0 of 4 digits entered")
        input.typeText("1234")
        XCTAssertTrue(confirmation.waitForExistence(timeout: 10))
        assertPinKeyboardAboveAction(app)
        confirmation.typeText("1234")
        ui.completeKeySetupIfNeeded()
        let readyStatus = ui.waitUntilWalletReady(timeout: walletReadyTimeout)
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        app.terminate()
        app.launch()
        XCTAssertTrue(input.waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["Step 1 of 2"].exists)
        assertPinKeyboardAboveAction(app)
        input.typeText("0000")
        XCTAssertTrue(app.descendants(matching: .any)["Wrong PIN"].firstMatch.waitForExistence(timeout: 10))
        assertPinKeyboardAboveAction(app)
        input.typeText("1234")
        XCTAssertEqual(ui.waitUntilWalletReady(timeout: walletReadyTimeout), "Wallet ready")
    }

    func testWalletAccessChangesPinAndRetainsTheReplacementAcrossRestart() {
        continueAfterFailure = false
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: isolatedWalletEnvironment())
        XCTAssertEqual(ui.waitUntilWalletReady(timeout: walletReadyTimeout), "Wallet ready")
        ui.tapButton(identifier: "wallet.settingsButton", fallbackLabel: "Settings")
        ui.tapButton(identifier: "wallet.settingsWalletAccess", fallbackLabel: "Wallet access")
        ui.tapButton(identifier: "wallet.settingsChangePin", fallbackLabel: "Change PIN")
        let input = ui.textInput(identifier: "wallet.pinInput", fallbackLabel: "PIN")
        XCTAssertTrue(input.waitForExistence(timeout: 10))
        assertPinKeyboardAboveAction(app)
        input.typeText("1234")
        XCTAssertTrue(app.staticTexts["Choose a new PIN"].waitForExistence(timeout: 10))
        input.typeText("5678")
        let confirmation = ui.textInput(identifier: "wallet.pinConfirmationInput", fallbackLabel: "Confirm PIN")
        XCTAssertTrue(confirmation.waitForExistence(timeout: 10))
        assertPinKeyboardAboveAction(app)
        confirmation.typeText("5678")
        let notice = app.descendants(matching: .any)["wallet.accessNotice"].firstMatch
        let changed = notice.waitForExistence(timeout: 10)
        if !changed {
            let capture = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
            capture.name = "pin-change-failure"; capture.lifetime = .keepAlways; add(capture)
        }
        XCTAssertTrue(changed)
        XCTAssertEqual(notice.label, "PIN changed")
        app.terminate()
        app.launch()
        XCTAssertTrue(input.waitForExistence(timeout: 10))
        assertPinKeyboardAboveAction(app)
        input.typeText("1234")
        XCTAssertTrue(app.descendants(matching: .any)["Wrong PIN"].firstMatch.waitForExistence(timeout: 10))
        input.typeText("5678")
        XCTAssertEqual(ui.waitUntilWalletReady(timeout: walletReadyTimeout), "Wallet ready")
    }

    private func assertPinKeyboardAboveAction(_ app: XCUIApplication) {
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5), "PIN input must open the keyboard automatically")
        XCTAssertLessThanOrEqual(app.buttons["wallet.pinClearButton"].frame.maxY, app.keyboards.firstMatch.frame.minY,
            "The keyboard must not cover the PIN action")
    }

    func testBootstrapCreatesDid() async throws {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: isolatedWalletEnvironment())

        let readyStatus = ui.waitUntilWalletReady(timeout: walletReadyTimeout)
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        ui.tapButton(identifier: "wallet.settingsButton", fallbackLabel: "Settings")
        app.buttons["wallet.settingsTechnicalDetails"].tap()
        let didLabel = app.staticTexts["wallet.settingsDid"]
        XCTAssertTrue(didLabel.waitForExistence(timeout: 10), "Bootstrapped DID label was not exposed")
        XCTAssertTrue(didLabel.label.starts(with: "did:"), "DID should start with 'did:', got: \(didLabel.label)")
    }

    func testReceiveAndPresentAgainstPublicDemoIssuer2Verifier2() async throws {
        let scenario = try publicDemoScenario()
        let offer = try await backend.createOffer(scenario: scenario)

        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: isolatedWalletEnvironment())

        let readyStatus = ui.waitUntilWalletReady(timeout: walletReadyTimeout)
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        ui.openDeepLink(offer.offerUrl)
        XCTAssertTrue(app.buttons["wallet.offerAcceptButton"].waitForExistence(timeout: credentialOperationTimeout), app.debugDescription)
        let offeredInformation = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "issuance-details-")).firstMatch
        XCTAssertTrue(offeredInformation.waitForExistence(timeout: 10))
        ui.tapButton(identifier: offeredInformation.identifier, fallbackLabel: "Credential information")
        XCTAssertTrue(app.descendants(matching: .any)["issuance-credential-details"].waitForExistence(timeout: 10))
        assertCredentialInformationHeader(app)
        XCTAssertFalse(app.buttons["wallet.offerAcceptButton"].exists)
        captureHost("Offered credential information")
        ui.tapButton(identifier: "wallet-detail-back", fallbackLabel: "Back")
        XCTAssertTrue(app.buttons["wallet.offerAcceptButton"].waitForExistence(timeout: 10))
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")

        XCTAssertTrue(app.buttons["issuance-done"].waitForExistence(timeout: credentialOperationTimeout), app.debugDescription)

        ui.tapButton(identifier: "issuance-done", fallbackLabel: "Done")
        assertHome(app)
        let credentialCards = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH %@", "wallet.credentialCard."))
        XCTAssertTrue(credentialCards.firstMatch.waitForExistence(timeout: 20))
        let savedCredentialIDs = Set(credentialCards.allElementsBoundByIndex.map(\.identifier))
        XCTAssertFalse(savedCredentialIDs.isEmpty)
        // XCUIApplication.terminate ends the app process; a new wallet object alone is insufficient.
        app.terminate()
        ui.launch(initializeSigningIdentity: false)
        XCTAssertEqual(ui.waitUntilWalletReady(timeout: walletReadyTimeout), "Wallet ready")
        XCTAssertFalse(app.buttons["wallet.keySetupContinue"].exists, "Restart must reuse the saved identity")
        XCTAssertTrue(credentialCards.firstMatch.waitForExistence(timeout: 20))
        let reopenedCredentialIDs = Set(credentialCards.allElementsBoundByIndex.map(\.identifier))
        XCTAssertEqual(reopenedCredentialIDs, savedCredentialIDs)
        let session = try await backend.createVerifierSession(
            scenario: scenario, signedRequest: true, clientID: DemoBackend.didVerifierClientID
        )
        ui.openDeepLink(session.authorizationRequestUri)
        let previewStatus = ui.waitForPresentationReview(timeout: credentialOperationTimeout)
        XCTAssertTrue(previewStatus, app.debugDescription)
        XCTAssertTrue(app.staticTexts["Information to share"].waitForExistence(timeout: 10))
        captureHost("Requested credential information")
        XCTAssertTrue(app.staticTexts["Given name"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Family name"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["wallet.presentationSubmitButton"].waitForExistence(timeout: 10))

        ui.tapButton(identifier: "wallet.presentationSubmitButton", fallbackLabel: "Share", useCoordinateTap: true)
        var submitStatus = ui.waitForStatus(
            prefixes: ["Presenting credential", "Presentation sent", "Present failed", "Receive failed", "Bootstrap failed"],
            timeout: 10
        )
        if submitStatus == nil {
            ui.tapButton(identifier: "wallet.presentationSubmitButton", fallbackLabel: "Share", useCoordinateTap: true)
            submitStatus = ui.waitForStatus(
                prefixes: ["Presenting credential", "Presentation sent", "Present failed", "Receive failed", "Bootstrap failed"],
                timeout: 10
            )
        }
        guard let submitStatus else {
            let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
            screenshot.name = "Presentation submit did not change status"
            screenshot.lifetime = .keepAlways
            add(screenshot)

            let hierarchy = XCTAttachment(string: app.debugDescription)
            hierarchy.name = "Presentation submit UI hierarchy"
            hierarchy.lifetime = .keepAlways
            add(hierarchy)

            XCTFail("Presentation submit did not update app status after tapping Share")
            return
        }
        XCTAssertFalse(submitStatus.starts(with: "Present failed"), "Present failed: \(submitStatus)")
        XCTAssertFalse(submitStatus.starts(with: "Receive failed"), "Receive failed during presentation: \(submitStatus)")
        XCTAssertFalse(submitStatus.starts(with: "Bootstrap failed"), "Bootstrap failed during presentation: \(submitStatus)")

        try await backend.waitForVerifierSuccess(
            sessionID: session.sessionID,
            timeoutSeconds: presentationOperationTimeout
        )

        if let presentStatus = ui.waitForStatus(
            prefixes: ["Presentation sent", "Presentation finished", "Present failed", "Receive failed", "Bootstrap failed"],
            timeout: verifierPollingTimeout
        ) {
            XCTAssertFalse(presentStatus.starts(with: "Present failed"), "Present failed: \(presentStatus)")
            XCTAssertFalse(presentStatus.starts(with: "Receive failed"), "Receive failed during presentation: \(presentStatus)")
            XCTAssertFalse(presentStatus.starts(with: "Bootstrap failed"), "Bootstrap failed during presentation: \(presentStatus)")
        }
        XCTAssertTrue(app.buttons["wallet.presentationDone"].waitForExistence(timeout: credentialOperationTimeout))
        XCTAssertFalse(app.textFields["wallet.presentationInput"].exists)
        captureHost("Sharing result")
        ui.tapButton(identifier: "wallet.presentationDone", fallbackLabel: "Done")
        assertHome(app)
    }

    private func assertCredentialInformationHeader(_ app: XCUIApplication) {
        // Compose exposes its heading as an accessibility container on iOS.
        let headers = app.descendants(matching: .any).matching(identifier: "wallet.screen.title")
        XCTAssertEqual(headers.count, 1)
        XCTAssertEqual(headers.firstMatch.label, "Credential information")
    }

    private func assertHome(_ app: XCUIApplication) {
        XCTAssertTrue(app.buttons["wallet.scanButton"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.textFields["wallet.presentationInput"].exists,
            "Completion must return Home, not the legacy request form")
        XCTAssertFalse(app.buttons["wallet.presentationSubmitButton"].exists)
    }

    private func captureHost(_ name: String) {
        let image = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        image.name = name
        image.lifetime = .keepAlways
        add(image)
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
        ui.launch(environment: isolatedWalletEnvironment())

        let readyStatus = ui.waitUntilWalletReady(timeout: walletReadyTimeout)
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        ui.openDeepLink(offer.offerUrl)

        guard let txCodeInput = ui.waitForTextInput(
            identifier: "wallet.txCodeInput",
            fallbackLabel: "Transaction code",
            timeout: 20
        ) else {
            let status = ui.waitForStatus(
                prefixes: ["Receive failed", "Bootstrap failed", "Wallet ready"],
                timeout: 1
            )
            XCTFail("Transaction-code input did not appear in offer review, status: \(status ?? "nil")")
            return
        }

        ui.replaceText(in: txCodeInput, value: incorrectCode(for: transactionCode), dismiss: false)
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        let rejectedStatus = ui.waitForStatus(prefixes: ["Receive failed"], timeout: credentialOperationTimeout)
        guard rejectedStatus?.starts(with: "Receive failed") == true else {
            XCTFail("Incorrect transaction code was not rejected, status: \(rejectedStatus ?? "nil")")
            return
        }

        // The reviewed offer remains active so the corrected code can be retried directly.
        ui.replaceText(in: txCodeInput, value: transactionCode, dismiss: false)
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertTrue(app.buttons["issuance-done"].waitForExistence(timeout: credentialOperationTimeout), app.debugDescription)
    }

    func testTransactionDataPreviewAgainstPublicDemoIssuer2Verifier2() async throws {
        let scenario = DemoBackend.transactionDataPresentationScenario
        let offer = try await backend.createOffer(scenario: scenario)

        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: isolatedWalletEnvironment())

        let readyStatus = ui.waitUntilWalletReady(timeout: walletReadyTimeout)
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        ui.openDeepLink(offer.offerUrl)
        XCTAssertTrue(app.buttons["wallet.offerAcceptButton"].waitForExistence(timeout: credentialOperationTimeout), app.debugDescription)
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")

        XCTAssertTrue(app.buttons["issuance-done"].waitForExistence(timeout: credentialOperationTimeout), app.debugDescription)

        ui.tapButton(identifier: "issuance-done", fallbackLabel: "Done")
        let session = try await backend.createTransactionDataVerifierSession(scenario: scenario)
        ui.openDeepLink(session.authorizationRequestUri)
        let previewStatus = ui.waitForPresentationReview(timeout: credentialOperationTimeout)
        XCTAssertTrue(previewStatus, app.debugDescription)

        XCTAssertTrue(app.staticTexts["Payment Authorization"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["42.00"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["EUR"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["ACME Corp"].waitForExistence(timeout: 10))
        let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        screenshot.name = "WAL-1077 Compose iOS transaction data preview"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    func testDemoCredentialPersistsAcrossAppRestart() async throws {
        let scenario = try publicDemoScenario()
        let offer = try await backend.createOffer(scenario: scenario)

        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        let environment = isolatedWalletEnvironment()

        ui.launch(environment: environment)
        let readyStatus = ui.waitUntilWalletReady(timeout: walletReadyTimeout)
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        ui.openDeepLink(offer.offerUrl)
        XCTAssertTrue(app.buttons["wallet.offerAcceptButton"].waitForExistence(timeout: credentialOperationTimeout), app.debugDescription)
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")

        XCTAssertTrue(app.buttons["issuance-done"].waitForExistence(timeout: credentialOperationTimeout), app.debugDescription)
        XCTAssertFalse(app.staticTexts["No credentials"].exists)

        app.terminate()
        try await Task.sleep(nanoseconds: 2_000_000_000)

        ui.launchExpectingLoginAndUnlock(environment: environment, walletReadyTimeout: walletReadyTimeout)
        XCTAssertFalse(app.staticTexts["No credentials"].exists, "Credentials did not persist across app restart")
    }

    private func publicDemoScenario() throws -> DemoCredentialScenario {
        try XCTUnwrap(DemoBackend.presentationScenarios.first { $0.id == "eudi-pid-mdoc" })
    }

    private func incorrectCode(for code: String) -> String {
        precondition(!code.isEmpty, "Transaction code must not be empty")
        let replacement = code.last == "0" ? "1" : "0"
        return String(code.dropLast()) + replacement
    }

    private func isolatedWalletEnvironment() -> [String: String] {
        [
            "WALLET_ID": "compose-ios-public-demo-\(UUID().uuidString)",
            "TRANSACTION_DATA_PROFILES_URL": DemoBackend.transactionDataProfilesURL.absoluteString,
        ]
    }
}

@MainActor
final class WalletIdentitySetupUITests: XCTestCase {
    func testBackupResetRestoreAndRestartKeepTheOriginalDid() {
        continueAfterFailure = false
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        let environment = ["WALLET_ID": "recovery-ui-\(UUID().uuidString)"]
        ui.launch(environment: environment, initializeSigningIdentity: false)
        let next = app.buttons["wallet.keySetupContinue"]
        XCTAssertTrue(next.waitForExistence(timeout: 30))
        ui.tapButton(identifier: "wallet.keySetupEdit.Recovery", fallbackLabel: "Recovery")
        ui.tapButton(identifier: "wallet.keySetupChoice.Recovery.1", fallbackLabel: "Back up with iCloud Keychain")
        next.tap()
        ui.tapButton(identifier: "wallet.keySetupEdit.Storage", fallbackLabel: "Key storage")
        XCTAssertEqual(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Secure Enclave")).count, 0)
        capture("recoverable-key-storage", app: app)
        next.tap()
        XCTAssertTrue(app.buttons["wallet.keySetupEdit.Recovery"].exists)
        next.tap()
        XCTAssertEqual(ui.waitUntilWalletReady(timeout: 30), "Wallet ready")
        ui.tapButton(identifier: "wallet.settingsButton", fallbackLabel: "Settings")
        app.buttons["wallet.settingsTechnicalDetails"].tap()
        let did = app.staticTexts["wallet.settingsDid"].label
        ui.tapButton(identifier: "wallet.settingsBack", fallbackLabel: "Back")
        XCTAssertTrue(did.hasPrefix("did:jwk:"))
        app.buttons["wallet.settingsSigningKey"].tap()
        let receipt = app.staticTexts["Saved on this device. Delivery to another device is not confirmed."]
        for _ in 0..<5 where !receipt.isHittable { app.swipeUp() }
        XCTAssertTrue(receipt.exists)
        capture("backup-receipt", app: app)
        ui.tapButton(identifier: "wallet.settingsBack", fallbackLabel: "Back")
        for _ in 0..<8 where !app.buttons["wallet.settingsReset"].isHittable { app.swipeUp() }
        ui.tapButton(identifier: "wallet.settingsReset", fallbackLabel: "Reset wallet")
        capture("reset-warning", app: app)
        app.buttons["Cancel"].tap()
        XCTAssertTrue(app.buttons["wallet.settingsReset"].exists)
        ui.tapButton(identifier: "wallet.settingsReset", fallbackLabel: "Reset wallet")
        ui.tapButton(identifier: "wallet.settingsResetConfirm", fallbackLabel: "Reset")
        XCTAssertTrue(app.descendants(matching: .any)["wallet.pinInput"].waitForExistence(timeout: 30))
        app.terminate()
        ui.launch(environment: environment, initializeSigningIdentity: false)
        XCTAssertTrue(next.waitForExistence(timeout: 30))
        ui.tapButton(identifier: "wallet.keySetupEdit.Recovery", fallbackLabel: "Recovery")
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
            return abs(frame.midY - previousFrame.midY) < 1
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [settled], timeout: 5), .completed)
        let visibleTop = max(restore.frame.minY, 180)
        let visibleBottom = min(restore.frame.maxY, next.frame.minY - 20)
        XCTAssertGreaterThan(visibleBottom, visibleTop)
        app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(
            dx: restore.frame.midX, dy: (visibleTop + visibleBottom) / 2
        )).tap()
        let selected = XCTNSPredicateExpectation(predicate: NSPredicate(format: "selected == true"), object: restore)
        XCTAssertEqual(XCTWaiter.wait(for: [selected], timeout: 5), .completed, app.debugDescription)
        capture("selected-recovery-record", app: app)
        next.tap()
        XCTAssertTrue(app.buttons["wallet.keySetupEdit.Recovery"].waitForExistence(timeout: 10))
        XCTAssertEqual(next.label, "Restore signing key")
        next.tap()
        XCTAssertEqual(ui.waitUntilWalletReady(timeout: 30), "Wallet ready")
        ui.tapButton(identifier: "wallet.settingsButton", fallbackLabel: "Settings")
        app.buttons["wallet.settingsTechnicalDetails"].tap()
        XCTAssertEqual(app.staticTexts["wallet.settingsDid"].label, did)
        ui.tapButton(identifier: "wallet.settingsBack", fallbackLabel: "Back")
        capture("restored-key", app: app)
        app.terminate()
        ui.launch(environment: environment)
        XCTAssertEqual(ui.waitUntilWalletReady(timeout: 30), "Wallet ready")
        ui.tapButton(identifier: "wallet.settingsButton", fallbackLabel: "Settings")
        app.buttons["wallet.settingsTechnicalDetails"].tap()
        XCTAssertEqual(app.staticTexts["wallet.settingsDid"].label, did)
        ui.tapButton(identifier: "wallet.settingsBack", fallbackLabel: "Back")
        app.buttons["wallet.settingsSigningKey"].tap()
        let delete = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Delete key backup,")).firstMatch
        for _ in 0..<10 {
            if delete.exists && delete.frame.minY > 100 && delete.frame.maxY < app.frame.maxY - 100 { break }
            app.swipeUp()
        }
        var previousDeleteFrame = CGRect.zero
        let deleteSettled = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            let frame = delete.frame
            defer { previousDeleteFrame = frame }
            return abs(frame.midY - previousDeleteFrame.midY) < 1
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [deleteSettled], timeout: 5), .completed)
        app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: delete.frame.midX, dy: delete.frame.midY)).tap()
        XCTAssertTrue(app.buttons["Cancel"].waitForExistence(timeout: 5))
        capture("delete-recovery-warning", app: app)
        app.buttons["Cancel"].firstMatch.tap()
        XCTAssertTrue(delete.exists)
        delete.tap()
        app.buttons["Delete key backup"].tap()
        let removed = app.staticTexts["Backup deletion requested. Keys already restored on other devices are not deleted."]
        XCTAssertTrue(removed.waitForExistence(timeout: 20))
        ui.tapButton(identifier: "wallet.settingsBack", fallbackLabel: "Back")
        for _ in 0..<8 where !app.buttons["wallet.settingsReset"].isHittable { app.swipeUp() }
        ui.tapButton(identifier: "wallet.settingsReset", fallbackLabel: "Reset wallet")
        ui.tapButton(identifier: "wallet.settingsResetConfirm", fallbackLabel: "Reset")
        XCTAssertTrue(app.descendants(matching: .any)["wallet.pinInput"].waitForExistence(timeout: 30))
    }

    private func capture(_ name: String, app: XCUIApplication) {
        // Compose navigation/rotation animations are not part of XCTest's
        // UIKit idling. Let the rendered frame settle before taking evidence.
        RunLoop.current.run(until: Date().addingTimeInterval(0.6))
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "wal749-\(name)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testComposeIdentitySetupAndProtectionDetails() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["WALLET_ID": "identity-ui-\(UUID().uuidString)"], initializeSigningIdentity: false)
        let create = app.buttons["wallet.keySetupContinue"]
        XCTAssertTrue(create.waitForExistence(timeout: 30))
        let setup = XCTAttachment(screenshot: app.screenshot())
        setup.name = "wal749-compose-ios-identity-setup"
        setup.lifetime = .keepAlways
        add(setup)
        for step in ["Recovery", "Storage", "Approval"] {
            ui.tapButton(identifier: "wallet.keySetupEdit.\(step)", fallbackLabel: step)
            let screen = XCTAttachment(screenshot: app.screenshot())
            screen.name = "key-setup-option-\(step)"
            screen.lifetime = .keepAlways
            add(screen)
            create.tap()
            XCTAssertTrue(app.buttons["wallet.keySetupEdit.Recovery"].waitForExistence(timeout: 10))
        }
        create.tap()
        XCTAssertEqual(ui.waitUntilWalletReady(timeout: 30), "Wallet ready")
        ui.tapButton(identifier: "wallet.settingsButton", fallbackLabel: "Settings")
        app.buttons["wallet.settingsSigningKey"].tap()
        let recovery = app.staticTexts["No key backup submitted."]
        for _ in 0..<4 where !recovery.isHittable { app.swipeUp() }
        XCTAssertTrue(recovery.waitForExistence(timeout: 10))
        let active = XCTAttachment(screenshot: app.screenshot())
        active.name = "wal749-compose-ios-identity-details"
        active.lifetime = .keepAlways
        add(active)
        ui.tapButton(identifier: "wallet.settingsBack", fallbackLabel: "Back")
        capture("settings-root", app: app)
        ui.tapButton(identifier: "wallet.settingsTechnicalDetails", fallbackLabel: "Technical details")
        let did = app.staticTexts["wallet.settingsDid"]
        XCTAssertTrue(did.waitForExistence(timeout: 10))
        XCTAssertTrue(did.label.hasPrefix("did:jwk:"))
        let disclosure = app.buttons["Show public key"]
        let disclosureFrame = disclosure.frame
        let copy = app.buttons["wallet.settingsDidCopy"]
        ui.tapButton(identifier: "wallet.settingsDidCopy", fallbackLabel: "Copy wallet DID")
        let copied = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == %@", "Wallet DID copied"), object: copy)
        XCTAssertEqual(XCTWaiter.wait(for: [copied], timeout: 3), .completed)
        XCTAssertEqual(disclosure.frame, disclosureFrame, "Copy feedback must not move the disclosure button")
        XCTAssertFalse(app.staticTexts["wallet.settingsPublicJwk"].exists)
        capture("technical-copy", app: app)
        let feedbackExpired = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value != %@", "Wallet DID copied"), object: copy)
        XCTAssertEqual(XCTWaiter.wait(for: [feedbackExpired], timeout: 3), .completed)
        XCTAssertEqual(disclosure.frame, disclosureFrame, "Expiring feedback must not move the disclosure button")
        disclosure.tap()
        XCTAssertTrue(app.staticTexts["wallet.settingsPublicJwk"].waitForExistence(timeout: 3))
        capture("technical-expanded", app: app)
        ui.tapButton(identifier: "wallet.settingsBack", fallbackLabel: "Back")
        ui.tapButton(identifier: "wallet.settingsDigitalCredentialsApi", fallbackLabel: "Digital Credentials API")
        XCTAssertTrue(app.descendants(matching: .any)["wallet.settingsShowDcApiPreview"].waitForExistence(timeout: 5))
        capture("digital-credentials-api", app: app)
        ui.tapButton(identifier: "wallet.settingsBack", fallbackLabel: "Back")
        ui.tapButton(identifier: "wallet.settingsProximityPresentation", fallbackLabel: "Nearby sharing")
        capture("nearby-sharing", app: app)
        ui.tapButton(identifier: "wallet.settingsReaderAuthentication", fallbackLabel: "Reader authentication")
        XCTAssertTrue(app.descendants(matching: .any)["wallet.settingsReaderPolicyAllowUntrusted"].waitForExistence(timeout: 5))
        capture("reader-authentication", app: app)
        ui.tapButton(identifier: "wallet.settingsBack", fallbackLabel: "Back")
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
