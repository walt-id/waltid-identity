import Foundation
import XCTest
import TestHelpers

@MainActor
final class WalletE2EUI {
    let app: XCUIApplication

    init(app: XCUIApplication) {
        self.app = app
    }

    func launch(attestation: [String: String] = [:], environment: [String: String] = [:]) {
        app.launchEnvironment["E2E_WALLET_ID"] = app.launchEnvironment["E2E_WALLET_ID"] ?? "e2e-\(UUID().uuidString)"
        for (key, value) in attestation {
            app.launchEnvironment[key] = value
        }
        for (key, value) in environment {
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

    func openDeepLink(_ value: String) {
        guard let url = URL(string: value) else {
            XCTFail("Invalid deep link URL: \(value)")
            return
        }

        guard #available(iOS 16.4, *) else {
            XCTFail("Opening deep links from UI tests requires iOS 16.4 or newer")
            return
        }

        app.open(url)
        app.activate()
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
        let element = firstElement(identifierPrefix: identifierPrefix)
        XCTAssertTrue(element.waitForExistence(timeout: timeout), "Element not found with identifier prefix: \(identifierPrefix)")
        makeHittable(element)
        XCTAssertTrue(element.isHittable, "Element is not hittable with identifier prefix: \(identifierPrefix)")
        element.tap()
    }

    func claimImageIdentifier(path: String) -> String {
        "wallet.claimImage.\(path.identifierSegment)"
    }

    func tapNavigationBack() {
        let button = app.navigationBars.buttons.firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: 20), "Navigation back button not found")
        button.tap()
    }

    func tapTab(label: String) {
        let tab = app.tabBars.buttons[label]
        XCTAssertTrue(tab.waitForExistence(timeout: 20), "Tab not found: \(label)")
        XCTAssertTrue(tab.isHittable, "Tab is not hittable: \(label)")
        tab.tap()
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

    private func firstElement(identifierPrefix: String) -> XCUIElement {
        let predicate = NSPredicate(format: "identifier BEGINSWITH %@", identifierPrefix)
        return app.descendants(matching: .any).matching(predicate).firstMatch
    }

    private func firstExisting(_ elements: [XCUIElement]) -> XCUIElement {
        for element in elements where element.exists {
            return element
        }
        return elements[0]
    }
}

private extension String {
    var identifierSegment: String {
        map { $0.isLetter || $0.isNumber ? String($0) : "_" }.joined()
    }
}
