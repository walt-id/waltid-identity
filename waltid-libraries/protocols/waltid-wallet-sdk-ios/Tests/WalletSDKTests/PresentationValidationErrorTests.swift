import XCTest
@testable import WalletSDK

final class PresentationValidationErrorTests: XCTestCase {
    func testLocalizedDescriptionIncludesProtocolAndResponseSafetyFailures() {
        let error = WalletError.presentationValidation(
            code: .invalidRequest,
            message: "Authorization Request nonce is required",
            responseSafetyFailure: "Unbound response destination"
        )
        XCTAssertEqual(error.localizedDescription,
            "invalid_request: Authorization Request nonce is required. Error response was not sent: Unbound response destination")
    }

#if canImport(WalletCore) && os(iOS)
    func testAuthenticatedInvalidRequestRetainsBothFailuresThroughNativeWallet() async throws {
        // Public verification material and a signed request only; no signing key is retained.
        let metadata = #"{"jwks":{"keys":[{"kty":"EC","x":"KH2qvLdBYAFx35kdYO96zr-b4Uqoe6auN4DZmGqk7C0","y":"uQ_zP-nB1_g_xmChTlvUQ8QEGJOqtpW_wji7TRvi9vw","crv":"P-256"}]}}"#
        let requestObject = "eyJhbGciOiJFUzI1NiIsInR5cCI6Im9hdXRoLWF1dGh6LXJlcStqd3QifQ.eyJjbGllbnRfaWQiOiJ2ZXJpZmllciIsImF1ZCI6Imh0dHBzOi8vc2VsZi1pc3N1ZWQubWUvdjIiLCJyZXNwb25zZV90eXBlIjoidnBfdG9rZW4iLCJyZXNwb25zZV9tb2RlIjoiZnJhZ21lbnQifQ.nZswGJNggigJzIAPrbSaTvAypdwipVxB4eUSJURugeoeY2nUTY4an_u0tXKVDQZTebh1RqVZwUqJDcyjxUO7Eg"
        let wallet = try await Wallet(configuration: WalletConfiguration(
            walletID: "validation-error-\(UUID().uuidString)",
            clientIDTrustConfiguration: .init(preRegisteredClientMetadataJSON: ["verifier": metadata]),
            persistence: .init(databaseKey: .provided(ValidationDatabaseKeyProvider())),
            defaultKeyUseAuthorizationPolicy: .none
        ))
        var components = URLComponents(string: "openid4vp://authorize")!
        components.queryItems = [
            URLQueryItem(name: "client_id", value: "verifier"),
            URLQueryItem(name: "request", value: requestObject)
        ]
        do {
            _ = try await wallet.previewPresentation(request: components.url!)
            XCTFail("Expected a local protocol error with its unsafe response channel")
        } catch let error as WalletError {
            guard case .presentationValidation(let code, let message, let responseSafetyFailure) = error else {
                XCTFail("Expected typed validation failure, got \(error)")
                try await wallet.deleteLocalData()
                return
            }
            XCTAssertEqual(code, .invalidRequest)
            XCTAssertEqual(message, "Authorization Request nonce is required")
            XCTAssertEqual(responseSafetyFailure,
                "Authorization Request redirect_uri is required; an error response cannot be sent safely")
            XCTAssertTrue(error.localizedDescription.contains(message))
            XCTAssertTrue(error.localizedDescription.contains(responseSafetyFailure))
        }
        try await wallet.deleteLocalData()
    }
#endif
}

#if canImport(WalletCore) && os(iOS)
private struct ValidationDatabaseKeyProvider: WalletDatabaseKeyProvider {
    func databaseKey(walletID: String, databaseName: String) async throws -> WalletDatabaseKey {
        WalletDatabaseKey(keyID: "validation-test", material: Data(repeating: 7, count: 32))
    }
    func deleteDatabaseKey(walletID: String, databaseName: String) async throws {}
}
#endif
