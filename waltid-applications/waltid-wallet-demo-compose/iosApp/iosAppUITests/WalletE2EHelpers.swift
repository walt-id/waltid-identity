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

    func tapButton(identifier: String, fallbackLabel: String) {
        let targetButton = button(identifier: identifier, fallbackLabel: fallbackLabel)
        XCTAssertTrue(targetButton.waitForExistence(timeout: 20), "Button not found: \(identifier)")
        makeHittable(targetButton)
        XCTAssertTrue(targetButton.isHittable, "Button is not hittable: \(identifier)")
        targetButton.tap()
    }

    private func replaceText(in element: XCUIElement, value: String) {
        XCTAssertTrue(element.waitForExistence(timeout: 20), "Input element not found")
        makeHittable(element)
        XCTAssertTrue(element.isHittable, "Input element is not hittable")
        element.tap()

        if let currentValue = element.value as? String {
            let placeholder = element.placeholderValue ?? ""
            if !currentValue.isEmpty && currentValue != placeholder {
                element.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: currentValue.count))
            }
        }

        element.typeText(value)
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
