import Foundation

/// A routing hint; the wallet's protocol client remains responsible for validation.
public enum WalletLinkKind: Equatable {
    case empty, offer, presentation, web, authorizationCallback, fidoHybrid, unsupported

    public static func classify(_ input: String) -> Self {
        let value = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return .empty }
        guard let colon = value.firstIndex(of: ":") else { return .unsupported }
        let scheme = value[..<colon].lowercased()
        if scheme == "fido" { return .fidoHybrid }
        guard value.rangeOfCharacter(from: .whitespacesAndNewlines) == nil else { return .unsupported }
        switch scheme {
        case "openid-credential-offer": return .offer
        case "openid4vp": return .presentation
        case "openid": return .authorizationCallback
        case "http", "https":
            guard let url = URLComponents(string: value), let host = url.host, !host.isEmpty else { return .unsupported }
            let keys = Set((url.queryItems ?? []).map(\.name))
            if !keys.isDisjoint(with: ["code", "error"]) { return .authorizationCallback }
            if !keys.isDisjoint(with: ["credential_offer", "credential_offer_uri"]) { return .offer }
            if !keys.isDisjoint(with: ["request", "request_uri"]) ||
                (keys.contains("client_id") && (!keys.isDisjoint(with: ["dcql_query", "presentation_definition"]) ||
                    url.queryItems?.first(where: { $0.name == "response_type" })?.value?.split(separator: " ").contains("vp_token") == true)) {
                return .presentation
            }
            return .web
        default: return .unsupported
        }
    }
}
