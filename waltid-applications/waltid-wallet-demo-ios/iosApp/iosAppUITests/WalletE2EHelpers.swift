import Foundation
import XCTest
import TestHelpers

struct WalletE2ESetupError: LocalizedError {
    let message: String

    var errorDescription: String? {
        message
    }
}

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

    static func requireExplicitLocalEnterpriseRun() throws {
        let env = ProcessInfo.processInfo.environment
        guard envValue("E2E_LOCAL_ENTERPRISE", from: env) == "true" else {
            throw WalletE2ESetupError(message: """
            LocalEnterpriseBackendE2ETests are local-only and require a provisioned Enterprise stack.
            Run them through waltid-wallet-demo-ios/scripts/e2e-local-enterprise.sh.
            The script passes E2E_LOCAL_ENTERPRISE=true, validates local Enterprise resources, and configures the local ngrok-backed test environment.
            """)
        }
    }

    static func fromEnvironment() throws -> LocalEnterpriseConfig {
        let env = ProcessInfo.processInfo.environment
        let hostAliasDomain = envValue("HOST_ALIAS_DOMAIN", from: env)
            ?? envValue("E2E_HOST_ALIAS_DOMAIN", from: env)
            ?? ""
        guard !hostAliasDomain.isEmpty else {
            throw WalletE2ESetupError(message: "HOST_ALIAS_DOMAIN (or E2E_HOST_ALIAS_DOMAIN) is required. Set it in scripts/e2e.env and rerun scripts/e2e-local-enterprise.sh.")
        }

        let apiBase = envValue("E2E_API_BASE_URL", from: env) ?? "https://\(hostAliasDomain)"
        guard let apiBaseURL = URL(string: apiBase) else {
            throw WalletE2ESetupError(message: "Invalid E2E_API_BASE_URL: \(apiBase)")
        }

        guard let ngrokBaseURL = URL(string: "https://\(hostAliasDomain)") else {
            throw WalletE2ESetupError(message: "Invalid HOST_ALIAS_DOMAIN: \(hostAliasDomain)")
        }

        return LocalEnterpriseConfig(
            hostAliasDomain: hostAliasDomain,
            apiBaseURL: apiBaseURL,
            ngrokBaseURL: ngrokBaseURL,
            adminEmail: envValue("E2E_ADMIN_EMAIL", from: env) ?? "admin@walt.id",
            adminPassword: envValue("E2E_ADMIN_PASSWORD", from: env) ?? "admin123456",
            organization: envValue("E2E_ORGANIZATION", from: env) ?? "waltid",
            tenant: envValue("E2E_TENANT", from: env) ?? "waltid-tenant01",
            issuerProfile: envValue("E2E_ISSUER_PROFILE", from: env) ?? "issuer2.mdl-profile",
            verifier: envValue("E2E_VERIFIER", from: env) ?? "verifier2"
        )
    }

    static func isAttested() -> Bool {
        (envValue("E2E_ATTESTED", from: ProcessInfo.processInfo.environment) ?? "false").lowercased() == "true"
    }

    static func attestationBaseURL() -> String {
        envValue("E2E_ATTESTATION_BASE_URL", from: ProcessInfo.processInfo.environment) ?? "http://localhost:7500"
    }

    private static func envValue(_ name: String, from env: [String: String]) -> String? {
        env[name] ?? env["TEST_RUNNER_\(name)"]
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

    init(app: XCUIApplication) {
        self.app = app
    }

    func launch(attestation: [String: String] = [:]) {
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

    func replaceText(in element: XCUIElement, value: String) {
        XCTAssertTrue(element.waitForExistence(timeout: 20), "Input element not found")
        element.tap()

        if let currentValue = element.value as? String {
            let placeholder = element.placeholderValue ?? ""
            if !currentValue.isEmpty && currentValue != placeholder {
                element.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: currentValue.count))
            }
        }

        element.typeText(value)
    }
}
