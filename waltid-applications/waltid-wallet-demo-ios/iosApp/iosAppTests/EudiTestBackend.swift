import Foundation
import TestHelpers

/// EUDI test backend helper for unit tests.
/// Uses the shared implementation from TestHelpers framework.
actor EudiTestBackend {
    static let shared = EudiTestBackend()

    /// Pinned `PID Issuer CA 02` test-PKI anchor used by the EUDI verifier.
    static let verifierTrustAnchorPEM = """
    -----BEGIN CERTIFICATE-----
    MIIC0zCCAnmgAwIBAgIUXRXxkLbUM6+njr/XT0IIw/HA/uowCgYIKoZIzj0EAwMw
    VzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxs
    ZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJFVTAeFw0yNTA0
    MDkwMDAzMzBaFw0zNDA3MDYwMDAzMjlaMFcxGTAXBgNVBAMMEFBJRCBJc3N1ZXIg
    Q0EgMDIxLTArBgNVBAoMJEVVREkgV2FsbGV0IFJlZmVyZW5jZSBJbXBsZW1lbnRh
    dGlvbjELMAkGA1UEBhMCRVUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARkqdLm
    wIlv+SSWr00tAIrt7EAMztgd3w9qA6qEm16yVfsLcyx2f4oIWuH45wa37J9GoNWp
    deo27VoSoNMCzxOYo4IBITCCAR0wEgYDVR0TAQH/BAgwBgEB/wIBADAfBgNVHSME
    GDAWgBRCUFC+ELgQ8J1EXI2/qxAI7ifcSTATBgNVHSUEDDAKBggrgQICAAABBzBD
    BgNVHR8EPDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9j
    cmwvcGlkX0NBX0VVXzAyLmNybDAdBgNVHQ4EFgQUQlBQvhC4EPCdRFyNv6sQCO4n
    3EkwDgYDVR0PAQH/BAQDAgEGMF0GA1UdEgRWMFSGUmh0dHBzOi8vZ2l0aHViLmNv
    bS9ldS1kaWdpdGFsLWlkZW50aXR5LXdhbGxldC9hcmNoaXRlY3R1cmUtYW5kLXJl
    ZmVyZW5jZS1mcmFtZXdvcmswCgYIKoZIzj0EAwMDSAAwRQIhAIavYfC5o0VVLKfg
    TKkzzWgc09hzDMsCl3O2le2sQfG7AiA2soqAN5gtUOLQKWK00DUz22EW79rvaV+V
    JPvfdQeokA==
    -----END CERTIFICATE-----
    """

    private let client = WalletE2EClient()
    private let offerFlow: EudiOfferFlow

    private init() {
        self.offerFlow = EudiOfferFlow(client: client)
    }

    struct VerifierTransaction {
        let transactionId: String
        let authorizationRequestUri: String
    }

    func generateOffer(credentialId: String = "eu.europa.ec.eudi.pid_vc_sd_jwt") async throws -> EudiGeneratedOffer {
        try await offerFlow.generate(credentialID: credentialId)
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

    func createVerifierTransaction(
        credentialId: String = "eu.europa.ec.eudi.pid_vc_sd_jwt",
        encryptedResponse: Bool = false
    ) async throws -> VerifierTransaction {
        var payload: [String: Any] = [
            "dcql_query": TestHelpers.buildDcqlQuery(credentialID: credentialId),
            "nonce": UUID().uuidString,
            "request_uri_method": "post",
            "profile": "openid4vp",
            "authorization_request_uri": "openid4vp://"
        ]
        if encryptedResponse {
            payload["encrypted_response"] = true
        }

        let response = try await client.jsonRequest(
            url: URL(string: "https://verifier-backend.eudiw.dev/ui/presentations/v2")!,
            method: "POST",
            headers: ["Content-Type": "application/json"],
            body: Data(try jsonString(payload).utf8)
        )

        guard let transactionId = response["transaction_id"] as? String,
              let authRequestUri = response["authorization_request_uri"] as? String else {
            throw NSError(domain: "EudiTestBackend", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid verifier response"])
        }

        return VerifierTransaction(transactionId: transactionId, authorizationRequestUri: authRequestUri)
    }

}
