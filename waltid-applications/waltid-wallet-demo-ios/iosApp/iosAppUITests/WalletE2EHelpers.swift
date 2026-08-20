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
        app.launchEnvironment["WALLET_BIOMETRIC_ENABLED"] =
            app.launchEnvironment["WALLET_BIOMETRIC_ENABLED"] ?? "false"
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
        if prefixes.contains("Wallet ready") {
            let scanAction = app.buttons["wallet.scanAction"]
            if scanAction.exists, scanAction.isEnabled {
                return "Wallet ready"
            }
        }
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
        var candidates = [
            app.textFields[identifier],
            app.secureTextFields[identifier],
            app.textViews[identifier],
            app.textFields[fallbackLabel],
            app.secureTextFields[fallbackLabel],
            app.textViews[fallbackLabel],
        ]
        if identifier == "wallet.offerInput" || identifier == "wallet.presentationInput" {
            candidates.append(app.textFields["wallet.scanInput"])
        }
        return firstExisting(candidates)
    }

    func tapButton(identifier: String, fallbackLabel: String) {
        var candidates = [
            app.buttons[identifier],
            app.buttons[fallbackLabel],
        ]
        if identifier == "wallet.receiveButton" || identifier == "wallet.presentButton" {
            candidates.append(app.buttons["wallet.scanSubmit"])
        }
        let button = firstExisting(candidates)
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
            XCTFail("Element not found or not hittable with identifier prefix: \(identifierPrefix)")
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
        let button = app.navigationBars.buttons.firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: 20), "Navigation back button not found")
        button.tap()
    }

    func tapTab(label: String) {
        dismissKeyboardIfPresent()
        if label == "Credentials" {
            let close = app.buttons["Close"].firstMatch
            if close.exists {
                makeHittable(close)
                close.tap()
            }
        } else {
            let close = app.buttons["Close"].firstMatch
            if close.exists && !app.buttons["wallet.scanSubmit"].exists {
                makeHittable(close)
                close.tap()
            }
            let scan = app.buttons["wallet.scanAction"]
            XCTAssertTrue(scan.waitForExistence(timeout: 10), "Scan action did not become visible")
            makeHittable(scan)
            scan.tap()
        }
        XCTAssertTrue(waitForTabContent(label: label, timeout: 5), "Tab content did not become visible: \(label)")
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
        submitFocusedInput(element)
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
        let doneButton = app.toolbars.buttons["Done"]
        if doneButton.exists && doneButton.isHittable {
            doneButton.tap()
        } else {
            element.typeText(XCUIKeyboardKey.return.rawValue)
        }
        RunLoop.current.run(until: Date().addingTimeInterval(0.3))
    }

    private func waitForTabContent(label: String, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if tabContentVisible(label: label) {
                return true
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        return tabContentVisible(label: label)
    }

    private func tabContentVisible(label: String) -> Bool {
        switch label {
        case "Credentials":
            return app.staticTexts["No credentials yet"].exists
                || app.staticTexts["Credential details"].exists
                || firstHittableElement(identifierPrefix: "wallet.credentialCard.") != nil
        case "Receive":
            return app.textFields["wallet.scanInput"].exists
        case "Present":
            return app.textFields["wallet.scanInput"].exists
        default:
            return false
        }
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
            .first { $0.exists && $0.isHittable }
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
