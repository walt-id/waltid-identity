import Foundation

public enum EnterpriseMobilePlatform: String {
    case android = "ANDROID"
    case ios = "IOS"
}

public struct EnterpriseMobileScenario {
    public let id: String
    public let displayName: String
    public let format: String
    public let supportsPresentation: Bool
    public let requiresClientAttestation: Bool

    init(json: [String: Any]) throws {
        guard let id = json["id"] as? String,
              let displayName = json["displayName"] as? String,
              let format = json["format"] as? String,
              let supportsPresentation = json["supportsPresentation"] as? Bool,
              let requiresClientAttestation = json["requiresClientAttestation"] as? Bool else {
            throw NSError(domain: "WalletE2E", code: 400, userInfo: [NSLocalizedDescriptionKey: "Invalid Enterprise scenario: \(json)"])
        }
        self.id = id
        self.displayName = displayName
        self.format = format
        self.supportsPresentation = supportsPresentation
        self.requiresClientAttestation = requiresClientAttestation
    }
}

public struct EnterpriseMobileAttestation {
    public let baseUrl: String
    public let attesterPath: String
    public let bearerToken: String
    public let hostHeader: String

    init?(json: [String: Any]?) throws {
        guard let json else {
            return nil
        }
        guard let baseUrl = json["baseUrl"] as? String,
              let attesterPath = json["attesterPath"] as? String else {
            throw NSError(domain: "WalletE2E", code: 401, userInfo: [NSLocalizedDescriptionKey: "Invalid Enterprise attestation config: \(json)"])
        }
        self.baseUrl = baseUrl
        self.attesterPath = attesterPath
        self.bearerToken = json["bearerToken"] as? String ?? ""
        self.hostHeader = json["hostHeader"] as? String ?? ""
    }
}

public struct EnterpriseMobileOffer {
    public let offerUrl: String
    public let txCode: String?
    public let attestation: EnterpriseMobileAttestation?

    init(json: [String: Any]) throws {
        guard let offerUrl = json["offerUrl"] as? String else {
            throw NSError(domain: "WalletE2E", code: 402, userInfo: [NSLocalizedDescriptionKey: "Invalid Enterprise offer: \(json)"])
        }
        self.offerUrl = offerUrl
        self.txCode = json["txCode"] as? String
        self.attestation = try EnterpriseMobileAttestation(json: json["attestation"] as? [String: Any])
    }
}

public struct EnterpriseMobileVerifierSession {
    public let sessionID: String
    public let authorizationRequestUri: String

    init(json: [String: Any]) throws {
        guard let sessionID = json["sessionId"] as? String,
              let authorizationRequestUri = json["authorizationRequestUri"] as? String else {
            throw NSError(domain: "WalletE2E", code: 403, userInfo: [NSLocalizedDescriptionKey: "Invalid Enterprise verifier session: \(json)"])
        }
        self.sessionID = sessionID
        self.authorizationRequestUri = authorizationRequestUri
    }
}

public final class EnterpriseMobileFixture {
    private let baseURL: URL
    private let client: WalletE2EClient

    public init(baseURL: URL, client: WalletE2EClient = WalletE2EClient()) {
        self.baseURL = baseURL
        self.client = client
    }

    public func scenarios() async throws -> [EnterpriseMobileScenario] {
        let url = baseURL.appendingPathComponent("scenarios")
        let response = try await client.textRequest(url: url)
        let object = try JSONSerialization.jsonObject(with: Data(response.body.utf8), options: [])
        guard let array = object as? [[String: Any]] else {
            throw NSError(domain: "WalletE2E", code: 404, userInfo: [NSLocalizedDescriptionKey: "Invalid Enterprise scenarios response: \(response.body)"])
        }
        return try array.map(EnterpriseMobileScenario.init(json:))
    }

    public func createOffer(
        scenario: EnterpriseMobileScenario,
        platform: EnterpriseMobilePlatform
    ) async throws -> EnterpriseMobileOffer {
        let response = try await client.jsonRequest(
            url: baseURL.appendingPathComponent("offers"),
            method: "POST",
            headers: ["Content-Type": "application/json"],
            body: Data(try jsonString([
                "scenarioId": scenario.id,
                "platform": platform.rawValue,
            ]).utf8)
        )
        return try EnterpriseMobileOffer(json: response)
    }

    public func createVerifierSession(
        scenario: EnterpriseMobileScenario,
        platform: EnterpriseMobilePlatform
    ) async throws -> EnterpriseMobileVerifierSession {
        let response = try await client.jsonRequest(
            url: baseURL.appendingPathComponent("verification-sessions"),
            method: "POST",
            headers: ["Content-Type": "application/json"],
            body: Data(try jsonString([
                "scenarioId": scenario.id,
                "platform": platform.rawValue,
            ]).utf8)
        )
        return try EnterpriseMobileVerifierSession(json: response)
    }

    public func waitForVerifierSuccess(sessionID: String, timeoutSeconds: TimeInterval) async throws {
        let deadline = Date().addingTimeInterval(timeoutSeconds)
        var lastStatus = "UNKNOWN"

        while Date() < deadline {
            let response = try await client.jsonRequest(
                url: baseURL
                    .appendingPathComponent("verification-sessions")
                    .appendingPathComponent(sessionID)
            )
            let status = response["status"] as? String
            if let status {
                lastStatus = status
                switch status.uppercased() {
                case "SUCCESSFUL":
                    return
                case "FAILED", "ERROR", "EXPIRED":
                    throw NSError(domain: "WalletE2E", code: 405, userInfo: [NSLocalizedDescriptionKey: "Enterprise verifier2 reported \(status) for session \(sessionID): \(response)"])
                default:
                    break
                }
            }

            try await Task.sleep(nanoseconds: 2_000_000_000)
        }

        throw NSError(domain: "WalletE2E", code: 406, userInfo: [NSLocalizedDescriptionKey: "Enterprise verifier2 timeout after \(timeoutSeconds)s for session \(sessionID); last status: \(lastStatus)"])
    }
}
