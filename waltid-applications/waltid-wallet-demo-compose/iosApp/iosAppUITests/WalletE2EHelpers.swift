import Foundation
import XCTest

@MainActor
final class WalletE2EUI {
    let app: XCUIApplication
    private let pin = "1234"

    init(app: XCUIApplication) {
        self.app = app
    }

    func launch(environment: [String: String] = [:]) {
        for (key, value) in environment {
            app.launchEnvironment[key] = value
        }
        app.launch()
        unlockWallet()
    }

    func launch(attestation: [String: String]) {
        launch(environment: attestation)
    }

    func launchExpectingLogin(environment: [String: String]) {
        for (key, value) in environment {
            app.launchEnvironment[key] = value
        }
        app.launch()

        let pinInput = textInput(identifier: "wallet.pinInput", fallbackLabel: "PIN")
        XCTAssertTrue(pinInput.waitForExistence(timeout: 10), "PIN input not found after relaunch")
        let confirmation = textInput(identifier: "wallet.pinConfirmationInput", fallbackLabel: "Confirm PIN")
        XCTAssertFalse(confirmation.waitForExistence(timeout: 2), "PIN setup was shown after relaunch")
        unlockWallet()
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
        guard let url = URL(string: value) else {
            XCTFail("Invalid deep link URL: \(value)")
            return
        }

        app.open(url)
        app.activate()

        let pinInput = textInput(identifier: "wallet.pinInput", fallbackLabel: "PIN")
        if pinInput.waitForExistence(timeout: 2) {
            unlockWallet()
            _ = waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 60)
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

    func tapButton(identifier: String, fallbackLabel: String, useCoordinateTap: Bool = false) {
        let targetButton = button(identifier: identifier, fallbackLabel: fallbackLabel)
        XCTAssertTrue(targetButton.waitForExistence(timeout: 20), "Button not found: \(identifier)")
        dismissKeyboard()
        makeHittable(targetButton)
        XCTAssertTrue(targetButton.isHittable, "Button is not hittable: \(identifier)")
        XCTAssertTrue(targetButton.isEnabled, "Button is not enabled: \(identifier)")
        if useCoordinateTap {
            targetButton.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        } else {
            targetButton.tap()
        }
    }

    func replaceText(in element: XCUIElement, value: String) {
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

        element.typeText(value)
        dismissKeyboard(focusedElement: element)
    }

    private func focusTextInput(_ element: XCUIElement, timeout: TimeInterval = 8) -> Bool {
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

            if waitForKeyboardFocus(in: element, timeout: 1) {
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
        } else if let focusedElement {
            focusedElement.typeText(XCUIKeyboardKey.return.rawValue)
        } else {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.1)).tap()
        }
        RunLoop.current.run(until: Date().addingTimeInterval(0.3))
    }

    private func unlockWallet() {
        let pinInput = textInput(identifier: "wallet.pinInput", fallbackLabel: "PIN")
        guard pinInput.waitForExistence(timeout: 10) else {
            return
        }

        replaceText(in: pinInput, value: pin)

        let confirmation = textInput(identifier: "wallet.pinConfirmationInput", fallbackLabel: "Confirm PIN")
        if confirmation.waitForExistence(timeout: 2) {
            replaceText(in: confirmation, value: pin)
        }

        let submit = button(identifier: "wallet.pinSubmitButton", fallbackLabel: "Set PIN")
        XCTAssertTrue(submit.waitForExistence(timeout: 10), "PIN submit button not found")
        submit.tap()
    }

    private func firstExisting(_ elements: [XCUIElement]) -> XCUIElement {
        for element in elements where element.exists {
            return element
        }
        return elements[0]
    }
}
