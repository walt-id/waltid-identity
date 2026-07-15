import Foundation
import TestHelpers

/// EUDI test backend helper for unit tests.
/// Uses the shared implementation from TestHelpers framework.
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
        let offer = try await offerFlow.generate(credentialID: credentialId)
        return GeneratedOffer(offerUrl: offer.offerUrl, txCode: offer.txCode)
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
            "request_uri_method": "post",
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
