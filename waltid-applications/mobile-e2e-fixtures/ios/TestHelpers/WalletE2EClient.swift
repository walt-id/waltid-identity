import Foundation

public struct WalletE2EHttpResponse {
    public let finalURL: URL
    public let statusCode: Int
    public let body: String

    public init(finalURL: URL, statusCode: Int, body: String) {
        self.finalURL = finalURL
        self.statusCode = statusCode
        self.body = body
    }
}

public final class WalletE2EClient {
    private let session: URLSession

    public init() {
        let config = URLSessionConfiguration.ephemeral
        config.httpShouldSetCookies = true
        config.httpCookieAcceptPolicy = .always
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        session = URLSession(configuration: config, delegate: LocalFixtureMetrics(), delegateQueue: nil)
    }

    public func jsonRequest(
        url: URL,
        method: String = "GET",
        headers: [String: String] = [:],
        body: Data? = nil,
        allow3xx: Bool = false,
        retryTransientFailures: Bool = false
    ) async throws -> [String: Any] {
        let response = try await textRequest(
            url: url,
            method: method,
            headers: headers,
            body: body,
            allow3xx: allow3xx,
            retryTransientFailures: retryTransientFailures
        )
        if response.body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return [:]
        }
        let object = try JSONSerialization.jsonObject(with: Data(response.body.utf8), options: [])
        guard let dict = object as? [String: Any] else {
            throw NSError(domain: "WalletE2E", code: 1, userInfo: [NSLocalizedDescriptionKey: "Response is not JSON object: \(response.body)"])
        }
        return dict
    }

    public func textRequest(
        url: URL,
        method: String = "GET",
        headers: [String: String] = [:],
        body: Data? = nil,
        allow3xx: Bool = false,
        retryTransientFailures: Bool = false
    ) async throws -> WalletE2EHttpResponse {
        let maxAttempts = retryTransientFailures ? 3 : 1
        var attempt = 1

        while true {
            do {
                return try await performTextRequest(
                    url: url,
                    method: method,
                    headers: headers,
                    body: body,
                    allow3xx: allow3xx
                )
            } catch {
                guard attempt < maxAttempts, Self.isTransient(error) else {
                    throw error
                }

                try await Task.sleep(nanoseconds: UInt64(attempt) * 1_000_000_000)
                attempt += 1
            }
        }
    }

    private func performTextRequest(
        url: URL,
        method: String,
        headers: [String: String],
        body: Data?,
        allow3xx: Bool
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

    public func formRequest(url: URL, fields: [String: String]) async throws -> WalletE2EHttpResponse {
        let encoded = fields
            .map { "\(Self.urlEncode($0.key))=\(Self.urlEncode($0.value))" }
            .joined(separator: "&")
        let headers = ["Content-Type": "application/x-www-form-urlencoded"]
        return try await textRequest(url: url, method: "POST", headers: headers, body: Data(encoded.utf8), allow3xx: true)
    }

    public static func urlEncode(_ value: String) -> String {
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }

    private static func isTransient(_ error: Error) -> Bool {
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain {
            let code = URLError.Code(rawValue: nsError.code)
            return [
                .timedOut,
                .networkConnectionLost,
                .notConnectedToInternet,
                .cannotFindHost,
                .cannotConnectToHost,
                .dnsLookupFailed,
                .secureConnectionFailed,
            ].contains(code)
        }

        if nsError.domain == "WalletE2E" {
            return [408, 429, 500, 502, 503, 504].contains(nsError.code)
        }

        return false
    }
}

// Keep fixture timing useful on CI without recording URLs, headers, bodies, or errors.
private final class LocalFixtureMetrics: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask, didFinishCollecting metrics: URLSessionTaskMetrics) {
        guard let url = task.originalRequest?.url, let host = url.host,
              ["localhost", "127.0.0.1", "::1", "[::1]"].contains(host), url.port == 33335,
              ["/scenarios", "/offers"].contains(url.path) else { return }
        let transactions: [[String: Any]] = metrics.transactionMetrics.map { transaction in
            var values: [String: Any] = ["reused_connection": transaction.isReusedConnection]
            let dates: [(String, Date?)] = [
                ("dns_start_ms", transaction.domainLookupStartDate), ("dns_end_ms", transaction.domainLookupEndDate),
                ("connect_start_ms", transaction.connectStartDate), ("connect_end_ms", transaction.connectEndDate),
                ("request_start_ms", transaction.requestStartDate), ("request_end_ms", transaction.requestEndDate),
                ("response_start_ms", transaction.responseStartDate), ("response_end_ms", transaction.responseEndDate),
            ]
            for (name, date) in dates {
                if let date { values[name] = date.timeIntervalSince(metrics.taskInterval.start) * 1_000 }
            }
            if let response = transaction.response as? HTTPURLResponse { values["status"] = response.statusCode }
            return values
        }
        var record: [String: Any] = [
            "route": url.path, "duration_ms": metrics.taskInterval.duration * 1_000,
            "transactions": transactions,
        ]
        if let error = task.error as NSError?, error.domain == NSURLErrorDomain {
            record["url_error_code"] = error.code
        }
        guard let data = try? JSONSerialization.data(withJSONObject: record, options: [.sortedKeys]) else { return }
        FileHandle.standardError.write(Data("WALLET_E2E_LOCAL_HTTP ".utf8) + data + Data("\n".utf8))
    }
}
