import Foundation

public struct EudiPublicBackendConfig {
    public let credentialID: String
    public let offerURL: String?

    public init(credentialID: String, offerURL: String?) {
        self.credentialID = credentialID
        self.offerURL = offerURL
    }

    public static func fromEnvironment(_ env: [String: String] = ProcessInfo.processInfo.environment) -> EudiPublicBackendConfig {
        EudiPublicBackendConfig(
            credentialID: envValue("E2E_CREDENTIAL_ID", from: env) ?? "eu.europa.ec.eudi.pid_vc_sd_jwt",
            offerURL: resolveOfferURL(from: env)
        )
    }

    private static func resolveOfferURL(from env: [String: String]) -> String? {
        if let direct = envValue("E2E_OFFER_URL", from: env), !direct.isEmpty {
            return direct
        }

        if let b64 = envValue("E2E_OFFER_URL_B64", from: env), !b64.isEmpty,
           let data = Data(base64Encoded: b64),
           let decoded = String(data: data, encoding: .utf8),
           !decoded.isEmpty {
            return decoded
        }

        if let path = envValue("E2E_OFFER_URL_FILE", from: env),
           let data = try? Data(contentsOf: URL(fileURLWithPath: path)),
           var text = String(data: data, encoding: .utf8)?
             .trimmingCharacters(in: .whitespacesAndNewlines),
           !text.isEmpty {
            if path.hasSuffix(".b64"),
               let decoded = Data(base64Encoded: text),
               let decodedText = String(data: decoded, encoding: .utf8) {
                text = decodedText
            }
            if text.starts(with: "openid-credential-offer://") {
                return text
            }
        }

        return nil
    }

    private static func envValue(_ name: String, from env: [String: String]) -> String? {
        env[name] ?? env["TEST_RUNNER_\(name)"]
    }
}

public struct EudiVerifierTransaction {
    public let transactionID: String
    public let authorizationRequestURI: String

    public init(transactionID: String, authorizationRequestURI: String) {
        self.transactionID = transactionID
        self.authorizationRequestURI = authorizationRequestURI
    }
}

public final class EudiPublicBackend {
    private let client: WalletE2EClient

    public init(client: WalletE2EClient = WalletE2EClient()) {
        self.client = client
    }

    public func generatePreAuthorizedOffer(credentialID: String = "eu.europa.ec.eudi.pid_vc_sd_jwt") async throws -> String {
        try await EudiOfferFlow(client: client).generate(credentialID: credentialID)
    }

    public func createVerifierTransaction(credentialID: String = "eu.europa.ec.eudi.pid_vc_sd_jwt") async throws -> EudiVerifierTransaction {
        let payload: [String: Any] = [
            "dcql_query": buildDcqlQuery(credentialID: credentialID),
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
                domain: "WalletE2E",
                code: 200,
                userInfo: [NSLocalizedDescriptionKey: "Invalid verifier response: \(response)"]
            )
        }

        return EudiVerifierTransaction(
            transactionID: transactionID,
            authorizationRequestURI: authorizationRequestURI
        )
    }

    public func waitForVerifierSuccess(transactionID: String, timeoutSeconds: TimeInterval) async throws {
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

                if combined.contains("failed") || combined.contains("error") || combined.contains("invalid") || combined.contains("timed out") {
                    throw NSError(domain: "WalletE2E", code: 201, userInfo: [NSLocalizedDescriptionKey: "Verifier failure: \(event)"])
                }
                if eventName.contains("wallet response posted") || eventName.contains("successful") || eventName.contains("verified") {
                    return
                }
            }

            try await Task.sleep(nanoseconds: 2_000_000_000)
        }

        throw NSError(domain: "WalletE2E", code: 202, userInfo: [NSLocalizedDescriptionKey: "Verifier timeout after \(timeoutSeconds)s"])
    }
}
