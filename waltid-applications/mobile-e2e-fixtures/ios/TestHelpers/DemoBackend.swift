import Foundation
import CryptoKit

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

public struct DemoMetadataSigner {
    public let keyID: String?
    public let algorithm: String
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
    public static let issuerIdentifier = "https://issuer2.demo.walt.id/openid4vci"
    // RFC 7638 thumbprint of the public issuer2 signing key from /openid4vci/jwks.
    // This independent pin must not be learned from the signed metadata JWT.
    private static let issuerMetadataSigningKeyThumbprint = "XVz5i-iLcVBvjz5X4LGc6dA-VFNSyzWMW32LAHF8fss"
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

    public func createVerifierSession(
        scenario: DemoCredentialScenario,
        signedRequest: Bool
    ) async throws -> DemoVerifierSession {
        try await createVerifierSession(
            scenario: scenario,
            transactionData: [],
            signedRequest: signedRequest
        )
    }

    /** Verifies an ES256 signed-metadata JWS served by the public issuer2 demo. */
    public static func verifySignedIssuerMetadata(
        compactJWT: String,
        expectedCredentialIssuer: String
    ) throws -> DemoMetadataSigner {
        guard expectedCredentialIssuer == issuerIdentifier else {
            throw NSError(
                domain: "WalletE2E",
                code: 309,
                userInfo: [NSLocalizedDescriptionKey: "Unexpected public demo Credential Issuer: \(expectedCredentialIssuer)"]
            )
        }
        let parts = compactJWT.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 3,
              let headerData = base64URLData(String(parts[0])),
              let header = try JSONSerialization.jsonObject(with: headerData) as? [String: Any],
              let algorithm = header["alg"] as? String,
              algorithm == "ES256",
              let jwk = header["jwk"] as? [String: Any],
              jwk["kty"] as? String == "EC",
              jwk["crv"] as? String == "P-256",
              let x = jwk["x"] as? String,
              let y = jwk["y"] as? String,
              let xData = base64URLData(x),
              let yData = base64URLData(y),
              let signatureData = base64URLData(String(parts[2])) else {
            throw NSError(domain: "WalletE2E", code: 310, userInfo: [NSLocalizedDescriptionKey: "Malformed public demo signed metadata"])
        }
        let thumbprintJwk = "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"\(x)\",\"y\":\"\(y)\"}"
        let thumbprint = base64URLString(Data(SHA256.hash(data: Data(thumbprintJwk.utf8))))
        guard thumbprint == Self.issuerMetadataSigningKeyThumbprint else {
            throw NSError(
                domain: "WalletE2E",
                code: 312,
                userInfo: [NSLocalizedDescriptionKey: "Public demo signed metadata key is not the pinned issuer2 signing key"]
            )
        }
        let publicKey = try P256.Signing.PublicKey(x963Representation: Data([0x04]) + xData + yData)
        let signature = try P256.Signing.ECDSASignature(rawRepresentation: signatureData)
        guard publicKey.isValidSignature(signature, for: Data("\(parts[0]).\(parts[1])".utf8)) else {
            throw NSError(domain: "WalletE2E", code: 311, userInfo: [NSLocalizedDescriptionKey: "Invalid public demo signed metadata signature"])
        }
        return DemoMetadataSigner(keyID: header["kid"] as? String, algorithm: algorithm)
    }

    public func createResponseBoundVerifierSession(scenario: DemoCredentialScenario) async throws -> DemoVerifierSession {
        try await createVerifierSession(
            scenario: scenario,
            transactionData: [],
            bindClientIDToResponseURI: true
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
        transactionData: [[String: Any]],
        bindClientIDToResponseURI: Bool = false,
        signedRequest: Bool = false
    ) async throws -> DemoVerifierSession {
        let endpoint = Self.verifierBaseURL
            .appendingPathComponent("verification-session")
            .appendingPathComponent("create")
        let requestedSessionID = bindClientIDToResponseURI ? UUID().uuidString.lowercased() : nil
        var coreFlow: [String: Any] = [
            "dcql_query": [
                "credentials": [scenario.verifierCredentialQuery],
            ],
            "signed_request": signedRequest,
        ]
        if let requestedSessionID {
            let responseURI = Self.verifierBaseURL
                .appendingPathComponent("verification-session")
                .appendingPathComponent(requestedSessionID)
                .appendingPathComponent("response")
            coreFlow["sessionId"] = requestedSessionID
            coreFlow["clientId"] = "redirect_uri:\(responseURI.absoluteString)"
        }
        var payload: [String: Any] = [
            "flow_type": "cross_device",
            "core_flow": coreFlow,
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
        guard requestedSessionID == nil || requestedSessionID == sessionID else {
            throw NSError(
                domain: "WalletE2E",
                code: 308,
                userInfo: [NSLocalizedDescriptionKey: "Public demo verifier2 did not preserve the requested session ID"]
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

    public func waitForVerifierFailure(
        sessionID: String,
        expectedError: String,
        timeoutSeconds: TimeInterval
    ) async throws -> [String: Any] {
        let deadline = Date().addingTimeInterval(timeoutSeconds)

        while Date() < deadline {
            let url = Self.verifierBaseURL
                .appendingPathComponent("verification-session")
                .appendingPathComponent(sessionID)
                .appendingPathComponent("info")
            let response = try await client.jsonRequest(url: url, retryTransientFailures: true)
            let status = (response["status"] as? String)
                ?? ((response["session"] as? [String: Any])?["status"] as? String)

            switch status?.uppercased() {
            case "FAILED":
                guard let failure = response["failure"] as? [String: Any],
                      failure["error"] as? String == expectedError else {
                    throw NSError(
                        domain: "WalletE2E",
                        code: 305,
                        userInfo: [NSLocalizedDescriptionKey: "public demo verifier2 omitted \(expectedError) failure details for session \(sessionID): \(response)"]
                    )
                }
                return response
            case "SUCCESSFUL", "ERROR", "EXPIRED":
                throw NSError(
                    domain: "WalletE2E",
                    code: 306,
                    userInfo: [NSLocalizedDescriptionKey: "public demo verifier2 reported \(status ?? "UNKNOWN") instead of \(expectedError) for session \(sessionID): \(response)"]
                )
            default:
                try await Task.sleep(nanoseconds: 2_000_000_000)
            }
        }

        throw NSError(
            domain: "WalletE2E",
            code: 307,
            userInfo: [NSLocalizedDescriptionKey: "public demo verifier2 did not report \(expectedError) within \(timeoutSeconds)s for session \(sessionID)"]
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

private func base64URLData(_ value: String) -> Data? {
    let padded = value
        .replacingOccurrences(of: "-", with: "+")
        .replacingOccurrences(of: "_", with: "/")
        .padding(toLength: ((value.count + 3) / 4) * 4, withPad: "=", startingAt: 0)
    return Data(base64Encoded: padded)
}

private func base64URLString(_ value: Data) -> String {
    value.base64EncodedString()
        .replacingOccurrences(of: "+", with: "-")
        .replacingOccurrences(of: "/", with: "_")
        .replacingOccurrences(of: "=", with: "")
}

private extension Dictionary where Key == String, Value == Any {
    mutating func putProfileField(fields: Set<String>, key: String, value: String) {
        if fields.contains(key) {
            self[key] = value
        }
    }
}
