import Foundation
import XCTest

struct LocalEnterpriseConfig {
    let hostAliasDomain: String
    let apiBaseURL: URL
    let ngrokBaseURL: URL
    let adminEmail: String
    let adminPassword: String
    let organization: String
    let tenant: String
    let issuerProfile: String
    let verifier: String

    var tenantPath: String { "\(organization).\(tenant)" }

    static func fromEnvironment() -> LocalEnterpriseConfig {
        let env = ProcessInfo.processInfo.environment
        let hostAliasDomain = env["HOST_ALIAS_DOMAIN"] ?? env["E2E_HOST_ALIAS_DOMAIN"] ?? ""
        precondition(!hostAliasDomain.isEmpty, "HOST_ALIAS_DOMAIN (or E2E_HOST_ALIAS_DOMAIN) is required")

        let apiBase = env["E2E_API_BASE_URL"] ?? "https://\(hostAliasDomain)"
        guard let apiBaseURL = URL(string: apiBase) else {
            fatalError("Invalid E2E_API_BASE_URL: \(apiBase)")
        }

        guard let ngrokBaseURL = URL(string: "https://\(hostAliasDomain)") else {
            fatalError("Invalid HOST_ALIAS_DOMAIN: \(hostAliasDomain)")
        }

        return LocalEnterpriseConfig(
            hostAliasDomain: hostAliasDomain,
            apiBaseURL: apiBaseURL,
            ngrokBaseURL: ngrokBaseURL,
            adminEmail: env["E2E_ADMIN_EMAIL"] ?? "admin@walt.id",
            adminPassword: env["E2E_ADMIN_PASSWORD"] ?? "admin123456",
            organization: env["E2E_ORGANIZATION"] ?? "waltid",
            tenant: env["E2E_TENANT"] ?? "waltid-tenant01",
            issuerProfile: env["E2E_ISSUER_PROFILE"] ?? "issuer2.mdl-profile",
            verifier: env["E2E_VERIFIER"] ?? "verifier2"
        )
    }
}

struct EudiPublicConfig {
    let credentialID: String
    let offerURL: String?

    static func fromEnvironment() -> EudiPublicConfig {
        let env = ProcessInfo.processInfo.environment
        let offerURL = resolveOfferURL(from: env)

        return EudiPublicConfig(
            credentialID: env["E2E_CREDENTIAL_ID"] ?? "eu.europa.ec.eudi.pid_vc_sd_jwt",
            offerURL: offerURL
        )
    }

    private static func resolveOfferURL(from env: [String: String]) -> String? {
        if let direct = env["E2E_OFFER_URL"], !direct.isEmpty {
            return direct
        }

        if let b64 = env["E2E_OFFER_URL_B64"], !b64.isEmpty,
           let data = Data(base64Encoded: b64),
           let decoded = String(data: data, encoding: .utf8),
           !decoded.isEmpty {
            return decoded
        }

        // File-based injection only when explicitly configured via E2E_OFFER_URL_FILE
        if let path = env["E2E_OFFER_URL_FILE"],
           let data = try? Data(contentsOf: URL(fileURLWithPath: path)),
           var text = String(data: data, encoding: .utf8)?
             .trimmingCharacters(in: .whitespacesAndNewlines),
           !text.isEmpty {
            if path.hasSuffix(".b64"), let decoded = Data(base64Encoded: text), let decodedText = String(data: decoded, encoding: .utf8) {
                text = decodedText
            }
            if text.starts(with: "openid-credential-offer://") {
                return text
            }
        }

        return nil
    }
}

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
