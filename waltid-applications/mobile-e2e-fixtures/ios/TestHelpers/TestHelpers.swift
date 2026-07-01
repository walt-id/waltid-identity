import Foundation

/// JSON serialization helper for tests
public func jsonString(_ object: Any) throws -> String {
    let data = try JSONSerialization.data(withJSONObject: object, options: [])
    return String(decoding: data, as: UTF8.self)
}

/// Build DCQL query for EUDI verifier
public func buildDcqlQuery(credentialID: String) -> [String: Any] {
    let normalized = credentialID.lowercased()

    if normalized.contains("sd_jwt") || normalized.contains("jwt_vc") {
        return [
            "credentials": [[
                "id": "query_0",
                "format": "dc+sd-jwt",
                "meta": ["vct_values": ["urn:eudi:pid:1", "eu.europa.ec.eudi.pid.1"]],
            ]],
        ]
    }

    let docType = normalized.contains("mdl") ? "org.iso.18013.5.1.mDL" : "eu.europa.ec.eudi.pid.1"
    return [
        "credentials": [[
            "id": "query_0",
            "format": "mso_mdoc",
            "meta": ["doctype_value": docType],
        ]],
    ]
}

/// Wait for EUDI verifier to process presentation
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
