import Foundation

enum EudiOfferFlowError: LocalizedError {
    case missingPayload
    case missingFormAction(String)
    case missingUserID
    case invalidActionURL(action: String, base: URL)

    var errorDescription: String? {
        switch self {
        case .missingPayload:
            return "Hidden payload input not found"
        case .missingFormAction(let formID):
            return "Form action not found for id=\(formID)"
        case .missingUserID:
            return "user_id not found on authorization page"
        case .invalidActionURL(let action, let base):
            return "Cannot resolve action=\(action) relative to base=\(base)"
        }
    }
}

public struct EudiGeneratedOffer {
    public let offerUrl: String
    public let txCode: String
}

/// Generates credential offers from EUDI test backend.
/// Handles multi-step form submission flow for pre-authorized offers.
public final class EudiOfferFlow {
    private let client: WalletE2EClient

    public init(client: WalletE2EClient) {
        self.client = client
    }

    public func generate(credentialID: String = "eu.europa.ec.eudi.pid_vc_sd_jwt") async throws -> EudiGeneratedOffer {
        let entrypoint = URL(string: "https://issuer.eudiw.dev/credential_offer")!
        let backendGenerate = URL(string: "https://backend.issuer.eudiw.dev/credential_offer")!
        let backendAuthorize = URL(string: "https://backend.issuer.eudiw.dev/form_authorize_generate")!

        // Step 1: Entry page redirect
        let entry = try await client.textRequest(url: entrypoint, allow3xx: true)
        let redirectAction = try resolveURL(base: entry.finalURL, action: try extractFormAction(page: entry.body, formID: "redirect_form"))
        let redirectPayload = try extractPayload(page: entry.body)
        _ = try await client.formRequest(url: redirectAction, fields: ["payload": redirectPayload])

        // Step 2: Generate pre-authorized offer
        let preauthRedirect = try await client.formRequest(url: backendGenerate, fields: [
            credentialID: credentialID,
            "Authorization Code Grant": "pre_auth_code",
            "credential_offer_URI": "openid-credential-offer://",
            "proceed": "Submit",
        ])

        // Step 3: Display page redirect
        let displayAction = try resolveURL(base: preauthRedirect.finalURL, action: try extractFormAction(page: preauthRedirect.body, formID: "redirect_form"))
        let displayPayload = try extractPayload(page: preauthRedirect.body)
        let displayPage = try await client.formRequest(url: displayAction, fields: ["payload": displayPayload])

        // Step 4: Country/user data submission
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

        // Step 5: Authorization page redirect
        let authAction = try resolveURL(base: authorizeRedirect.finalURL, action: try extractFormAction(page: authorizeRedirect.body, formID: "redirect_form"))
        let authPayload = try extractPayload(page: authorizeRedirect.body)
        let authPage = try await client.formRequest(url: authAction, fields: ["payload": authPayload])

        // Step 6: Final authorization with user_id
        let userID = try extractUserID(page: authPage.body)
        let offerRedirect = try await client.formRequest(url: backendAuthorize, fields: [
            "user_id": userID,
            "proceed": "Authorize",
        ])

        // Step 7: Return the standard offer and separately delivered transaction code.
        let payloadRaw = try extractPayload(page: offerRedirect.body)
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
              let offerJSON = try JSONSerialization.jsonObject(with: credentialData) as? [String: Any] else {
            throw NSError(domain: "WalletE2E", code: 211, userInfo: [NSLocalizedDescriptionKey: "Missing credential_offer payload"])
        }

        guard let grants = offerJSON["grants"] as? [String: Any],
              let preAuth = grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"] as? [String: Any] else {
            throw NSError(domain: "WalletE2E", code: 212, userInfo: [NSLocalizedDescriptionKey: "Missing pre-authorized grant"])
        }
        let txCodeMetadata = preAuth["tx_code"] as? [String: Any]
        guard txCodeMetadata?["value"] == nil else {
            throw NSError(domain: "WalletE2E", code: 213, userInfo: [NSLocalizedDescriptionKey: "EUDI credential offer embedded a transaction code value"])
        }
        return EudiGeneratedOffer(offerUrl: urlData, txCode: txCodeValue)
    }

    private func extractPayload(page: String) throws -> String {
        guard let raw = firstMatch(page, pattern: "name=\\\"payload\\\"\\s+value='(.*?)'\\s*>") else {
            throw EudiOfferFlowError.missingPayload
        }
        return unescapedHTML(raw)
    }

    private func extractFormAction(page: String, formID: String) throws -> String {
        let pattern = "<form\\s+id=\\\"\(NSRegularExpression.escapedPattern(for: formID))\\\"[^>]*action=\\\"([^\\\"]+)\\\""
        guard let raw = firstMatch(page, pattern: pattern) else {
            throw EudiOfferFlowError.missingFormAction(formID)
        }
        return unescapedHTML(raw)
    }

    private func extractUserID(page: String) throws -> String {
        if let v = firstMatch(page, pattern: "name=\\\"user_id\\\"\\s+value=\\\"([^\\\"]+)\\\"") {
            return v
        }
        if let v = firstMatch(page, pattern: "value=\\\"([a-f0-9\\-]{36})\\\"\\s+name=\\\"user_id\\\"") {
            return v
        }
        throw EudiOfferFlowError.missingUserID
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
            throw EudiOfferFlowError.invalidActionURL(action: action, base: base)
        }
        return resolved
    }
}
