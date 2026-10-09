import Foundation
import XCTest

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
        XCTAssertTrue(app.buttons["wallet.keySetupEdit.Storage"].waitForExistence(timeout: 10))
        XCTAssertEqual(button.label, "Create signing key")
        button.tap()
    }

    func launch(environment: [String: String] = [:], initializeSigningIdentity: Bool = true) {
        app.launchEnvironment["WALLET_SIGNING_PROTECTION_MODE"] =
            app.launchEnvironment["WALLET_SIGNING_PROTECTION_MODE"] ?? "disabled"
        for (key, value) in environment {
            app.launchEnvironment[key] = value
        }
        app.launch()
        unlockWallet()
        if initializeSigningIdentity { completeKeySetupIfNeeded() }
    }

    func launch(attestation: [String: String]) {
        launch(environment: attestation)
    }

    func launchExpectingLoginAndUnlock(
        environment: [String: String],
        walletReadyTimeout: TimeInterval = 60
    ) {
        app.launchEnvironment["WALLET_SIGNING_PROTECTION_MODE"] =
            app.launchEnvironment["WALLET_SIGNING_PROTECTION_MODE"] ?? "disabled"
        for (key, value) in environment {
            app.launchEnvironment[key] = value
        }
        app.launch()

        let pinInput = textInput(identifier: "wallet.pinInput", fallbackLabel: "PIN")
        XCTAssertTrue(pinInput.waitForExistence(timeout: 10), "PIN input not found after relaunch")
        XCTAssertFalse(app.staticTexts["Step 1 of 2"].exists, "PIN setup was shown after relaunch")
        unlockWallet()

        let readyStatus = waitUntilWalletReady(timeout: walletReadyTimeout)
        XCTAssertEqual(
            readyStatus,
            "Wallet ready",
            "Persisted PIN did not unlock the wallet, status: \(readyStatus ?? "nil")"
        )
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

    func waitForTextInput(
        identifier: String,
        fallbackLabel: String,
        timeout: TimeInterval
    ) -> XCUIElement? {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            let input = textInput(identifier: identifier, fallbackLabel: fallbackLabel)
            if input.exists { return input }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        return nil
    }

    func button(identifier: String, fallbackLabel: String) -> XCUIElement {
        firstExisting([
            app.buttons[identifier],
            app.buttons[fallbackLabel],
        ])
    }

    func waitForOfferReview(timeout: TimeInterval) -> Bool {
        app.buttons["wallet.offerAcceptButton"].waitForExistence(timeout: timeout)
    }

    func waitForPresentationReview(timeout: TimeInterval) -> Bool {
        app.buttons["wallet.presentationSubmitButton"].waitForExistence(timeout: timeout)
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

    func waitUntilWalletReady(timeout: TimeInterval) -> String? {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if let failed = latestStatus(prefixes: ["Bootstrap failed"]) {
                return failed
            }
            if latestStatus(prefixes: ["Wallet ready"]) != nil {
                return "Wallet ready"
            }
            let bootstrapping = latestStatus(prefixes: ["Bootstrapping"]) != nil
            let pinVisible = textInput(identifier: "wallet.pinInput", fallbackLabel: "PIN").exists
            let settings = button(identifier: "wallet.settingsButton", fallbackLabel: "Settings")
            if !bootstrapping && !pinVisible && settings.exists {
                return "Wallet ready"
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.4))
        }
        return latestStatus(prefixes: ["Wallet ready", "Bootstrap failed", "Bootstrapping"])
    }

    func latestStatus(prefixes: [String]) -> String? {
        // Read one snapshot: the status banner can disappear between live element queries.
        let snapshot: any XCUIElementSnapshot
        do {
            snapshot = try app.snapshot()
        } catch {
            XCTFail("Could not capture wallet status: \(error)")
            return nil
        }

        var pending = [snapshot]
        var taggedValues: [String] = []
        var labels: [String] = []
        while let element = pending.popLast() {
            if element.identifier == "wallet.status" {
                taggedValues += [element.label, element.value as? String].compactMap { $0 }.filter { !$0.isEmpty }
            }
            if element.elementType == .staticText {
                labels.append(element.label)
            }
            pending.append(contentsOf: element.children.reversed())
        }
        for candidates in [taggedValues, labels] {
            for prefix in prefixes {
                if let match = candidates.first(where: { $0.hasPrefix(prefix) }) {
                    return match
                }
            }
        }
        return nil
    }

    func openDeepLink(_ value: String) {
        guard let url = URL(string: value) else {
            XCTFail("Invalid deep link URL")
            return
        }

        // Route through iOS to preserve the running wallet; app.open launches it again.
        XCUIDevice.shared.system.open(url)
        guard app.wait(for: .runningForeground, timeout: 10) else {
            XCTFail("The URL handoff did not foreground the Compose wallet")
            return
        }

        let pinInput = textInput(identifier: "wallet.pinInput", fallbackLabel: "PIN")
        if pinInput.waitForExistence(timeout: 2) {
            unlockWallet()
            XCTAssertEqual(waitUntilWalletReady(timeout: 60), "Wallet ready")
        }
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

    func returnToWallet() {
        dismissKeyboard()
        let home = app.buttons["wallet.scanButton"]
        for _ in 0..<6 {
            if home.exists && home.isHittable { return }
            let identifiers = ["wallet.presentationDone", "issuance-done", "wallet-detail-close",
                "wallet.detailsBack", "wallet.flowBack", "wallet.external.close"]
            guard let button = identifiers.map({ app.buttons[$0] }).first(where: { $0.exists && $0.isHittable }) else {
                XCTFail("No action returns to wallet home: \(app.debugDescription)")
                return
            }
            button.tap()
        }
        XCTAssertTrue(home.exists && home.isHittable, "Wallet home did not appear: \(app.debugDescription)")
    }

    func openWalletLink(_ value: String) {
        returnToWallet()
        tapButton(identifier: "wallet.scanButton", fallbackLabel: "Scan QR code")
        tapButton(identifier: "wallet.scanMode", fallbackLabel: "Enter a link")
        replaceText(in: textInput(identifier: "wallet.scanInput", fallbackLabel: "Credential offer or request"), value: value)
        tapButton(identifier: "wallet.scanContinue", fallbackLabel: "Continue")
    }

    func tapButton(identifier: String, fallbackLabel: String, useCoordinateTap: Bool = false) {
        let tagged = app.buttons.matching(identifier: identifier)
        if tagged.firstMatch.waitForExistence(timeout: 20) {
            // Navigation 3 retains both pages during motion; wait for the active page.
            let unique = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in tagged.count == 1 }, object: nil)
            XCTAssertEqual(XCTWaiter.wait(for: [unique], timeout: 5), .completed,
                "Button remained ambiguous after navigation: \(identifier)")
        }
        let targetButton = button(identifier: identifier, fallbackLabel: fallbackLabel)
        XCTAssertTrue(targetButton.waitForExistence(timeout: 20), "Button not found: \(identifier)")
        if !targetButton.isHittable {
            dismissKeyboard()
            makeHittable(targetButton)
        }
        XCTAssertTrue(targetButton.isHittable, "Button is not hittable: \(identifier)")
        XCTAssertTrue(targetButton.isEnabled, "Button is not enabled: \(identifier)")
        if useCoordinateTap {
            targetButton.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        } else {
            targetButton.tap()
        }
    }

    func replaceText(in element: XCUIElement, value: String, dismiss: Bool = true) {
        XCTAssertTrue(element.waitForExistence(timeout: 20), "Input element not found")
        makeHittable(element)
        XCTAssertTrue(element.isHittable, "Input element is not hittable")
        guard focusTextInput(element) else {
            XCTFail("Input element did not accept keyboard focus")
            return
        }

        if let currentValue = element.value as? String {
            let placeholder = element.placeholderValue ?? ""
            if !currentValue.isEmpty && currentValue != placeholder {
                element.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: max(currentValue.count, 32)))
            }
        }

        let identifier = element.identifier
        let scannerInput = identifier == "wallet.scanInput"
        element.typeText(value)
        // Scanner Go now starts resolution. Keep explicit Continue tests in
        // charge of submission, including assertions while the IME is visible.
        if dismiss && !scannerInput && identifier != "wallet.pinInput" && identifier != "wallet.pinConfirmationInput" {
            dismissKeyboard(focusedElement: element)
        }
    }

    private func focusTextInput(_ element: XCUIElement, timeout: TimeInterval = 15) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        var useCoordinateTap = false

        repeat {
            makeHittable(element)
            guard element.isHittable else {
                RunLoop.current.run(until: Date().addingTimeInterval(0.2))
                continue
            }

            if useCoordinateTap {
                element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            } else {
                element.tap()
                useCoordinateTap = true
            }

            if waitForKeyboardFocus(in: element, timeout: 1.5) {
                return true
            }

            // Compose iOS exposes OutlinedTextField as a TextView that often never sets
            // hasKeyboardFocus, especially on the first cold-start PIN screen.
            if app.keyboards.firstMatch.waitForExistence(timeout: 1) {
                return true
            }

            app.activate()
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        } while Date() < deadline

        return false
    }

    private func waitForKeyboardFocus(in element: XCUIElement, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)

        repeat {
            if hasKeyboardFocus(in: element) {
                return true
            }

            RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        } while Date() < deadline

        return false
    }

    private func hasKeyboardFocus(in element: XCUIElement) -> Bool {
        let predicate = NSPredicate(format: "hasKeyboardFocus == true")
        if predicate.evaluate(with: element) {
            return true
        }
        if element.descendants(matching: .any).matching(predicate).firstMatch.exists {
            return true
        }

        return app.descendants(matching: .any)
            .matching(predicate)
            .matching(identifier: element.identifier)
            .firstMatch
            .exists
    }

    private func makeHittable(_ element: XCUIElement) {
        guard element.exists, !element.isHittable else {
            return
        }

        for _ in 0..<8 where !element.isHittable {
            app.swipeUp()
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
    }

    private func dismissKeyboard(focusedElement: XCUIElement? = nil) {
        guard app.keyboards.firstMatch.exists else { return }

        let doneButton = app.toolbars.buttons["Done"]
        if doneButton.exists && doneButton.isHittable {
            doneButton.tap()
        } else if let focusedElement, hasKeyboardFocus(in: focusedElement) {
            focusedElement.typeText(XCUIKeyboardKey.return.rawValue)
        } else {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.1)).tap()
        }
        RunLoop.current.run(until: Date().addingTimeInterval(0.3))
    }

    private func unlockWallet() {
        let pinInput = textInput(identifier: "wallet.pinInput", fallbackLabel: "PIN")
        guard pinInput.waitForExistence(timeout: 10) else { return }
        let creating = app.staticTexts["Step 1 of 2"].exists
        replaceText(in: pinInput, value: pin, dismiss: false)
        if creating {
            let confirmation = textInput(identifier: "wallet.pinConfirmationInput", fallbackLabel: "Confirm PIN")
            XCTAssertTrue(confirmation.waitForExistence(timeout: 10), app.debugDescription)
            XCTAssertFalse(pinInput.exists, "Choose and Confirm must be separate screens")
            replaceText(in: confirmation, value: pin, dismiss: false)
        }
    }

    private func firstExisting(_ elements: [XCUIElement]) -> XCUIElement {
        for element in elements where element.exists {
            return element
        }
        return elements[0]
    }
}
