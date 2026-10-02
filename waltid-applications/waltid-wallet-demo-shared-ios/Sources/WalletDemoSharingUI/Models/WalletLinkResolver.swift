import Foundation

public struct ResolvedWalletLink {
    public let url: String
    public let kind: WalletLinkKind
}

public struct WalletLinkDocument {
    public let status: Int
    public let body: String
    public let location: String?
    public init(status: Int, body: String = "", location: String? = nil) {
        self.status = status; self.body = body; self.location = location
    }
}

public struct WalletLinkError: LocalizedError {
    public let message: String
    public var errorDescription: String? { message }
}

/// Routing only: the existing wallet client validates every resolved offer/request before review.
public enum WalletLinkResolver {
    public static func resolve(_ input: String,
        fetch: (String) async throws -> WalletLinkDocument = fetchDocument
    ) async throws -> ResolvedWalletLink {
        do {
            var value = input.trimmingCharacters(in: .whitespacesAndNewlines)
            var visited = Set<String>()
            for _ in 0..<6 {
                try Task.checkCancellation()
                let kind = WalletLinkKind.classify(value)
                if [.offer, .presentation, .authorizationCallback].contains(kind) {
                    return ResolvedWalletLink(url: value, kind: kind)
                }
                guard kind == .web, let url = URL(string: value), url.user == nil, url.password == nil else {
                    throw WalletLinkError(message: unsupportedMessage)
                }
                guard visited.insert(value).inserted else {
                    throw WalletLinkError(message: "This link keeps redirecting. Ask for a fresh QR code.")
                }
                let document = try await fetch(value)
                try Task.checkCancellation()
                if [301, 302, 303, 307, 308].contains(document.status) {
                    guard let location = document.location, let target = URL(string: location, relativeTo: url)?.absoluteURL else {
                        throw WalletLinkError(message: unsupportedMessage)
                    }
                    if url.scheme?.lowercased() == "https" && target.scheme?.lowercased() == "http" {
                        throw WalletLinkError(message: "This link redirects to an insecure page. Ask for a new link.")
                    }
                    value = target.absoluteString
                } else {
                    guard (200..<300).contains(document.status) else { throw WalletLinkError(message: unavailableMessage) }
                    return try routeDocument(url: value, body: document.body)
                }
            }
            throw WalletLinkError(message: "This link has too many redirects. Ask for a fresh QR code.")
        } catch is CancellationError { throw CancellationError() }
        catch let error as WalletLinkError { throw error }
        catch {
            try Task.checkCancellation()
            throw WalletLinkError(message: unavailableMessage)
        }
    }

    /// Shape is a routing hint. JWT claims are not authenticated until the protocol client runs.
    public static func routeDocument(url: String, body: String) throws -> ResolvedWalletLink {
        guard body.utf8.count <= maxBytes else { throw WalletLinkError(message: unsupportedMessage) }
        let value = body.trimmingCharacters(in: .whitespacesAndNewlines)
        let json = object(Data(value.utf8))
        let segments = value.split(separator: ".", omittingEmptySubsequences: false)
        var jwt: [String: Any]?
        if json == nil && segments.count == 3 {
            var base64 = String(segments[1]).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
            base64 += String(repeating: "=", count: (4 - base64.count % 4) % 4)
            if let data = Data(base64Encoded: base64) { jwt = object(data) }
        }
        guard let payload = json ?? jwt else { throw WalletLinkError(message: unsupportedMessage) }
        let offer = payload["credential_issuer"] is String &&
            (payload["credential_configuration_ids"] is [Any] || payload["credentials"] is [Any])
        let clientID = (payload["client_id"] as? String).flatMap { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : $0 }
        let presentation = clientID != nil && (payload["dcql_query"] != nil || payload["presentation_definition"] != nil ||
            (payload["response_type"] as? String)?.split(separator: " ").contains("vp_token") == true)
        guard offer != presentation else { throw WalletLinkError(message: unsupportedMessage) }
        if offer && json != nil {
            return ResolvedWalletLink(url: "openid-credential-offer://?credential_offer=\(encoded(value))", kind: .offer)
        }
        if presentation, let clientID {
            // Preserve JWT bytes. Unsigned JSON keeps the SDK's request_uri policy and warning.
            let reference = jwt != nil ? "request=\(encoded(value))" : "request_uri=\(encoded(url))"
            return ResolvedWalletLink(url: "openid4vp://?client_id=\(encoded(clientID))&\(reference)", kind: .presentation)
        }
        throw WalletLinkError(message: unsupportedMessage)
    }

    public static func fetchDocument(_ value: String) async throws -> WalletLinkDocument {
        guard let url = URL(string: value) else { throw WalletLinkError(message: unsupportedMessage) }
        let config = URLSessionConfiguration.ephemeral
        config.httpShouldSetCookies = false
        config.httpCookieStorage = nil
        config.urlCredentialStorage = nil
        config.urlCache = nil
        config.timeoutIntervalForRequest = 10
        config.timeoutIntervalForResource = 10
        let session = URLSession(configuration: config, delegate: NoAutomaticRedirect(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData)
        request.setValue("application/json, application/oauth-authz-req+jwt", forHTTPHeaderField: "Accept")
        let (bytes, response) = try await session.bytes(for: request)
        guard let http = response as? HTTPURLResponse else { throw WalletLinkError(message: unavailableMessage) }
        if (300..<400).contains(http.statusCode) {
            return WalletLinkDocument(status: http.statusCode, location: http.value(forHTTPHeaderField: "Location"))
        }
        guard response.expectedContentLength <= maxBytes else { throw WalletLinkError(message: unsupportedMessage) }
        var data = Data()
        for try await byte in bytes {
            guard data.count < maxBytes else { throw WalletLinkError(message: unsupportedMessage) }
            data.append(byte)
        }
        guard let body = String(data: data, encoding: .utf8) else { throw WalletLinkError(message: unsupportedMessage) }
        return WalletLinkDocument(status: http.statusCode, body: body)
    }

    private static func object(_ data: Data) -> [String: Any]? { (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] }
    private static func encoded(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: .alphanumerics.union(CharacterSet(charactersIn: "-._~")))!
    }
    private static let maxBytes = 262_144
    private static let unsupportedMessage = "This page does not contain a credential offer or sharing request. Open the issuer or verifier page and scan its QR code."
    private static let unavailableMessage = "Could not open this link. Check your connection or ask for a fresh QR code, then try again."
}

private final class NoAutomaticRedirect: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}
