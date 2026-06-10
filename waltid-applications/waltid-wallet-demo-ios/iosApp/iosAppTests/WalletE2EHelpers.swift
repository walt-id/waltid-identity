import Foundation

// Minimal EUDI backend helpers for unit tests
// Extracted from UI tests - only includes what's needed

struct WalletE2EHttpResponse {
    let finalURL: URL
    let statusCode: Int
    let body: String
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

final class EudiOfferFlow {
    private let client: WalletE2EClient

    init(client: WalletE2EClient) {
        self.client = client
    }

    func generate(credentialID: String) async throws -> String {
        let entrypoint = URL(string: "https://issuer.eudiw.dev/credential_offer")!
        let backendGenerate = URL(string: "https://backend.issuer.eudiw.dev/credential_offer")!
        let backendAuthorize = URL(string: "https://backend.issuer.eudiw.dev/form_authorize_generate")!

        let entry = try await client.textRequest(url: entrypoint, allow3xx: true)
        let redirectAction = resolveURL(base: entry.finalURL, action: extractFormAction(page: entry.body, formID: "redirect_form"))
        let redirectPayload = extractPayload(page: entry.body)
        _ = try await client.formRequest(url: redirectAction, fields: ["payload": redirectPayload])

        let preauthRedirect = try await client.formRequest(url: backendGenerate, fields: [
            credentialID: credentialID,
            "Authorization Code Grant": "pre_auth_code",
            "credential_offer_URI": "openid-credential-offer://",
            "proceed": "Submit",
        ])

        let displayAction = resolveURL(base: preauthRedirect.finalURL, action: extractFormAction(page: preauthRedirect.body, formID: "redirect_form"))
        let displayPayload = extractPayload(page: preauthRedirect.body)
        let displayPage = try await client.formRequest(url: displayAction, fields: ["payload": displayPayload])

        let countryAction = resolveURL(base: displayPage.finalURL, action: extractFormAction(page: displayPage.body, formID: "selectCountryForm"))
        let authorizeRedirect = try await client.formRequest(url: countryAction, fields: [
            "birthdate": "1990-01-01",
            "family_name": "Tester",
            "given_name": "Alice",
            "nationalities[0][country_code]": "DE",
            "place_of_birth[0][country]": "DE",
            "place_of_birth[0][region]": "Berlin",
            "place_of_birth[0][locality]": "Berlin",
            "proceed": "Confirm",
        ])

        let authAction = resolveURL(base: authorizeRedirect.finalURL, action: extractFormAction(page: authorizeRedirect.body, formID: "redirect_form"))
        let authPayload = extractPayload(page: authorizeRedirect.body)
        let authPage = try await client.formRequest(url: authAction, fields: ["payload": authPayload])

        let userID = extractUserID(page: authPage.body)
        let offerRedirect = try await client.formRequest(url: backendAuthorize, fields: [
            "user_id": userID,
            "proceed": "Authorize",
        ])

        let payloadRaw = extractPayload(page: offerRedirect.body)
        guard let payloadData = payloadRaw.data(using: .utf8),
              let payloadJSON = try JSONSerialization.jsonObject(with: payloadData) as? [String: Any] else {
            throw NSError(domain: "WalletE2E", code: 210, userInfo: [NSLocalizedDescriptionKey: "Invalid final payload"])
        }

        let txCodeRaw = payloadJSON["tx_code"]
        guard let txCodeValue = txCodeRaw as? String ?? (txCodeRaw as? NSNumber)?.stringValue,
              let urlData = payloadJSON["url_data"] as? String else {
            throw NSError(domain: "WalletE2E", code: 211, userInfo: [NSLocalizedDescriptionKey: "Missing tx_code or url_data in final payload"])
        }
        guard let offerURI = URL(string: urlData),
              let components = URLComponents(url: offerURI, resolvingAgainstBaseURL: false),
              let credentialOfferRaw = components.queryItems?.first(where: { $0.name == "credential_offer" })?.value,
              let credentialData = credentialOfferRaw.data(using: .utf8),
              var offerJSON = try JSONSerialization.jsonObject(with: credentialData) as? [String: Any] else {
            throw NSError(domain: "WalletE2E", code: 211, userInfo: [NSLocalizedDescriptionKey: "Missing credential_offer payload"])
        }

        guard var grants = offerJSON["grants"] as? [String: Any],
              var preAuth = grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"] as? [String: Any] else {
            throw NSError(domain: "WalletE2E", code: 212, userInfo: [NSLocalizedDescriptionKey: "Missing pre-authorized grant"])
        }

        var txCode = (preAuth["tx_code"] as? [String: Any]) ?? [:]
        txCode["value"] = txCodeValue
        preAuth["tx_code"] = txCode
        grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"] = preAuth
        offerJSON["grants"] = grants

        let updated = try jsonString(offerJSON)
        var out = URLComponents()
        out.scheme = offerURI.scheme ?? "openid-credential-offer"
        out.host = "credential_offer"
        out.queryItems = [URLQueryItem(name: "credential_offer", value: updated)]

        guard let finalURL = out.url?.absoluteString else {
            throw NSError(domain: "WalletE2E", code: 213, userInfo: [NSLocalizedDescriptionKey: "Failed to build offer URL"])
        }
        return finalURL
    }

    private func extractPayload(page: String) -> String {
        guard let raw = firstMatch(page, pattern: "name=\\\"payload\\\"\\s+value='(.*?)'\\s*>") else {
            fatalError("Hidden payload input not found")
        }
        return unescapedHTML(raw)
    }

    private func extractFormAction(page: String, formID: String) -> String {
        let pattern = "<form\\s+id=\\\"\(NSRegularExpression.escapedPattern(for: formID))\\\"[^>]*action=\\\"([^\\\"]+)\\\""
        guard let raw = firstMatch(page, pattern: pattern) else {
            fatalError("Form action not found for id=\(formID)")
        }
        return unescapedHTML(raw)
    }

    private func extractUserID(page: String) -> String {
        if let v = firstMatch(page, pattern: "name=\\\"user_id\\\"\\s+value=\\\"([^\\\"]+)\\\"") {
            return v
        }
        if let v = firstMatch(page, pattern: "value=\\\"([a-f0-9\\-]{36})\\\"\\s+name=\\\"user_id\\\"") {
            return v
        }
        fatalError("user_id not found on authorization page")
    }
}

// MARK: - Helper Functions

func jsonString(_ object: Any) throws -> String {
    let data = try JSONSerialization.data(withJSONObject: object, options: [])
    return String(data: data, encoding: .utf8) ?? "{}"
}

func unescapedHTML(_ value: String) -> String {
    value
        .replacingOccurrences(of: "&quot;", with: "\"")
        .replacingOccurrences(of: "&#x27;", with: "'")
        .replacingOccurrences(of: "&amp;", with: "&")
        .replacingOccurrences(of: "&lt;", with: "<")
        .replacingOccurrences(of: "&gt;", with: ">")
}

func firstMatch(_ text: String, pattern: String) -> String? {
    guard let regex = try? NSRegularExpression(pattern: pattern, options: []),
          let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
          match.numberOfRanges >= 2,
          let range = Range(match.range(at: 1), in: text) else {
        return nil
    }
    return String(text[range])
}

func resolveURL(base: URL, action: String) -> URL {
    guard let resolved = URL(string: action, relativeTo: base)?.absoluteURL else {
        fatalError("Cannot resolve action=\(action) relative to base=\(base)")
    }
    return resolved
}
