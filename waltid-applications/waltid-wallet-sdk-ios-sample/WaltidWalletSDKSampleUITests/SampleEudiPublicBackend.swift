import Foundation

struct SampleEudiVerifierTransaction {
    let transactionID: String
    let authorizationRequestURI: String
}

final class SampleEudiPublicBackend {
    private let client = SampleWalletE2EClient()
    private let credentialID = "eu.europa.ec.eudi.pid_vc_sd_jwt"

    func generatePreAuthorizedOffer() async throws -> String {
        try await SampleEudiOfferFlow(client: client).generate(credentialID: credentialID)
    }

    func createVerifierTransaction() async throws -> SampleEudiVerifierTransaction {
        let payload: [String: Any] = [
            "dcql_query": buildDcqlQuery(),
            "nonce": UUID().uuidString,
            "request_uri_method": "post",
            "profile": "openid4vp",
            "authorization_request_uri": "openid4vp://",
        ]

        let response = try await client.jsonRequest(
            url: URL(string: "https://verifier-backend.eudiw.dev/ui/presentations/v2")!,
            method: "POST",
            headers: ["Content-Type": "application/json"],
            body: Data(try jsonString(payload).utf8)
        )

        guard let transactionID = response["transaction_id"] as? String,
              let authorizationRequestURI = response["authorization_request_uri"] as? String,
              !transactionID.isEmpty,
              !authorizationRequestURI.isEmpty else {
            throw NSError(
                domain: "SampleEudiPublicBackend",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Invalid verifier response: \(response)"]
            )
        }

        return SampleEudiVerifierTransaction(
            transactionID: transactionID,
            authorizationRequestURI: authorizationRequestURI
        )
    }

    func waitForVerifierSuccess(transactionID: String, timeoutSeconds: TimeInterval) async throws {
        let deadline = Date().addingTimeInterval(timeoutSeconds)
        let session = URLSession.shared

        while Date() < deadline {
            let url = URL(string: "https://verifier-backend.eudiw.dev/ui/presentations/\(transactionID)/events")!
            let (data, _) = try await session.data(from: url)

            guard let response = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let events = response["events"] as? [[String: Any]] else {
                try await Task.sleep(nanoseconds: 2_000_000_000)
                continue
            }

            for event in events {
                let eventName = (event["event"] as? String ?? "").lowercased()
                let cause = (event["cause"] as? String ?? "").lowercased()
                let combined = eventName + " " + cause

                if combined.contains("failed") ||
                    combined.contains("error") ||
                    combined.contains("invalid") ||
                    combined.contains("timed out") {
                    throw NSError(
                        domain: "SampleEudiPublicBackend",
                        code: 2,
                        userInfo: [NSLocalizedDescriptionKey: "Verifier failure: \(event)"]
                    )
                }
                if eventName.contains("wallet response posted") ||
                    eventName.contains("successful") ||
                    eventName.contains("verified") {
                    return
                }
            }

            try await Task.sleep(nanoseconds: 2_000_000_000)
        }

        throw NSError(
            domain: "SampleEudiPublicBackend",
            code: 3,
            userInfo: [NSLocalizedDescriptionKey: "Verifier timeout after \(timeoutSeconds)s"]
        )
    }

    private func buildDcqlQuery() -> [String: Any] {
        [
            "credentials": [[
                "id": "query_0",
                "format": "dc+sd-jwt",
                "meta": ["vct_values": ["urn:eudi:pid:1", "eu.europa.ec.eudi.pid.1"]],
            ]],
        ]
    }

    private func jsonString(_ object: Any) throws -> String {
        let data = try JSONSerialization.data(withJSONObject: object, options: [])
        return String(decoding: data, as: UTF8.self)
    }
}

private struct SampleWalletE2EHttpResponse {
    let finalURL: URL
    let body: String
}

private final class SampleWalletE2EClient {
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
        let response = try await textRequest(
            url: url,
            method: method,
            headers: headers,
            body: body,
            allow3xx: allow3xx
        )
        if response.body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return [:]
        }
        let object = try JSONSerialization.jsonObject(with: Data(response.body.utf8), options: [])
        guard let dict = object as? [String: Any] else {
            throw NSError(
                domain: "SampleWalletE2EClient",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Response is not a JSON object: \(response.body)"]
            )
        }
        return dict
    }

    func textRequest(
        url: URL,
        method: String = "GET",
        headers: [String: String] = [:],
        body: Data? = nil,
        allow3xx: Bool = false
    ) async throws -> SampleWalletE2EHttpResponse {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body
        request.setValue("*/*", forHTTPHeaderField: "Accept")
        headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NSError(
                domain: "SampleWalletE2EClient",
                code: 2,
                userInfo: [NSLocalizedDescriptionKey: "Non-HTTP response for \(url)"]
            )
        }

        let text = String(data: data, encoding: .utf8) ?? ""
        let successRange = allow3xx ? (200...399) : (200...299)
        guard successRange.contains(httpResponse.statusCode) else {
            throw NSError(
                domain: "SampleWalletE2EClient",
                code: httpResponse.statusCode,
                userInfo: [NSLocalizedDescriptionKey: "HTTP \(httpResponse.statusCode) from \(url): \(text)"]
            )
        }

        return SampleWalletE2EHttpResponse(finalURL: httpResponse.url ?? url, body: text)
    }

    func formRequest(url: URL, fields: [String: String]) async throws -> SampleWalletE2EHttpResponse {
        let encoded = fields
            .map { "\(Self.urlEncode($0.key))=\(Self.urlEncode($0.value))" }
            .joined(separator: "&")
        return try await textRequest(
            url: url,
            method: "POST",
            headers: ["Content-Type": "application/x-www-form-urlencoded"],
            body: Data(encoded.utf8),
            allow3xx: true
        )
    }

    private static func urlEncode(_ value: String) -> String {
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }
}

private final class SampleEudiOfferFlow {
    private let client: SampleWalletE2EClient

    init(client: SampleWalletE2EClient) {
        self.client = client
    }

    func generate(credentialID: String) async throws -> String {
        let entrypoint = URL(string: "https://issuer.eudiw.dev/credential_offer")!
        let backendGenerate = URL(string: "https://backend.issuer.eudiw.dev/credential_offer")!
        let backendAuthorize = URL(string: "https://backend.issuer.eudiw.dev/form_authorize_generate")!

        let entry = try await client.textRequest(url: entrypoint, allow3xx: true)
        let redirectAction = try resolveURL(base: entry.finalURL, action: try extractFormAction(page: entry.body, formID: "redirect_form"))
        let redirectPayload = try extractPayload(page: entry.body)
        _ = try await client.formRequest(url: redirectAction, fields: ["payload": redirectPayload])

        let preauthRedirect = try await client.formRequest(url: backendGenerate, fields: [
            credentialID: credentialID,
            "Authorization Code Grant": "pre_auth_code",
            "credential_offer_URI": "openid-credential-offer://",
            "proceed": "Submit",
        ])

        let displayAction = try resolveURL(base: preauthRedirect.finalURL, action: try extractFormAction(page: preauthRedirect.body, formID: "redirect_form"))
        let displayPayload = try extractPayload(page: preauthRedirect.body)
        let displayPage = try await client.formRequest(url: displayAction, fields: ["payload": displayPayload])

        let countryAction = try resolveURL(base: displayPage.finalURL, action: try extractFormAction(page: displayPage.body, formID: "selectCountryForm"))
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

        let authAction = try resolveURL(base: authorizeRedirect.finalURL, action: try extractFormAction(page: authorizeRedirect.body, formID: "redirect_form"))
        let authPayload = try extractPayload(page: authorizeRedirect.body)
        let authPage = try await client.formRequest(url: authAction, fields: ["payload": authPayload])

        let offerRedirect = try await client.formRequest(url: backendAuthorize, fields: [
            "user_id": try extractUserID(page: authPage.body),
            "proceed": "Authorize",
        ])

        let payloadRaw = try extractPayload(page: offerRedirect.body)
        guard let payloadData = payloadRaw.data(using: .utf8),
              let payloadJSON = try JSONSerialization.jsonObject(with: payloadData) as? [String: Any],
              let txCodeValue = payloadJSON["tx_code"] as? String ?? (payloadJSON["tx_code"] as? NSNumber)?.stringValue,
              let urlData = payloadJSON["url_data"] as? String else {
            throw NSError(
                domain: "SampleEudiOfferFlow",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Missing tx_code or url_data in final payload"]
            )
        }

        guard let offerURI = URL(string: urlData),
              let components = URLComponents(url: offerURI, resolvingAgainstBaseURL: false),
              let credentialOfferRaw = components.queryItems?.first(where: { $0.name == "credential_offer" })?.value,
              let credentialData = credentialOfferRaw.data(using: .utf8),
              var offerJSON = try JSONSerialization.jsonObject(with: credentialData) as? [String: Any],
              var grants = offerJSON["grants"] as? [String: Any],
              var preAuth = grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"] as? [String: Any] else {
            throw NSError(
                domain: "SampleEudiOfferFlow",
                code: 2,
                userInfo: [NSLocalizedDescriptionKey: "Missing credential offer payload"]
            )
        }

        var txCode = (preAuth["tx_code"] as? [String: Any]) ?? [:]
        txCode["value"] = txCodeValue
        preAuth["tx_code"] = txCode
        grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"] = preAuth
        offerJSON["grants"] = grants

        let updated = try jsonString(offerJSON)
        var output = URLComponents()
        output.scheme = offerURI.scheme ?? "openid-credential-offer"
        output.host = "credential_offer"
        output.queryItems = [URLQueryItem(name: "credential_offer", value: updated)]

        guard let finalURL = output.url?.absoluteString else {
            throw NSError(
                domain: "SampleEudiOfferFlow",
                code: 3,
                userInfo: [NSLocalizedDescriptionKey: "Failed to build offer URL"]
            )
        }
        return finalURL
    }

    private func extractPayload(page: String) throws -> String {
        guard let raw = firstMatch(page, pattern: "name=\\\"payload\\\"\\s+value='(.*?)'\\s*>") else {
            throw NSError(domain: "SampleEudiOfferFlow", code: 4, userInfo: [NSLocalizedDescriptionKey: "Hidden payload input not found"])
        }
        return unescapedHTML(raw)
    }

    private func extractFormAction(page: String, formID: String) throws -> String {
        let pattern = "<form\\s+id=\\\"\(NSRegularExpression.escapedPattern(for: formID))\\\"[^>]*action=\\\"([^\\\"]+)\\\""
        guard let raw = firstMatch(page, pattern: pattern) else {
            throw NSError(domain: "SampleEudiOfferFlow", code: 5, userInfo: [NSLocalizedDescriptionKey: "Form action not found for id=\(formID)"])
        }
        return unescapedHTML(raw)
    }

    private func extractUserID(page: String) throws -> String {
        if let value = firstMatch(page, pattern: "name=\\\"user_id\\\"\\s+value=\\\"([^\\\"]+)\\\"") {
            return value
        }
        if let value = firstMatch(page, pattern: "value=\\\"([a-f0-9\\-]{36})\\\"\\s+name=\\\"user_id\\\"") {
            return value
        }
        throw NSError(domain: "SampleEudiOfferFlow", code: 6, userInfo: [NSLocalizedDescriptionKey: "user_id not found on authorization page"])
    }

    private func jsonString(_ object: Any) throws -> String {
        let data = try JSONSerialization.data(withJSONObject: object, options: [])
        return String(decoding: data, as: UTF8.self)
    }

    private func unescapedHTML(_ value: String) -> String {
        value
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#x27;", with: "'")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
    }

    private func firstMatch(_ text: String, pattern: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: []),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              match.numberOfRanges >= 2,
              let range = Range(match.range(at: 1), in: text) else {
            return nil
        }
        return String(text[range])
    }

    private func resolveURL(base: URL, action: String) throws -> URL {
        guard let resolved = URL(string: action, relativeTo: base)?.absoluteURL else {
            throw NSError(domain: "SampleEudiOfferFlow", code: 7, userInfo: [NSLocalizedDescriptionKey: "Cannot resolve action=\(action) relative to base=\(base)"])
        }
        return resolved
    }
}
