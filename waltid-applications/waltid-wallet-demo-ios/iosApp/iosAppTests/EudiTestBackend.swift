import Foundation
import TestHelpers

/// EUDI test backend helper for unit tests.
/// Uses the shared implementation from TestHelpers framework.
actor EudiTestBackend {
    static let shared = EudiTestBackend()

    /// Pinned `PID Issuer CA - UT 02` test-PKI anchor used by the EUDI verifier.
    static let verifierTrustAnchorPEM = """
    -----BEGIN CERTIFICATE-----
    MIIC3TCCAoOgAwIBAgIUEwybFc9Jw+az3r188OiHDaxCfHEwCgYIKoZIzj0EAwMw
    XDEeMBwGA1UEAwwVUElEIElzc3VlciBDQSAtIFVUIDAyMS0wKwYDVQQKDCRFVURJ
    IFdhbGxldCBSZWZlcmVuY2UgSW1wbGVtZW50YXRpb24xCzAJBgNVBAYTAlVUMB4X
    DTI1MDMyNDIwMjYxNFoXDTM0MDYyMDIwMjYxM1owXDEeMBwGA1UEAwwVUElEIElz
    c3VlciBDQSAtIFVUIDAyMS0wKwYDVQQKDCRFVURJIFdhbGxldCBSZWZlcmVuY2Ug
    SW1wbGVtZW50YXRpb24xCzAJBgNVBAYTAlVUMFkwEwYHKoZIzj0CAQYIKoZIzj0D
    AQcDQgAEesDKj9rCIcrGj0wbSXYvCV953bOPSYLZH5TNmhTz2xa7VdlvQgQeGZRg
    1PrF5AFwt070wvL9qr1DUDdvLp6a1qOCASEwggEdMBIGA1UdEwEB/wQIMAYBAf8C
    AQAwHwYDVR0jBBgwFoAUYseURyi9D6IWIKeawkmURPEB08cwEwYDVR0lBAwwCgYI
    K4ECAgAAAQcwQwYDVR0fBDwwOjA4oDagNIYyaHR0cHM6Ly9wcmVwcm9kLnBraS5l
    dWRpdy5kZXYvY3JsL3BpZF9DQV9VVF8wMi5jcmwwHQYDVR0OBBYEFGLHlEcovQ+i
    FiCnmsJJlETxAdPHMA4GA1UdDwEB/wQEAwIBBjBdBgNVHRIEVjBUhlJodHRwczov
    L2dpdGh1Yi5jb20vZXUtZGlnaXRhbC1pZGVudGl0eS13YWxsZXQvYXJjaGl0ZWN0
    dXJlLWFuZC1yZWZlcmVuY2UtZnJhbWV3b3JrMAoGCCqGSM49BAMDA0gAMEUCIQCe
    4R9rO4JhFp821kO8Gkb8rXm4qGG/e5/Oi2XmnTQqOQIgfFs+LDbnP2/j1MB4rwZ1
    FgGdpr4oyrFB9daZyRIcP90=
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

    func createVerifierTransaction(credentialId: String = "eu.europa.ec.eudi.pid_vc_sd_jwt") async throws -> VerifierTransaction {
        let payload: [String: Any] = [
            "dcql_query": TestHelpers.buildDcqlQuery(credentialID: credentialId),
            "nonce": UUID().uuidString,
            // The EUDI verifier currently returns HTTP 400 for POST request-object
            // retrieval, so use the default GET transport it supports.
            "profile": "openid4vp",
            "authorization_request_uri": "openid4vp://"
        ]

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
