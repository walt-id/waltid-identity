#if canImport(WalletCore) && os(iOS)
import XCTest
@preconcurrency import WalletCore
@testable import WalletSDK

final class KMPKeyAttestationProviderTests: XCTestCase {
    func testAdapterForwardsRequestAndReturnsProviderJWT() async throws {
        let provider = StubProvider { request in
            XCTAssertEqual(request.credentialIssuer, "https://issuer.example")
            XCTAssertEqual(request.proofKeyJWK, "proof-public-jwk")
            XCTAssertEqual(request.nonce, "fresh-nonce")
            XCTAssertEqual(request.requiredKeyStorage, ["secure_element", "tee"])
            XCTAssertEqual(request.requiredUserAuthentication, ["biometric"])
            return "signed.attestation.jwt"
        }
        let adapter = KMPKeyAttestationProviderAdapter(provider: provider)
        XCTAssertEqual(adapter.verificationPublicJwk, provider.verificationPublicJWK)

        let jwt = try await adapter.__attest(request: WalletBridgeKeyAttestationRequest(
            credentialIssuer: "https://issuer.example",
            proofKeyJwk: "proof-public-jwk",
            nonce: "fresh-nonce",
            requiredKeyStorage: ["secure_element", "tee"],
            requiredUserAuthentication: ["biometric"]
        ))
        XCTAssertEqual(jwt, "signed.attestation.jwt")
    }

    func testAdapterPreservesAbsentNonceAndConstraints() async throws {
        let adapter = KMPKeyAttestationProviderAdapter(provider: StubProvider { request in
            XCTAssertNil(request.nonce)
            XCTAssertNil(request.requiredKeyStorage)
            XCTAssertNil(request.requiredUserAuthentication)
            return "signed.attestation.jwt"
        })
        let jwt = try await adapter.__attest(request: unconstrainedRequest())
        XCTAssertEqual(jwt, "signed.attestation.jwt")
    }

    func testAdapterPropagatesProviderFailure() async {
        let adapter = KMPKeyAttestationProviderAdapter(provider: StubProvider { _ in
            throw ProviderFailure.unavailable
        })
        do {
            _ = try await adapter.__attest(request: unconstrainedRequest())
            XCTFail("Provider failure must reach the caller")
        } catch {
            XCTAssertEqual(error as? ProviderFailure, .unavailable)
        }
    }

    private func unconstrainedRequest() -> WalletBridgeKeyAttestationRequest {
        WalletBridgeKeyAttestationRequest(
            credentialIssuer: "https://issuer.example",
            proofKeyJwk: "proof-public-jwk",
            nonce: nil,
            requiredKeyStorage: nil,
            requiredUserAuthentication: nil
        )
    }

    private enum ProviderFailure: Error, Equatable { case unavailable }

    private struct StubProvider: KeyAttestationProvider {
        let verificationPublicJWK = "attester-public-jwk"
        let operation: @Sendable (KeyAttestationRequest) async throws -> String

        func attest(_ request: KeyAttestationRequest) async throws -> String {
            try await operation(request)
        }
    }
}
#endif
