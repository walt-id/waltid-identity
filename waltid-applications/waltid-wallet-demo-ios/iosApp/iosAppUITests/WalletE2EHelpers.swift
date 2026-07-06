import Foundation
import XCTest
import TestHelpers

@MainActor
final class WalletE2EUI {
    let app: XCUIApplication

    init(app: XCUIApplication) {
        self.app = app
    }

    func launch(attestation: [String: String] = [:]) {
        app.launchEnvironment["E2E_WALLET_ID"] = app.launchEnvironment["E2E_WALLET_ID"] ?? "e2e-\(UUID().uuidString)"
        for (key, value) in attestation {
            app.launchEnvironment[key] = value
        }
        app.launch()
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

    func replaceText(in element: XCUIElement, value: String) {
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

    private func firstExisting(_ elements: [XCUIElement]) -> XCUIElement {
        for element in elements where element.exists {
            return element
        }
        return elements[0]
    }
}
