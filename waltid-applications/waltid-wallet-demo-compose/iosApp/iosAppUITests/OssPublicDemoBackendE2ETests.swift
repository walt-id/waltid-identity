import XCTest

/// End-to-end UI tests for the Compose wallet demo app against the public OSS demo stack.
///
/// Tests the full user flow: launch app, receive credential, present credential,
/// and keep received credentials across app restart.
@MainActor
final class OssPublicDemoBackendE2ETests: XCTestCase {

    private let backend = DemoBackend.shared

    // Timeouts (aligned with Android for cross-platform consistency)
    private let walletReadyTimeout: TimeInterval = 60
    private let credentialOperationTimeout: TimeInterval = 90
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

    func testReceiveAndPresentAgainstOssPublicDemoIssuer2Verifier2() async throws {
        let scenario = try ossUiScenario()
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

    func testDemoCredentialPersistsAcrossAppRestart() async throws {
        let scenario = try ossUiScenario()
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

    private func ossUiScenario() throws -> DemoCredentialScenario {
        try XCTUnwrap(DemoBackend.presentationScenarios.first { $0.id == "eudi-pid-mdoc" })
    }

    private func isolatedWalletEnvironment() -> [String: String] {
        ["WALLET_ID": "compose-ios-oss-demo-\(UUID().uuidString)"]
    }
}
