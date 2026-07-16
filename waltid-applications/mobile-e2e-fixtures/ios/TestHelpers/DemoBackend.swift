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
    public let txCode: String?
}

public struct DemoVerifierSession {
    public let sessionID: String
    public let authorizationRequestUri: String
}

public final class DemoBackend {
    public static let shared = DemoBackend()
    private static let eudiPidSdJwtVct = "https://issuer2.demo.walt.id/openid4vci/urn:eudi:pid:1"
    public static let transactionDataProfilesURL = URL(string: "https://wallet.demo.walt.id/wallet-api/transaction-data-profiles")!
    private static let paymentAuthorizationType = "org.waltid.transaction-data.payment-authorization"
    private static let requiredPaymentAuthorizationFields: Set<String> = ["amount", "currency", "payee"]

    public static let scenarios: [DemoCredentialScenario] = [
        DemoCredentialScenario(
            id: "eudi-pid-sdjwt",
            displayName: "EUDI PID SD-JWT VC",
            profileId: "eudiPidSdJwt",
            credentialConfigurationId: "urn:eudi:pid:1",
            format: "dc+sd-jwt",
            verifierCredentialQuery: sdJwtQuery(
                id: "pid",
                vct: eudiPidSdJwtVct
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

    public static let presentationScenarios = scenarios

    public static let transactionDataPresentationScenario = scenarios.first { $0.id == "eudi-pid-sdjwt" }!

    public static let persistenceScenario = scenarios.first { $0.id == "eudi-pid-mdoc" }!

    private static let issuerBaseURL = URL(string: "https://issuer2.demo.walt.id")!
    private static let verifierBaseURL = URL(string: "https://verifier2.demo.walt.id")!

    private let client: WalletE2EClient

    public init(client: WalletE2EClient = WalletE2EClient()) {
        self.client = client
    }

    public func createOffer(
        scenario: DemoCredentialScenario,
        withGeneratedTransactionCode: Bool = false
    ) async throws -> DemoOffer {
        let endpoint = Self.issuerBaseURL
            .appendingPathComponent("issuer2")
            .appendingPathComponent("credential-offers")
        var payload: [String: Any] = [
            "profileId": scenario.profileId,
            "authMethod": "PRE_AUTHORIZED",
        ]
        if withGeneratedTransactionCode {
            payload["txCode"] = [
                "input_mode": "numeric",
                "length": 6,
                "description": "Enter the transaction code shown by the issuer",
            ]
        }
        let response = try await client.jsonRequest(
            url: endpoint,
            method: "POST",
            headers: ["Content-Type": "application/json"],
            body: Data(try jsonString(payload).utf8),
            retryTransientFailures: true
        )

        guard let offerUrl = response["credentialOffer"] as? String, !offerUrl.isEmpty else {
            throw NSError(
                domain: "WalletE2E",
                code: 300,
                userInfo: [NSLocalizedDescriptionKey: "Missing credentialOffer in public demo issuer2 response: \(response)"]
            )
        }

        let txCode = response["txCodeValue"] as? String ?? response["txCode"] as? String
        guard !withGeneratedTransactionCode || txCode != nil else {
            throw NSError(
                domain: "WalletE2E",
                code: 307,
                userInfo: [NSLocalizedDescriptionKey: "Public demo issuer2 did not return the requested transaction code: \(response)"]
            )
        }

        return DemoOffer(offerUrl: offerUrl, txCode: txCode)
    }

    public func createVerifierSession(scenario: DemoCredentialScenario) async throws -> DemoVerifierSession {
        try await createVerifierSession(
            scenario: scenario,
            transactionData: []
        )
    }

    public func createTransactionDataVerifierSession(
        scenario: DemoCredentialScenario = DemoBackend.transactionDataPresentationScenario
    ) async throws -> DemoVerifierSession {
        let fields = try await transactionDataProfileFields(type: Self.paymentAuthorizationType)
        let missingFields = Self.requiredPaymentAuthorizationFields.subtracting(fields)
        guard missingFields.isEmpty else {
            throw NSError(
                domain: "WalletE2E",
                code: 305,
                userInfo: [NSLocalizedDescriptionKey: "Public demo transaction data profile '\(Self.paymentAuthorizationType)' is missing required fields: \(missingFields.sorted().joined(separator: ", "))"]
            )
        }
        return try await createVerifierSession(
            scenario: scenario,
            transactionData: [Self.paymentAuthorizationTransactionData(credentialID: "pid", fields: fields)]
        )
    }

    public func transactionDataProfileFields(type: String) async throws -> Set<String> {
        let response = try await client.textRequest(
            url: Self.transactionDataProfilesURL,
            retryTransientFailures: true
        )
        let json = try JSONSerialization.jsonObject(with: Data(response.body.utf8), options: [])
        guard let profiles = json as? [[String: Any]],
              let profile = profiles.first(where: { $0["type"] as? String == type }),
              let fields = profile["fields"] as? [String] else {
            throw NSError(
                domain: "WalletE2E",
                code: 306,
                userInfo: [NSLocalizedDescriptionKey: "Missing public demo transaction data profile: \(type)"]
            )
        }
        return Set(fields)
    }

    private func createVerifierSession(
        scenario: DemoCredentialScenario,
        transactionData: [[String: Any]]
    ) async throws -> DemoVerifierSession {
        let endpoint = Self.verifierBaseURL
            .appendingPathComponent("verification-session")
            .appendingPathComponent("create")
        var payload: [String: Any] = [
            "flow_type": "cross_device",
            "core_flow": [
                "dcql_query": [
                    "credentials": [scenario.verifierCredentialQuery],
                ],
            ],
        ]
        if !transactionData.isEmpty {
            payload["openid"] = ["transactionData": transactionData]
        }
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
        // This fixture creates an unsigned session. The request_uri endpoint is a
        // Request Object endpoint and must not serve the unsigned JSON response.
        let requestURL = response["fullAuthorizationRequestUrl"] as? String
            ?? response["authorizationRequestUrl"] as? String
            ?? response["bootstrapAuthorizationRequestUrl"] as? String
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

    private static func paymentAuthorizationTransactionData(credentialID: String, fields: Set<String>) -> [String: Any] {
        var payload: [String: Any] = [
            "type": paymentAuthorizationType,
            "credential_ids": [credentialID],
            "require_cryptographic_holder_binding": true,
            "transaction_data_hashes_alg": ["sha-256"],
        ]
        payload.putProfileField(fields: fields, key: "amount", value: "42.00")
        payload.putProfileField(fields: fields, key: "currency", value: "EUR")
        payload.putProfileField(fields: fields, key: "payee", value: "ACME Corp")
        return payload
    }
}

private extension Dictionary where Key == String, Value == Any {
    mutating func putProfileField(fields: Set<String>, key: String, value: String) {
        if fields.contains(key) {
            self[key] = value
        }
    }
}
