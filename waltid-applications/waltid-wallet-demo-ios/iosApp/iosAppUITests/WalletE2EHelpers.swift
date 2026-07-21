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
        if identifierPrefix == "wallet.credentialCard.", app.buttons["Done"].exists {
            app.buttons["Done"].tap()
        }
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
        let tab = app.tabBars.buttons[label]
        dismissKeyboardIfPresent()
        if tab.exists {
            makeHittable(tab)
            tab.tap()
        } else {
            returnToWalletHome()
            switch label {
            case "Credentials":
                break
            case "Receive":
                tapButton(identifier: "wallet.home.receive", fallbackLabel: "Receive")
                tapButton(identifier: "wallet.manualEntry", fallbackLabel: "Enter link manually")
            case "Present":
                tapButton(identifier: "wallet.home.present", fallbackLabel: "Present")
                tapButton(identifier: "wallet.manualEntry", fallbackLabel: "Enter link manually")
            default:
                XCTFail("Unknown wallet destination: \(label)")
            }
        }
        XCTAssertTrue(waitForTabContent(label: label, timeout: 5), "Tab content did not become visible: \(label)")
    }

    private func returnToWalletHome() {
        let done = app.buttons["Done"]
        if done.exists && done.isHittable {
            done.tap()
        }
        let cancel = app.buttons["Cancel"]
        if !app.buttons["wallet.home.receive"].exists, cancel.exists, cancel.isHittable {
            cancel.tap()
        }
        _ = app.buttons["wallet.home.receive"].waitForExistence(timeout: 5)
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

    private func tapTabCoordinate(label: String) {
        let xOffset: CGFloat
        switch label {
        case "Credentials":
            xOffset = 1.0 / 6.0
        case "Receive":
            xOffset = 3.0 / 6.0
        case "Present":
            xOffset = 5.0 / 6.0
        default:
            XCTFail("Unknown tab: \(label)")
            return
        }
        let tabBar = app.tabBars.firstMatch
        if tabBar.exists {
            tabBar.coordinate(withNormalizedOffset: CGVector(dx: xOffset, dy: 0.5)).tap()
        } else {
            app.coordinate(withNormalizedOffset: CGVector(dx: xOffset, dy: 0.95)).tap()
        }
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
            return app.buttons["wallet.home.receive"].exists
                && (app.staticTexts["No credentials yet"].exists
                || app.staticTexts["Credential details"].exists
                || firstHittableElement(identifierPrefix: "wallet.credentialCard.") != nil)
        case "Receive":
            return textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL").isHittable
                || app.staticTexts["Add this credential?"].exists
                || app.staticTexts["Credential details"].exists
        case "Present":
            return textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL").isHittable
                || app.staticTexts["Share this information?"].exists
                || app.staticTexts["Credential details"].exists
                || app.staticTexts["Request completed"].exists
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
