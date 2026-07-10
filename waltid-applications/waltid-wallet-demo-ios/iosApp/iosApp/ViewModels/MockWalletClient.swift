import Foundation
import WalletSDK

actor MockWalletClient: WalletClient {
    enum VerifierStyle {
        case named
        case did
        case x509SanDns
    }

    private var storedCredentials: [Credential]
    private let operationDelayNanoseconds: UInt64
    private let verifierStyle: VerifierStyle

    init(
        storedCredentials: [Credential] = [],
        operationDelayMilliseconds: UInt64 = 0,
        verifierStyle: VerifierStyle = .named
    ) {
        self.storedCredentials = storedCredentials
        self.operationDelayNanoseconds = operationDelayMilliseconds * 1_000_000
        self.verifierStyle = verifierStyle
    }

    func bootstrap() async throws -> WalletBootstrapResult {
        WalletBootstrapResult(keyID: "mock-key-1", did: "did:key:mock")
    }

    func credentials() async throws -> [Credential] {
        storedCredentials
    }

    func receive(offer: URL) async throws -> [String] {
        try await delayOperation()
        storedCredentials = [Self.sampleCredential]
        return storedCredentials.map(\.id)
    }

    func present(request: URL, did: String?) async throws -> PresentationResult {
        try await delayOperation()
        return PresentationResult(success: true, redirectTo: nil, verifierResponseJSON: nil)
    }

    func previewPresentation(request: URL) async throws -> PresentationPreview {
        try await delayOperation()
        return PresentationPreview(
            request: previewRequestInfo,
            credentialOptions: [
                PresentationCredentialOption(
                    queryID: "pid",
                    credentialID: Self.sampleCredential.id,
                    format: Self.sampleCredential.format,
                    issuer: Self.sampleCredential.issuer,
                    subject: Self.sampleCredential.subject,
                    label: Self.sampleCredential.label,
                    credentialDataJSON: Self.sampleCredential.credentialDataJSON,
                    disclosures: [
                        PresentationDisclosure(
                            path: "$.given_name",
                            name: "given_name",
                            valueJSON: "\"Ada\"",
                            displayValue: "Ada",
                            selectivelyDisclosable: true
                        ),
                        PresentationDisclosure(
                            path: "$.portrait",
                            name: nil,
                            valueJSON: Self.samplePortraitDisclosureValueJSON,
                            displayValue: nil,
                            selectivelyDisclosable: true
                        )
                    ]
                )
            ]
        )
    }

    func submitPresentation(request: URL, selectedCredentialIDs: [String], did: String?) async throws -> PresentationResult {
        try await delayOperation()
        return PresentationResult(success: true, redirectTo: nil, verifierResponseJSON: nil)
    }

    private func delayOperation() async throws {
        guard operationDelayNanoseconds > 0 else { return }
        try await Task.sleep(nanoseconds: operationDelayNanoseconds)
    }

    private var previewRequestInfo: PresentationRequestInfo {
        PresentationRequestInfo(
            clientID: verifierClientID,
            verifierName: verifierName,
            responseURI: URL(string: "https://verifier.example/response"),
            state: "state-123",
            nonce: "nonce-456"
        )
    }

    private var verifierClientID: String {
        switch verifierStyle {
        case .named: return "https://verifier.example/client"
        case .did: return Self.didClientID
        case .x509SanDns: return Self.x509SanDnsClientID
        }
    }

    private var verifierName: String? {
        switch verifierStyle {
        case .named: return "Example Verifier"
        case .did, .x509SanDns: return nil
        }
    }

    private static let didClientID = "decentralized_identifier:did:jwk:abc"
    private static let x509SanDnsClientID = "x509_san_dns:verifier.example"
    private static let samplePortraitDisclosureValueJSON = "[-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0, 1, 8, 4, 0, 0, 0, -75, 28, 12, 2, 0, 0, 0, 11, 73, 68, 65, 84, 120, -38, 99, -4, -1, 31, 0, 3, 3, 2, 0, -17, -65, -89, -34, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126]"

    private static let sampleCredential = Credential(
        id: "cred-1",
        format: "jwt_vc_json",
        issuer: "Example Issuer",
        subject: nil,
        label: "Example Credential",
        addedAt: ISO8601DateFormatter().date(from: "2026-07-09T12:00:00Z"),
        credentialDataJSON: """
        {
          "vct": "https://issuer.example/credential-types/mobile-driving-licence",
          "given_name": "Ada",
          "family_name": "Lovelace",
          "valid_to": 1781654400,
          "resident_address": {
            "street_address": "Main Street 1",
            "locality": "Vienna"
          },
          "portrait": {
            "elementValue": [-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0, 1, 8, 4, 0, 0, 0, -75, 28, 12, 2, 0, 0, 0, 11, 73, 68, 65, 84, 120, -38, 99, -4, -1, 31, 0, 3, 3, 2, 0, -17, -65, -89, -34, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126]
          }
        }
        """
    )
}
