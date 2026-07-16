import XCTest

/// End-to-end UI tests for the Compose wallet demo app against the public demo stack.
///
/// Tests the full user flow: launch app, receive credential, present credential,
/// and keep received credentials across app restart.
@MainActor
final class PublicDemoBackendE2ETests: XCTestCase {

    private let backend = DemoBackend.shared

    // Timeouts (aligned with Android for cross-platform consistency)
    private let walletReadyTimeout: TimeInterval = 60
    private let credentialOperationTimeout: TimeInterval = 90
    private let presentationOperationTimeout: TimeInterval = 180
    private let verifierPollingTimeout: TimeInterval = 30

    func testBootstrapCreatesDid() async throws {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: isolatedWalletEnvironment())

        let readyStatus = ui.waitForStatus(
            prefixes: ["Wallet ready", "Bootstrap failed"],
            timeout: walletReadyTimeout
        )
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        let didLabel = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "did:")).firstMatch
        XCTAssertTrue(didLabel.waitForExistence(timeout: 10), "Bootstrapped DID label was not exposed")
        XCTAssertTrue(didLabel.label.starts(with: "did:"), "DID should start with 'did:', got: \(didLabel.label)")
    }

    func testReceiveAndPresentAgainstPublicDemoIssuer2Verifier2() async throws {
        let scenario = try publicDemoScenario()
        let offer = try await backend.createOffer(scenario: scenario)

        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: isolatedWalletEnvironment())

        let readyStatus = ui.waitForStatus(
            prefixes: ["Wallet ready", "Bootstrap failed"],
            timeout: walletReadyTimeout
        )
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        ui.openDeepLink(offer.offerUrl)
        let offerURLApplied = ui.waitForTextInputValue(
            identifier: "wallet.offerInput",
            fallbackLabel: "Credential offer URL",
            value: offer.offerUrl,
            timeout: 10
        )
        XCTAssertTrue(offerURLApplied, "Offer URL did not appear in UI after deep link")
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")

        let receiveStatus = ui.waitForStatus(
            prefixes: ["Received", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertTrue(receiveStatus?.starts(with: "Received") == true, "Receive failed, status: \(receiveStatus ?? "nil")")

        let session = try await backend.createVerifierSession(scenario: scenario)
        ui.openDeepLink(session.authorizationRequestUri)
        let presentationURLApplied = ui.waitForTextInputValue(
            identifier: "wallet.presentationInput",
            fallbackLabel: "OpenID4VP request URL",
            value: session.authorizationRequestUri,
            timeout: 10
        )
        XCTAssertTrue(presentationURLApplied, "Presentation request URL did not appear in UI after deep link")
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Present")

        let previewStatus = ui.waitForStatus(
            prefixes: ["Review presentation request", "Preview failed", "Present failed", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertEqual(previewStatus, "Review presentation request", "Presentation preview did not load, status: \(previewStatus ?? "nil")")

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

        let readyStatus = ui.waitForStatus(
            prefixes: ["Wallet ready", "Bootstrap failed"],
            timeout: walletReadyTimeout
        )
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        ui.openDeepLink(offer.offerUrl)
        XCTAssertTrue(
            ui.waitForTextInputValue(
                identifier: "wallet.offerInput",
                fallbackLabel: "Credential offer URL",
                value: offer.offerUrl,
                timeout: 20
            ),
            "Offer URL did not appear in UI after deep link"
        )
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")

        guard let txCodeInput = ui.waitForTextInput(
            identifier: "wallet.txCodeInput",
            fallbackLabel: "Transaction code",
            timeout: 20
        ) else {
            let status = ui.waitForStatus(
                prefixes: ["Receive failed", "Bootstrap failed", "Wallet ready"],
                timeout: 1
            )
            XCTFail("Transaction-code input did not appear, status: \(status ?? "nil")")
            return
        }

        ui.replaceText(in: txCodeInput, value: incorrectCode(for: transactionCode))
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        let rejectedStatus = ui.waitForStatus(prefixes: ["Receive failed"], timeout: credentialOperationTimeout)
        guard rejectedStatus?.starts(with: "Receive failed") == true else {
            XCTFail("Incorrect transaction code was not rejected, status: \(rejectedStatus ?? "nil")")
            return
        }

        ui.replaceText(in: txCodeInput, value: transactionCode)
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        let receivedStatus = ui.waitForStatus(
            prefixes: ["Received", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertTrue(
            receivedStatus?.starts(with: "Received") == true,
            "Receive did not succeed after correcting the transaction code, status: \(receivedStatus ?? "nil")"
        )
    }

    func testTransactionDataPreviewAgainstPublicDemoIssuer2Verifier2() async throws {
        let scenario = DemoBackend.transactionDataPresentationScenario
        let offer = try await backend.createOffer(scenario: scenario)

        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: isolatedWalletEnvironment())

        let readyStatus = ui.waitForStatus(
            prefixes: ["Wallet ready", "Bootstrap failed"],
            timeout: walletReadyTimeout
        )
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        ui.openDeepLink(offer.offerUrl)
        let offerURLApplied = ui.waitForTextInputValue(
            identifier: "wallet.offerInput",
            fallbackLabel: "Credential offer URL",
            value: offer.offerUrl,
            timeout: 10
        )
        XCTAssertTrue(offerURLApplied, "Offer URL did not appear in UI after deep link")
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")

        let receiveStatus = ui.waitForStatus(
            prefixes: ["Received", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertTrue(receiveStatus?.starts(with: "Received") == true, "Receive failed, status: \(receiveStatus ?? "nil")")

        let session = try await backend.createTransactionDataVerifierSession(scenario: scenario)
        ui.openDeepLink(session.authorizationRequestUri)
        let presentationURLApplied = ui.waitForTextInputValue(
            identifier: "wallet.presentationInput",
            fallbackLabel: "OpenID4VP request URL",
            value: session.authorizationRequestUri,
            timeout: 10
        )
        XCTAssertTrue(presentationURLApplied, "Presentation request URL did not appear in UI after deep link")
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Present")

        let previewStatus = ui.waitForStatus(
            prefixes: ["Review presentation request", "Preview failed", "Present failed", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertEqual(previewStatus, "Review presentation request", "Presentation preview did not load, status: \(previewStatus ?? "nil")")

        XCTAssertTrue(app.staticTexts["PAYMENT AUTHORIZATION"].waitForExistence(timeout: 10))
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
        let readyStatus = ui.waitForStatus(
            prefixes: ["Wallet ready", "Bootstrap failed"],
            timeout: walletReadyTimeout
        )
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        ui.openDeepLink(offer.offerUrl)
        let offerURLApplied = ui.waitForTextInputValue(
            identifier: "wallet.offerInput",
            fallbackLabel: "Credential offer URL",
            value: offer.offerUrl,
            timeout: 10
        )
        XCTAssertTrue(offerURLApplied, "Offer URL did not appear in UI after deep link")
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")

        let receiveStatus = ui.waitForStatus(
            prefixes: ["Received", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertTrue(receiveStatus?.starts(with: "Received") == true, "Receive failed, status: \(receiveStatus ?? "nil")")
        XCTAssertFalse(app.staticTexts["No credentials"].exists)

        app.terminate()
        try await Task.sleep(nanoseconds: 2_000_000_000)

        ui.launch(environment: environment)
        let readyAfterRestart = ui.waitForStatus(
            prefixes: ["Wallet ready", "Bootstrap failed"],
            timeout: walletReadyTimeout
        )
        XCTAssertEqual(readyAfterRestart, "Wallet ready", "Wallet did not become ready after restart, status: \(readyAfterRestart ?? "nil")")
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
