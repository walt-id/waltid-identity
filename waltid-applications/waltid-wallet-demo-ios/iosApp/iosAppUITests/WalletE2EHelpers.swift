import Foundation
import XCTest
import TestHelpers

@MainActor
final class WalletE2EUI {
    let app: XCUIApplication
    private let pin = "1234"

    init(app: XCUIApplication) {
        self.app = app
    }

    func completeKeySetupIfNeeded() {
        let button = app.buttons["wallet.keySetupContinue"]
        guard button.waitForExistence(timeout: 10) else { return }
        XCTAssertTrue(app.buttons["wallet.keySetupEdit.storage"].waitForExistence(timeout: 10))
        XCTAssertEqual(button.label, "Create signing key")
        button.tap()
    }

    func launch(attestation: [String: String] = [:], environment: [String: String] = [:], initializeSigningIdentity: Bool = true) {
        app.launchEnvironment["E2E_WALLET_ID"] = app.launchEnvironment["E2E_WALLET_ID"] ?? "e2e-\(UUID().uuidString)"
        app.launchEnvironment["WALLET_SIGNING_PROTECTION_MODE"] =
            app.launchEnvironment["WALLET_SIGNING_PROTECTION_MODE"] ?? "disabled"
        for (key, value) in attestation {
            app.launchEnvironment[key] = value
        }
        for (key, value) in environment {
            app.launchEnvironment[key] = value
        }
        if app.launchEnvironment["E2E_MOCK_WALLET"] == "1" {
            addCredentialImageFixtures()
        }
        app.launch()
        unlockWallet()
        if initializeSigningIdentity && app.launchEnvironment["E2E_MOCK_WALLET"] != "1" {
            completeKeySetupIfNeeded()
        }
    }

    private func addCredentialImageFixtures() {
        let fixtures = [
            ("E2E_MOCK_PORTRAIT_DATA_URL", "synthetic-portrait", "jpg", "image/jpeg"),
            ("E2E_MOCK_SIGNATURE_DATA_URL", "synthetic-signature", "png", "image/png"),
            (
                "E2E_MOCK_VERIFICATION_DOCUMENT_DATA_URL",
                "synthetic-verification-document",
                "jpg",
                "image/jpeg"
            ),
        ]

        for (environmentKey, resourceName, resourceExtension, mimeType) in fixtures
            where app.launchEnvironment[environmentKey] == nil {
            guard let url = Bundle(for: WalletE2EUI.self).url(
                forResource: resourceName,
                withExtension: resourceExtension
            ), let data = try? Data(contentsOf: url) else {
                XCTFail("Missing credential image fixture: \(resourceName).\(resourceExtension)")
                continue
            }
            app.launchEnvironment[environmentKey] = "data:\(mimeType);base64,\(data.base64EncodedString())"
        }
    }

    func waitForStatus(prefixes: [String], timeout: TimeInterval) -> String? {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if let status = latestStatus(prefixes: prefixes) {
                return status
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.4))
        }
        return nil
    }

    func latestStatus(prefixes: [String]) -> String? {
        for prefix in prefixes {
            let predicate = NSPredicate(format: "label BEGINSWITH %@", prefix)
            let match = app.staticTexts.matching(predicate).firstMatch
            if match.exists {
                return match.label
            }
        }
        return nil
    }

    func openDeepLink(_ value: String) {
        guard URL(string: value) != nil else {
            XCTFail("Invalid deep link URL: \(value)")
            return
        }

        // XCUIApplication.open launches a new process. Enter the link in Safari
        // to exercise delivery to the running wallet and its navigation state.
        let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
        safari.activate()
        let address = safari.textFields.firstMatch
        if !address.waitForExistence(timeout: 5), safari.buttons["Continue"].exists {
            safari.buttons["Continue"].tap()
        }
        XCTAssertTrue(address.waitForExistence(timeout: 10), safari.debugDescription)
        address.tap()
        address.typeText(value + XCUIKeyboardKey.return.rawValue)
        // Safari can put its first-run toolbar tip above the external-app confirmation.
        // Dismiss that tip before waiting for the real Open action to become enabled.
        let tip = safari.staticTexts.matching(NSPredicate(format: "label CONTAINS[cd] %@", "View Bookmarks")).firstMatch
        if tip.waitForExistence(timeout: 2) {
            let close = safari.buttons["Close"].firstMatch
            if close.exists { close.tap() }
        }
        let open = safari.buttons["Open"]
        if open.waitForExistence(timeout: 5) {
            let enabled = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: open)
            XCTAssertEqual(XCTWaiter.wait(for: [enabled], timeout: 10), .completed, safari.debugDescription)
            // Safari's external-app confirmation reports no XCTest hit point on
            // iOS 26. Tap the visible button's own frame, not a fixed coordinate.
            XCTAssertFalse(open.frame.isEmpty)
            open.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10), safari.debugDescription)
    }

    /// First-use simulator registration is an OS-owned step, separate from wallet receipt rendering.
    func allowIdentityDocumentRegistrationIfRequested() {
        let alert = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts
            .containing(NSPredicate(format: "label CONTAINS %@", "Identity Verification")).firstMatch
        if alert.waitForExistence(timeout: 15) { alert.buttons["Allow"].tap() }
    }

    func waitForTextInputValue(identifier: String, fallbackLabel: String, value: String, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            let input = textInput(identifier: identifier, fallbackLabel: fallbackLabel)
            if input.exists, input.value as? String == value {
                return true
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.4))
        }
        return false
    }

    func textInput(identifier: String, fallbackLabel: String) -> XCUIElement {
        firstExisting([
            app.textFields[identifier],
            app.secureTextFields[identifier],
            app.textViews[identifier],
            app.textFields[fallbackLabel],
            app.secureTextFields[fallbackLabel],
            app.textViews[fallbackLabel],
        ])
    }

    func tapButton(identifier: String, fallbackLabel: String) {
        let button = firstExisting([
            app.buttons[identifier],
            app.buttons[fallbackLabel],
        ])
        XCTAssertTrue(button.waitForExistence(timeout: 20), "Button not found: \(identifier)")
        makeHittable(button)
        XCTAssertTrue(button.isHittable, "Button is not hittable: \(identifier)")
        button.tap()
    }

    func assertExists(identifierPrefix: String, timeout: TimeInterval = 20) {
        let element = firstElement(identifierPrefix: identifierPrefix)
        XCTAssertTrue(element.waitForExistence(timeout: timeout), "Element not found with identifier prefix: \(identifierPrefix)")
    }

    func assertExists(identifier: String, timeout: TimeInterval = 20) {
        let element = app.descendants(matching: .any)[identifier]
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if element.exists || element.waitForExistence(timeout: 0.5) {
                return
            }
            app.swipeUp()
        }
        XCTAssertTrue(element.exists, "Element not found with identifier: \(identifier)")
    }

    func tapElement(identifierPrefix: String, timeout: TimeInterval = 20) {
        guard let element = waitForHittableElement(identifierPrefix: identifierPrefix, timeout: timeout) else {
            XCTFail("Element not found or not hittable with identifier prefix: \(identifierPrefix)\n\(app.debugDescription)")
            return
        }
        element.tap()
    }

    func tapElement(identifier: String, timeout: TimeInterval = 20) {
        let element = app.descendants(matching: .any)[identifier]
        XCTAssertTrue(element.waitForExistence(timeout: timeout), "Element not found with identifier: \(identifier)")
        makeHittable(element)
        XCTAssertTrue(element.isHittable, "Element is not hittable with identifier: \(identifier)")
        element.tap()
    }

    func claimImageIdentifier(path: String) -> String {
        "wallet.claimImage.\(path.identifierSegment)"
    }

    func tapNavigationBack() {
        let close = app.buttons["wallet.detailsBack"]
        if close.waitForExistence(timeout: 2) {
            close.tap()
            return
        }
        let button = app.navigationBars.buttons.firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: 20), "Navigation back button not found")
        button.tap()
    }

    func returnToWallet() {
        dismissKeyboardIfPresent()
        let home = app.buttons["wallet.scanButton"]
        for _ in 0..<6 {
            if home.exists && home.isHittable { return }
            let identifiers = ["wallet.presentationDone", "issuance-done", "wallet-detail-close",
                "wallet.detailsBack", "wallet.flowBack", "wallet.external.close"]
            if let button = identifiers.map({ app.buttons[$0] }).first(where: { $0.exists && $0.isHittable }) {
                button.tap()
            } else {
                tapNavigationBack()
            }
        }
        XCTAssertTrue(home.exists && home.isHittable, "Wallet home did not appear: \(app.debugDescription)")
    }

    func openScanner() {
        returnToWallet()
        tapButton(identifier: "wallet.scanButton", fallbackLabel: "Scan QR code")
        XCTAssertTrue(app.buttons["wallet.scanMode"].waitForExistence(timeout: 10))
        // A simulator may report the camera unavailable; manual entry is then already offered.
        if !textInput(identifier: "wallet.scanInput", fallbackLabel: "Credential offer or request").exists {
            tapButton(identifier: "wallet.scanMode", fallbackLabel: "Enter a link")
        }
        XCTAssertTrue(textInput(identifier: "wallet.scanInput", fallbackLabel: "Credential offer or request").waitForExistence(timeout: 10))
    }

    /// Exercise the same automatic routing as a pasted or scanned user link.
    func openWalletLink(_ value: String) {
        openScanner()
        replaceText(in: textInput(identifier: "wallet.scanInput", fallbackLabel: "Credential offer or request"), value: value)
        tapButton(identifier: "wallet.scanContinue", fallbackLabel: "Continue")
    }

    func replaceText(in element: XCUIElement, value: String) {
        XCTAssertTrue(element.waitForExistence(timeout: 20), "Input element not found")
        let enabled = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: element)
        guard XCTWaiter.wait(for: [enabled], timeout: 20) == .completed else {
            XCTFail("Input did not become enabled: \(element.identifier)")
            return
        }
        makeHittable(element)
        XCTAssertTrue(element.isHittable, "Input element is not hittable")
        element.tap()

        if let currentValue = element.value as? String {
            let placeholder = element.placeholderValue ?? ""
            if !currentValue.isEmpty && currentValue != placeholder {
                element.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: currentValue.count))
            }
        }

        let identifier = element.identifier
        let scannerInput = identifier == "wallet.scanInput"
        element.typeText(value)
        // Scanner Go starts resolution. Keep the draft editable until the caller chooses Continue.
        // PIN fields submit on the fourth digit and can change their AX identity before typing returns.
        if !scannerInput && identifier != "wallet.pinInput" && identifier != "wallet.pinConfirmationInput" {
            submitFocusedInput(element)
        }
    }

    private func makeHittable(_ element: XCUIElement) {
        guard element.exists, !element.isHittable else {
            return
        }

        for _ in 0..<6 where !element.isHittable {
            app.swipeUp()
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        for _ in 0..<6 where !element.isHittable {
            app.swipeDown()
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
    }

    private func dismissKeyboardIfPresent() {
        guard app.keyboards.firstMatch.exists else {
            return
        }

        let doneButton = app.toolbars.buttons["Done"]
        if doneButton.exists && doneButton.isHittable {
            doneButton.tap()
        } else {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.1)).tap()
        }
        RunLoop.current.run(until: Date().addingTimeInterval(0.3))
    }

    private func submitFocusedInput(_ element: XCUIElement) {
        guard element.exists && element.isEnabled else { return }
        let numericDone = app.buttons["wallet.pinKeyboardAction"]
        if numericDone.exists && numericDone.isEnabled && numericDone.isHittable {
            numericDone.tap()
            return
        }
        let doneButton = app.toolbars.buttons["Done"]
        if doneButton.exists && doneButton.isHittable {
            doneButton.tap()
        } else {
            element.typeText(XCUIKeyboardKey.return.rawValue)
        }
        RunLoop.current.run(until: Date().addingTimeInterval(0.3))
    }

    private func firstElement(identifierPrefix: String) -> XCUIElement {
        let predicate = NSPredicate(format: "identifier BEGINSWITH %@", identifierPrefix)
        return app.descendants(matching: .any).matching(predicate).firstMatch
    }

    private func waitForHittableElement(identifierPrefix: String, timeout: TimeInterval) -> XCUIElement? {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if let hittable = firstHittableElement(identifierPrefix: identifierPrefix) {
                return hittable
            }
            if firstElement(identifierPrefix: identifierPrefix).exists {
                app.swipeUp()
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        return firstHittableElement(identifierPrefix: identifierPrefix)
    }

    private func firstHittableElement(identifierPrefix: String) -> XCUIElement? {
        let predicate = NSPredicate(format: "identifier BEGINSWITH %@", identifierPrefix)
        return app.descendants(matching: .any)
            .matching(predicate)
            .allElementsBoundByIndex
            .first { element in
                guard element.exists, element.isHittable, element.isEnabled else { return false }
                return true
            }
    }

    private func firstExisting(_ elements: [XCUIElement]) -> XCUIElement {
        for element in elements where element.exists {
            return element
        }
        return elements[0]
    }

    func unlockWallet() {
        let pinInput = textInput(identifier: "wallet.pinInput", fallbackLabel: "PIN")
        guard pinInput.waitForExistence(timeout: 10) else { return }
        let creating = app.staticTexts["Step 1 of 2"].exists
        replaceText(in: pinInput, value: pin)
        if creating {
            let confirmation = textInput(identifier: "wallet.pinConfirmationInput", fallbackLabel: "Confirm PIN")
            XCTAssertTrue(confirmation.waitForExistence(timeout: 10), app.debugDescription)
            XCTAssertFalse(pinInput.exists, "Choose and Confirm must be separate screens")
            replaceText(in: confirmation, value: pin)
        }
        let keyboardDismissed = XCTNSPredicateExpectation(
            predicate: NSPredicate { [app] _, _ in !app.keyboards.firstMatch.exists }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [keyboardDismissed], timeout: 10), .completed,
            "PIN verification must dismiss its keyboard before another flow starts")
    }
}

private extension String {
    var identifierSegment: String {
        map { $0.isLetter || $0.isNumber ? String($0) : "_" }.joined()
    }
}
