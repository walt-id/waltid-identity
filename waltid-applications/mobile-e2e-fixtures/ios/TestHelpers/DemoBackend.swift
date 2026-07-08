import Foundation

public struct DemoCredentialScenario {
    public let id: String
    public let displayName: String
    public let profileId: String
    public let credentialConfigurationId: String
    public let format: String
    public let verifierCredentialQuery: [String: Any]
}

public struct DemoOffer {
    public let offerUrl: String
}

public struct DemoVerifierSession {
    public let sessionID: String
    public let authorizationRequestUri: String
}

public final class DemoBackend {
    public static let shared = DemoBackend()

    public static let scenarios: [DemoCredentialScenario] = [
        DemoCredentialScenario(
            id: "eudi-pid-sdjwt",
            displayName: "EUDI PID SD-JWT VC",
            profileId: "eudiPidSdJwt",
            credentialConfigurationId: "urn:eudi:pid:1",
            format: "dc+sd-jwt",
            verifierCredentialQuery: sdJwtQuery(
                id: "pid",
                vct: "urn:eudi:pid:1"
            )
        ),
        DemoCredentialScenario(
            id: "eudi-pid-mdoc",
            displayName: "EUDI PID mdoc",
            profileId: "eudiPidMdoc",
            credentialConfigurationId: "eu.europa.ec.eudi.pid.1",
            format: "mso_mdoc",
            verifierCredentialQuery: mdocQuery(
                id: "pid_mdoc",
                doctype: "eu.europa.ec.eudi.pid.1",
                namespace: "eu.europa.ec.eudi.pid.1",
                claims: ["given_name", "family_name"]
            )
        ),
        DemoCredentialScenario(
            id: "iso-mdl",
            displayName: "ISO mDL",
            profileId: "isoMdl",
            credentialConfigurationId: "org.iso.18013.5.1.mDL",
            format: "mso_mdoc",
            verifierCredentialQuery: mdocQuery(
                id: "mdl",
                doctype: "org.iso.18013.5.1.mDL",
                namespace: "org.iso.18013.5.1",
                claims: ["given_name", "family_name"]
            )
        ),
    ]

    // Keep SD-JWT in issuer coverage, but limit verifier2 presentation to the
    // public demo formats the mobile wallet currently fulfills end to end.
    public static let presentationScenarios = scenarios.filter { $0.format == "mso_mdoc" }

    public static let persistenceScenario = presentationScenarios[0]

    private static let issuerBaseURL = URL(string: "https://issuer2.demo.walt.id")!
    private static let verifierBaseURL = URL(string: "https://verifier2.demo.walt.id")!

    private let client: WalletE2EClient

    public init(client: WalletE2EClient = WalletE2EClient()) {
        self.client = client
    }

    public func createOffer(scenario: DemoCredentialScenario) async throws -> DemoOffer {
        let endpoint = Self.issuerBaseURL
            .appendingPathComponent("issuer2")
            .appendingPathComponent("credential-offers")
        let response = try await client.jsonRequest(
            url: endpoint,
            method: "POST",
            headers: ["Content-Type": "application/json"],
            body: Data(try jsonString([
                "profileId": scenario.profileId,
                "authMethod": "PRE_AUTHORIZED",
            ]).utf8),
            retryTransientFailures: true
        )

        guard let offerUrl = response["credentialOffer"] as? String, !offerUrl.isEmpty else {
            throw NSError(
                domain: "WalletE2E",
                code: 300,
                userInfo: [NSLocalizedDescriptionKey: "Missing credentialOffer in public demo issuer2 response: \(response)"]
            )
        }

        return DemoOffer(offerUrl: offerUrl)
    }

    public func createVerifierSession(scenario: DemoCredentialScenario) async throws -> DemoVerifierSession {
        let endpoint = Self.verifierBaseURL
            .appendingPathComponent("verification-session")
            .appendingPathComponent("create")
        let payload: [String: Any] = [
            "flow_type": "cross_device",
            "core_flow": [
                "dcql_query": [
                    "credentials": [scenario.verifierCredentialQuery],
                ],
            ],
        ]
        let response = try await client.jsonRequest(
            url: endpoint,
            method: "POST",
            headers: ["Content-Type": "application/json"],
            body: Data(try jsonString(payload).utf8),
            retryTransientFailures: true
        )

        guard let sessionID = response["sessionId"] as? String, !sessionID.isEmpty else {
            throw NSError(
                domain: "WalletE2E",
                code: 301,
                userInfo: [NSLocalizedDescriptionKey: "Missing sessionId in public demo verifier2 response: \(response)"]
            )
        }
        let requestURL = response["bootstrapAuthorizationRequestUrl"] as? String
            ?? response["authorizationRequestUrl"] as? String
            ?? response["fullAuthorizationRequestUrl"] as? String
        guard let requestURL, !requestURL.isEmpty else {
            throw NSError(
                domain: "WalletE2E",
                code: 302,
                userInfo: [NSLocalizedDescriptionKey: "Missing authorization request URL in public demo verifier2 response: \(response)"]
            )
        }

        return DemoVerifierSession(sessionID: sessionID, authorizationRequestUri: requestURL)
    }

    public func waitForVerifierSuccess(sessionID: String, timeoutSeconds: TimeInterval) async throws {
        let deadline = Date().addingTimeInterval(timeoutSeconds)
        var lastStatus = "UNKNOWN"

        while Date() < deadline {
            let url = Self.verifierBaseURL
                .appendingPathComponent("verification-session")
                .appendingPathComponent(sessionID)
                .appendingPathComponent("info")
            do {
                let response = try await client.jsonRequest(url: url, retryTransientFailures: true)
                let status = (response["status"] as? String)
                    ?? ((response["session"] as? [String: Any])?["status"] as? String)
                if let status {
                    lastStatus = status
                    switch status.uppercased() {
                    case "SUCCESSFUL":
                        return
                    case "FAILED", "ERROR", "EXPIRED":
                        throw NSError(
                            domain: "WalletE2E",
                            code: 303,
                            userInfo: [NSLocalizedDescriptionKey: "public demo verifier2 reported \(status) for session \(sessionID): \(response)"]
                        )
                    default:
                        break
                    }
                }
            } catch let error as NSError where error.domain == "WalletE2E" && error.code == 303 {
                throw error
            } catch {
                lastStatus = "request failed: \(error.localizedDescription)"
            }

            try await Task.sleep(nanoseconds: 2_000_000_000)
        }

        throw NSError(
            domain: "WalletE2E",
            code: 304,
            userInfo: [NSLocalizedDescriptionKey: "public demo verifier2 timeout after \(timeoutSeconds)s for session \(sessionID); last status: \(lastStatus)"]
        )
    }

    private static func sdJwtQuery(id: String, vct: String) -> [String: Any] {
        [
            "id": id,
            "format": "dc+sd-jwt",
            "meta": ["vct_values": [vct]],
            // The public demo verifier accepts vct-only SD-JWT requests; claim-path
            // filtering here currently causes wallet presentation matching to miss.
        ]
    }

    private static func mdocQuery(id: String, doctype: String, namespace: String, claims: [String]) -> [String: Any] {
        [
            "id": id,
            "format": "mso_mdoc",
            "meta": ["doctype_value": doctype],
            "claims": claims.map { ["path": [namespace, $0]] },
        ]
    }
}
