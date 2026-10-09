import XCTest
import TestHelpers

/// End-to-end UI test for the SwiftUI wallet demo app against the public demo stack.
///
/// Tests the full user flow: launch app, receive credential, present credential,
/// and confirm verifier2 observed the presentation.
@MainActor
final class PublicDemoBackendE2ETests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    private let backend = DemoBackend.shared

    // Timeouts (aligned with Android for cross-platform consistency)
    private let walletReadyTimeout: TimeInterval = 60
    private let credentialOperationTimeout: TimeInterval = 90
    private let verifierPollingTimeout: TimeInterval = 30

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
        ui.launch(environment: publicDemoEnvironment().merging(["WALLET_SIGNING_PROTECTION_MODE": "required"]) { _, new in new },
                  initializeSigningIdentity: false)
        let next = app.buttons["wallet.keySetupContinue"]
        XCTAssertTrue(app.buttons["wallet.keySetupEdit.storage"].waitForExistence(timeout: 20))
        // Default: no recovery, so Secure Enclave remains eligible.
        ui.tapButton(identifier: "wallet.keySetupEdit.storage", fallbackLabel: "Key storage")
        let storage = app.staticTexts["Secure Enclave"]
        XCTAssertTrue(storage.waitForExistence(timeout: 10))
        storage.tap()
        next.tap()
        ui.tapButton(identifier: "wallet.keySetupEdit.approval", fallbackLabel: "Signing approval")
        let approval = app.staticTexts["Current biometrics only"]
        XCTAssertTrue(approval.waitForExistence(timeout: 10))
        approval.tap()
        next.tap()
        XCTAssertTrue(app.buttons["wallet.keySetupEdit.approval"].waitForExistence(timeout: 10))
        print("SCA_OPERATOR: approve iPhone key setup and issuance prompts")
        next.tap()
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 180), "Wallet ready")
        let authentication = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts["com.apple.localauthentication.ax.authentication.alert"]
        let authorized = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: authentication)
        XCTAssertEqual(XCTWaiter.wait(for: [authorized], timeout: 180), .completed, "Native setup authorization is still pending")

        ui.openWalletLink(offer.offerUrl)
        XCTAssertTrue(ui.waitForOfferReview(timeout: 90), app.debugDescription)
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        ui.assertExists(identifierPrefix: "wallet.credentialCard.", timeout: 180)

        let session = try await backend.createScaPaymentVerifierSession()
        ui.openWalletLink(session.authorizationRequestUri)
        XCTAssertTrue(ui.waitForPresentationReview(timeout: 60), app.debugDescription)
        let payment = app.descendants(matching: .any).matching(identifier: "payment-consent").firstMatch
        XCTAssertTrue(payment.waitForExistence(timeout: 60), app.debugDescription)
        for value in ["Super Store", "11.56", "EUR", "Confirm this payment", "Payee", "Payee ID", "Currency", "Amount",
                      "Check the payee and amount before confirming."] {
            let text = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", value)).firstMatch
            for _ in 0..<8 where !text.isHittable { app.swipeDown() }
            for _ in 0..<12 where !text.isHittable { app.swipeUp() }
            XCTAssertTrue(text.isHittable, "Missing visible payment value: \(value)")
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

        ui.openWalletLink(offer.offerUrl)
        let offerReadyStatus = ui.waitForOfferReview(timeout: credentialOperationTimeout)
        XCTAssertTrue(offerReadyStatus, app.debugDescription)
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")

        ui.allowIdentityDocumentRegistrationIfRequested()
        // Successful in-app issuance returns to the credential list.
        ui.assertExists(identifierPrefix: "wallet.credentialCard.", timeout: credentialOperationTimeout)

        ui.returnToWallet()
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        ui.assertExists(identifierPrefix: "wallet.credentialOverview.")
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "wallet.credentialDetailsScreen").firstMatch.waitForExistence(timeout: 20))
        ui.tapNavigationBack()

        let savedCredentialIDs = Set(app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH %@", "wallet.credentialCard."))
            .allElementsBoundByIndex.map(\.identifier))
        XCTAssertFalse(savedCredentialIDs.isEmpty)
        // XCUIApplication.terminate ends the app process; a new wallet object alone is insufficient.
        app.terminate()
        ui.launch(initializeSigningIdentity: false)
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: walletReadyTimeout), "Wallet ready")
        ui.returnToWallet()
        XCTAssertFalse(app.buttons["wallet.keySetupContinue"].exists, "Restart must reuse the saved identity")
        let reopenedCredentialIDs = Set(app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH %@", "wallet.credentialCard."))
            .allElementsBoundByIndex.map(\.identifier))
        XCTAssertEqual(reopenedCredentialIDs, savedCredentialIDs)
        let session = try await backend.createVerifierSession(
            scenario: scenario, signedRequest: true, clientID: DemoBackend.didVerifierClientID
        )
        ui.openWalletLink(session.authorizationRequestUri)
        let previewStatus = ui.waitForPresentationReview(timeout: credentialOperationTimeout)
        XCTAssertTrue(previewStatus, app.debugDescription)

        XCTAssertTrue(app.staticTexts["Information to share"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.staticTexts["Given name"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.staticTexts["Family name"].waitForExistence(timeout: 20))

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

        ui.openWalletLink(offer.offerUrl)
        let offerReadyStatus2 = ui.waitForOfferReview(timeout: credentialOperationTimeout)
        XCTAssertTrue(offerReadyStatus2, app.debugDescription)
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")

        let receiveStatus = ui.waitForStatus(
            prefixes: ["Received", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertTrue(receiveStatus?.starts(with: "Received") == true, "Receive failed, status: \(receiveStatus ?? "nil")")

        let session = try await backend.createTransactionDataVerifierSession(scenario: scenario)
        ui.openWalletLink(session.authorizationRequestUri)
        let previewStatus = ui.waitForPresentationReview(timeout: credentialOperationTimeout)
        XCTAssertTrue(previewStatus, app.debugDescription)

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
        XCTAssertTrue(app.buttons["wallet.offerAcceptButton"].waitForExistence(timeout: credentialOperationTimeout), app.debugDescription)

        let txCodeInput = ui.textInput(identifier: "wallet.txCodeInput", fallbackLabel: "Transaction code")
        guard txCodeInput.waitForExistence(timeout: 20) else {
            XCTFail("Transaction-code input did not appear in offer review")
            return
        }

        ui.replaceText(in: txCodeInput, value: incorrectCode(for: transactionCode))
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        let error = app.staticTexts["wallet.status"]
        let rejected = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "label == %@", "The issuer returned an invalid or unsuccessful response."),
            object: error
        )
        XCTAssertEqual(XCTWaiter.wait(for: [rejected], timeout: credentialOperationTimeout), .completed, app.debugDescription)
        XCTAssertFalse(app.buttons["issuance-done"].exists, "A rejected code must not create an issuance receipt")

        // The reviewed offer remains active so the corrected code can be retried directly.
        ui.replaceText(in: txCodeInput, value: transactionCode)
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        ui.allowIdentityDocumentRegistrationIfRequested()
        XCTAssertTrue(app.buttons["issuance-done"].waitForExistence(timeout: credentialOperationTimeout), app.debugDescription)
        ui.tapButton(identifier: "issuance-done", fallbackLabel: "Done")
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
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

        ui.openWalletLink("openid-credential-offer://mock")
        XCTAssertTrue(ui.waitForOfferReview(timeout: 10), app.debugDescription)
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertTrue(app.staticTexts["Saved · 1"].waitForExistence(timeout: 10), app.debugDescription)

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
