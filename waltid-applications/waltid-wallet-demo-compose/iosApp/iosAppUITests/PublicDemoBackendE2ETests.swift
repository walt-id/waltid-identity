import CryptoKit
import XCTest

/// End-to-end UI tests for the Compose wallet demo app against the public demo stack.
///
/// Tests the full user flow: launch app, receive credential, present credential,
/// and keep received credentials across app restart.
@MainActor
final class PublicDemoBackendE2ETests: XCTestCase {

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
        XCTAssertTrue(app.otherElements["Wallet DID copied"].waitForExistence(timeout: 5))
    }

    private let backend = DemoBackend.shared

    // Timeouts (aligned with Android for cross-platform consistency)
    private let walletReadyTimeout: TimeInterval = 60
    private let credentialOperationTimeout: TimeInterval = 90
    private let presentationOperationTimeout: TimeInterval = 180
    private let verifierPollingTimeout: TimeInterval = 30

    func testPinPersistsAcrossAppRestart() throws {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        let environment = isolatedWalletEnvironment()

        ui.launch(environment: environment)
        let readyStatus = ui.waitUntilWalletReady(timeout: walletReadyTimeout)
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        app.terminate()
        ui.launchExpectingLoginAndUnlock(environment: environment, walletReadyTimeout: walletReadyTimeout)
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
        let offerURLApplied = ui.waitForTextInputValue(
            identifier: "wallet.offerInput",
            fallbackLabel: "Credential offer URL",
            value: offer.offerUrl,
            timeout: 10
        )
        XCTAssertTrue(offerURLApplied, "Offer URL did not appear in UI after deep link")
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        let offerPreviewStatus = ui.waitForStatus(
            prefixes: ["Review credential offer", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertEqual(offerPreviewStatus, "Review credential offer", "Offer preview did not appear, status: \(offerPreviewStatus ?? "nil")")
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")

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
        let previewStatus = ui.previewPresentation(timeout: credentialOperationTimeout)
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

        let readyStatus = ui.waitUntilWalletReady(timeout: walletReadyTimeout)
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
            XCTFail("Transaction-code input did not appear in offer review, status: \(status ?? "nil")")
            return
        }

        ui.replaceText(in: txCodeInput, value: incorrectCode(for: transactionCode))
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        try await Task.sleep(for: .seconds(1))
        if ui.button(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept").isEnabled {
            // Compose iOS can consume the first activation after auto-dismissing a text input.
            ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        }
        let rejectedStatus = ui.waitForStatus(prefixes: ["Receive failed"], timeout: credentialOperationTimeout)
        guard rejectedStatus?.starts(with: "Receive failed") == true else {
            XCTFail("Incorrect transaction code was not rejected, status: \(rejectedStatus ?? "nil")")
            return
        }

        // The reviewed offer remains active so the corrected code can be retried directly.
        ui.replaceText(in: txCodeInput, value: transactionCode)
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

    func testTransactionDataPreviewAgainstPublicDemoIssuer2Verifier2() async throws {
        let scenario = DemoBackend.transactionDataPresentationScenario
        let offer = try await backend.createOffer(scenario: scenario)

        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: isolatedWalletEnvironment())

        let readyStatus = ui.waitUntilWalletReady(timeout: walletReadyTimeout)
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
        let offerPreviewStatus2 = ui.waitForStatus(
            prefixes: ["Review credential offer", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertEqual(offerPreviewStatus2, "Review credential offer", "Offer preview did not appear, status: \(offerPreviewStatus2 ?? "nil")")
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")

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
        let previewStatus = ui.previewPresentation(timeout: credentialOperationTimeout)
        XCTAssertEqual(previewStatus, "Review presentation request", "Presentation preview did not load, status: \(previewStatus ?? "nil")")

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
        let offerURLApplied = ui.waitForTextInputValue(
            identifier: "wallet.offerInput",
            fallbackLabel: "Credential offer URL",
            value: offer.offerUrl,
            timeout: 10
        )
        XCTAssertTrue(offerURLApplied, "Offer URL did not appear in UI after deep link")
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        let offerPreviewStatus3 = ui.waitForStatus(
            prefixes: ["Review credential offer", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertEqual(offerPreviewStatus3, "Review credential offer", "Offer preview did not appear, status: \(offerPreviewStatus3 ?? "nil")")
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")

        let receiveStatus = ui.waitForStatus(
            prefixes: ["Received", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertTrue(receiveStatus?.starts(with: "Received") == true, "Receive failed, status: \(receiveStatus ?? "nil")")
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
        ui.tapButton(identifier: "wallet.keySetupChoice.Recovery.1", fallbackLabel: "Back up with iCloud Keychain")
        next.tap()
        XCTAssertTrue(app.staticTexts["2 of 3 · Key storage"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Secure Enclave")).count, 0)
        capture("recoverable-key-storage", app: app)
        next.tap()
        XCTAssertTrue(app.staticTexts["3 of 3 · Signing approval"].waitForExistence(timeout: 10))
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
        XCTAssertTrue(app.staticTexts["2 of 3 · Key storage"].waitForExistence(timeout: 10))
        next.tap()
        XCTAssertTrue(app.staticTexts["3 of 3 · Signing approval"].waitForExistence(timeout: 10))
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
        for (index, heading) in ["1 of 3 · Recovery", "2 of 3 · Key storage", "3 of 3 · Signing approval"].enumerated() {
            XCTAssertTrue(app.staticTexts[heading].waitForExistence(timeout: 10))
            let screen = XCTAttachment(screenshot: app.screenshot())
            screen.name = "wal749-key-setup-step-\(index + 1)"
            screen.lifetime = .keepAlways
            add(screen)
            create.tap()
        }
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
        ui.tapButton(identifier: "wallet.settingsDidCopy", fallbackLabel: "Copy wallet DID")
        XCTAssertTrue(app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "Wallet DID copied")).firstMatch.waitForExistence(timeout: 3))
        XCTAssertFalse(app.staticTexts["wallet.settingsPublicJwk"].exists)
        capture("technical-copy", app: app)
        app.buttons["Show public key"].tap()
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
