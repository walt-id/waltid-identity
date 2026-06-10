import Foundation

/// EUDI test backend helper for unit tests.
/// Uses the proven implementation from UI tests.
actor EudiTestBackend {
    static let shared = EudiTestBackend()

    private let client = WalletE2EClient()
    private let offerFlow: EudiOfferFlow

    private init() {
        self.offerFlow = EudiOfferFlow(client: client)
    }

    struct GeneratedOffer {
        let offerUrl: String
        let txCode: String?
    }

    struct VerifierTransaction {
        let transactionId: String
        let authorizationRequestUri: String
    }

    func generateOffer(credentialId: String = "eu.europa.ec.eudi.pid_vc_sd_jwt") async throws -> GeneratedOffer {
        let offerUrl = try await offerFlow.generate(credentialID: credentialId)
        // Extract tx_code from the offer URL
        guard let url = URL(string: offerUrl),
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let offerParam = components.queryItems?.first(where: { $0.name == "credential_offer" })?.value,
              let offerData = offerParam.data(using: .utf8),
              let offerJson = try? JSONSerialization.jsonObject(with: offerData) as? [String: Any],
              let grants = offerJson["grants"] as? [String: Any],
              let preAuth = grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"] as? [String: Any],
              let txCodeDict = preAuth["tx_code"] as? [String: Any],
              let txCodeValue = txCodeDict["value"] as? String else {
            return GeneratedOffer(offerUrl: offerUrl, txCode: nil)
        }

        return GeneratedOffer(offerUrl: offerUrl, txCode: txCodeValue)
    }

    func extractCredentialIdFromOfferUrl(offerUrl: String) -> String {
        guard let url = URL(string: offerUrl),
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let offerParam = components.queryItems?.first(where: { $0.name == "credential_offer" })?.value,
              let offerData = offerParam.data(using: .utf8),
              let offerJson = try? JSONSerialization.jsonObject(with: offerData) as? [String: Any],
              let configIds = offerJson["credential_configuration_ids"] as? [String],
              let firstId = configIds.first else {
            return "eu.europa.ec.eudi.pid_vc_sd_jwt"
        }
        return firstId
    }

    func createVerifierTransaction(credentialId: String = "eu.europa.ec.eudi.pid_vc_sd_jwt") async throws -> VerifierTransaction {
        let dcqlQuery = buildDcqlQuery(credentialId: credentialId)
        let payload: [String: Any] = [
            "dcql_query": dcqlQuery,
            "nonce": UUID().uuidString,
            "request_uri_method": "post",
            "profile": "openid4vp",
            "authorization_request_uri": "openid4vp://"
        ]

        let response = try await client.jsonRequest(
            url: URL(string: "https://verifier-backend.eudiw.dev/ui/presentations/v2")!,
            method: "POST",
            headers: ["Content-Type": "application/json"],
            body: Data(try! jsonString(payload).utf8)
        )

        guard let transactionId = response["transaction_id"] as? String,
              let authRequestUri = response["authorization_request_uri"] as? String else {
            throw NSError(domain: "EudiTestBackend", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid verifier response"])
        }

        return VerifierTransaction(transactionId: transactionId, authorizationRequestUri: authRequestUri)
    }

    func waitForVerifierSuccess(transactionId: String, timeoutMs: Int = 10_000) async throws {
        let start = Date()
        let session = URLSession.shared
        while Date().timeIntervalSince(start) * 1000 < Double(timeoutMs) {
            let url = URL(string: "https://verifier-backend.eudiw.dev/ui/presentations/\(transactionId)/events")!
            let (data, _) = try await session.data(from: url)
            let body = String(data: data, encoding: .utf8) ?? ""

            if !body.isEmpty {
                // Response is {"events": [...]} not just [...]
                let response = (try? JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
                let events = response["events"] as? [[String: Any]] ?? []

                for event in events {
                    let eventName = (event["event"] as? String ?? "").lowercased()
                    let cause = (event["cause"] as? String ?? "").lowercased()
                    let combined = eventName + " " + cause

                    if combined.contains("failed") || combined.contains("error") || combined.contains("invalid") || combined.contains("timed out") {
                        throw NSError(domain: "EudiTestBackend", code: 2, userInfo: [NSLocalizedDescriptionKey: "Verifier reported failure: \(event)"])
                    }
                    if eventName.contains("wallet response posted") || eventName.contains("successful") || eventName.contains("verified") {
                        return
                    }
                }
            }

            try await Task.sleep(nanoseconds: 2_000_000_000)
        }

        throw NSError(domain: "EudiTestBackend", code: 3, userInfo: [NSLocalizedDescriptionKey: "Verifier timeout"])
    }

    private func buildDcqlQuery(credentialId: String) -> [String: Any] {
        let format: String
        let meta: [String: Any]

        if credentialId.contains("sd_jwt") || credentialId.contains("jwt_vc") {
            format = "dc+sd-jwt"
            meta = ["vct_values": ["urn:eudi:pid:1", "eu.europa.ec.eudi.pid.1"]]
        } else if credentialId.contains("mdl") {
            format = "mso_mdoc"
            meta = ["doctype_value": "org.iso.18013.5.1.mDL"]
        } else {
            format = "mso_mdoc"
            meta = ["doctype_value": "eu.europa.ec.eudi.pid.1"]
        }

        return [
            "credentials": [
                [
                    "id": "identity",
                    "format": format,
                    "meta": meta
                ]
            ]
        ]
    }
}
