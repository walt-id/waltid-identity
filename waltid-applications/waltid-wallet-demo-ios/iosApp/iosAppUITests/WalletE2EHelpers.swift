import Foundation
import XCTest

struct WalletE2EHttpResponse {
    let finalURL: URL
    let statusCode: Int
    let body: String
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

final class WalletE2EClient {
    private let session: URLSession

    init() {
        let config = URLSessionConfiguration.ephemeral
        config.httpShouldSetCookies = true
        config.httpCookieAcceptPolicy = .always
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        session = URLSession(configuration: config)
    }

    func jsonRequest(
        url: URL,
        method: String = "GET",
        headers: [String: String] = [:],
        body: Data? = nil,
        allow3xx: Bool = false
    ) async throws -> [String: Any] {
        let response = try await textRequest(url: url, method: method, headers: headers, body: body, allow3xx: allow3xx)
        if response.body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return [:]
        }
        let object = try JSONSerialization.jsonObject(with: Data(response.body.utf8), options: [])
        guard let dict = object as? [String: Any] else {
            throw NSError(domain: "WalletE2E", code: 1, userInfo: [NSLocalizedDescriptionKey: "Response is not JSON object: \(response.body)"])
        }
        return dict
    }

    func textRequest(
        url: URL,
        method: String = "GET",
        headers: [String: String] = [:],
        body: Data? = nil,
        allow3xx: Bool = false
    ) async throws -> WalletE2EHttpResponse {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body
        request.setValue("*/*", forHTTPHeaderField: "Accept")
        headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NSError(domain: "WalletE2E", code: 2, userInfo: [NSLocalizedDescriptionKey: "Non-HTTP response for \(url)"])
        }

        let text = String(data: data, encoding: .utf8) ?? ""
        let successRange = allow3xx ? (200...399) : (200...299)
        guard successRange.contains(httpResponse.statusCode) else {
            throw NSError(
                domain: "WalletE2E",
                code: httpResponse.statusCode,
                userInfo: [NSLocalizedDescriptionKey: "HTTP \(httpResponse.statusCode) from \(url): \(text)"]
            )
        }

        return WalletE2EHttpResponse(
            finalURL: httpResponse.url ?? url,
            statusCode: httpResponse.statusCode,
            body: text
        )
    }

    func formRequest(url: URL, fields: [String: String]) async throws -> WalletE2EHttpResponse {
        let encoded = fields
            .map { "\(Self.urlEncode($0.key))=\(Self.urlEncode($0.value))" }
            .joined(separator: "&")
        let headers = ["Content-Type": "application/x-www-form-urlencoded"]
        return try await textRequest(url: url, method: "POST", headers: headers, body: Data(encoded.utf8), allow3xx: true)
    }

    static func urlEncode(_ value: String) -> String {
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
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

func jsonString(_ object: Any) throws -> String {
    let data = try JSONSerialization.data(withJSONObject: object, options: [])
    return String(decoding: data, as: UTF8.self)
}

func unescapedHTML(_ value: String) -> String {
    guard let data = value.data(using: .utf8) else { return value }
    let options: [NSAttributedString.DocumentReadingOptionKey: Any] = [
        .documentType: NSAttributedString.DocumentType.html,
        .characterEncoding: String.Encoding.utf8.rawValue,
    ]
    return (try? NSAttributedString(data: data, options: options, documentAttributes: nil).string) ?? value
}

func firstMatch(_ text: String, pattern: String) -> String? {
    guard let regex = try? NSRegularExpression(pattern: pattern, options: [.dotMatchesLineSeparators, .caseInsensitive]) else {
        return nil
    }
    let ns = text as NSString
    let range = NSRange(location: 0, length: ns.length)
    guard let match = regex.firstMatch(in: text, options: [], range: range), match.numberOfRanges > 1 else {
        return nil
    }
    return ns.substring(with: match.range(at: 1))
}

func resolveURL(base: URL, action: String) -> URL {
    if let absolute = URL(string: action), absolute.scheme != nil {
        return absolute
    }
    return URL(string: action, relativeTo: base)?.absoluteURL ?? base
}
