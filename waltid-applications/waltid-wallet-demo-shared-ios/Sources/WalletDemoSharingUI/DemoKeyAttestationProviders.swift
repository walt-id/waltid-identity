import CryptoKit
import Foundation
import WalletSDK

/// Demo-only test attesters. Their claims do not establish hardware protection, authentication,
/// certification or production Wallet Unit Attestation. Unknown issuers have no synthetic fallback.
public struct DemoKeyAttestationProviders: KeyAttestationProviderResolver {
    public static let itbIssuer = DemoAttestationProfiles.shared.itb.issuer
    public static let eudiIssuer = DemoAttestationProfiles.shared.eudi.issuer

    public init() {}

    public func resolve(credentialIssuer: String) async throws -> (any KeyAttestationProvider)? {
        try Task.checkCancellation()
        switch credentialIssuer {
        case Self.itbIssuer:
            return ItbDemoKeyAttester()
        case Self.eudiIssuer:
            let response = try await EudiDemoKeyAttester.request(path: DemoAttestationProfiles.shared.eudi.jwksPath)
            guard let keys = response["keys"] as? [[String: Any]], keys.count == 1 else {
                throw DemoKeyAttestationError.invalidResponse
            }
            let data = try JSONSerialization.data(withJSONObject: keys[0], options: [.sortedKeys])
            return EudiDemoKeyAttester(verificationPublicJWK: String(decoding: data, as: UTF8.self))
        default:
            return nil
        }
    }
}

private enum DemoKeyAttestationError: LocalizedError {
    case invalidResponse
    case unsupportedIssuer

    var errorDescription: String? {
        switch self {
        case .invalidResponse: "EUDI demo key attester returned an invalid response."
        case .unsupportedIssuer: "No demo key attester is configured for this issuer."
        }
    }
}

private struct EudiDemoKeyAttester: KeyAttestationProvider {
    let verificationPublicJWK: String

    func attest(_ request: KeyAttestationRequest) async throws -> String {
        guard request.credentialIssuer == DemoKeyAttestationProviders.eudiIssuer else {
            throw DemoKeyAttestationError.unsupportedIssuer
        }
        let proofKey = try JSONSerialization.jsonObject(with: Data(request.proofKeyJWK.utf8))
        var payload: [String: Any] = [
            "jwkSet": ["keys": [proofKey]],
            "supportedSigningAlgorithms": DemoAttestationProfiles.shared.eudi.signingAlgorithms,
        ]
        if let nonce = request.nonce { payload["nonce"] = nonce }
        let response = try await Self.request(path: DemoAttestationProfiles.shared.eudi.attestationPath, payload: payload)
        guard let jwt = response["keyAttestation"] as? String, !jwt.isEmpty else {
            throw DemoKeyAttestationError.invalidResponse
        }
        return jwt
    }

    static func request(path: String, payload: [String: Any]? = nil) async throws -> [String: Any] {
        let url = URL(string: DemoAttestationProfiles.shared.eudi.baseUrl + path)!
        var request = URLRequest(url: url, timeoutInterval: 30)
        if let payload {
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        }
        let session = URLSession(configuration: .ephemeral, delegate: RejectRedirects(), delegateQueue: nil)
        defer { session.finishTasksAndInvalidate() }
        let (data, response) = try await session.data(for: request)
        try Task.checkCancellation()
        guard (response as? HTTPURLResponse)?.statusCode == 200,
              let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw DemoKeyAttestationError.invalidResponse
        }
        return json
    }

    private final class RejectRedirects: NSObject, URLSessionTaskDelegate, Sendable {
        func urlSession(_ session: URLSession, task: URLSessionTask,
                        willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
                        completionHandler: @escaping (URLRequest?) -> Void) {
            completionHandler(nil)
        }
    }
}

private struct ItbDemoKeyAttester: KeyAttestationProvider {
    private let signingKey = P256.Signing.PrivateKey()

    var verificationPublicJWK: String {
        // P-256 uncompressed representation: 0x04 followed by 32-byte x and y coordinates.
        let bytes = signingKey.publicKey.x963Representation
        let jwk = ["kty": "EC", "crv": "P-256",
                   "x": Data(bytes[1..<33]).base64URL, "y": Data(bytes[33..<65]).base64URL]
        return String(decoding: try! JSONSerialization.data(withJSONObject: jwk, options: [.sortedKeys]), as: UTF8.self)
    }

    func attest(_ request: KeyAttestationRequest) async throws -> String {
        guard request.credentialIssuer == DemoKeyAttestationProviders.itbIssuer else {
            throw DemoKeyAttestationError.unsupportedIssuer
        }
        try Task.checkCancellation()
        let now = Int64(Date().timeIntervalSince1970)
        let profile = DemoAttestationProfiles.shared.itb
        let header: [String: Any] = [
            "alg": profile.algorithm,
            "typ": profile.jwtType,
            "jwk": try JSONSerialization.jsonObject(with: Data(verificationPublicJWK.utf8)),
        ]
        var payload: [String: Any] = [
            "iat": now, "exp": now + profile.lifetimeSeconds,
            "attested_keys": [try JSONSerialization.jsonObject(with: Data(request.proofKeyJWK.utf8))],
            "key_storage": profile.claims.keyStorage,
            "user_authentication": profile.claims.userAuthentication,
            "certification": profile.claims.certification,
            "key_storage_status": [
                "status": ["status_list": [
                    "uri": profile.claims.keyStorageStatus.status.statusList.uri,
                    "idx": profile.claims.keyStorageStatus.status.statusList.idx,
                ]],
                "exp": now + profile.statusLifetimeSeconds,
            ],
        ]
        if let nonce = request.nonce { payload["nonce"] = nonce }
        let input = try JSONSerialization.data(withJSONObject: header, options: [.sortedKeys]).base64URL
            + "." + JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys]).base64URL
        let signature = try signingKey.signature(for: Data(input.utf8)).rawRepresentation.base64URL
        return input + "." + signature
    }
}

private extension Data {
    var base64URL: String {
        base64EncodedString().replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }
}

/// The same profile resource is compiled into the Kotlin demo/test adapters by sources.gradle.kts.
private struct DemoAttestationProfiles: Decodable, Sendable {
    let itb: Itb
    let eudi: Eudi

    static let shared: Self = {
        do {
            guard let url = Bundle.module.url(forResource: "KeyAttestationProfiles", withExtension: "json") else {
                preconditionFailure("Demo key attestation profiles are missing")
            }
            let decoder = JSONDecoder()
            decoder.keyDecodingStrategy = .convertFromSnakeCase
            let profiles = try decoder.decode(Self.self, from: Data(contentsOf: url))
            precondition(profiles.itb.algorithm == "ES256" && profiles.eudi.signingAlgorithms == ["ES256"],
                         "Demo key attestation signing adapters require ES256")
            precondition(profiles.itb.jwtType == "key-attestation+jwt"
                         && profiles.itb.lifetimeSeconds > 0 && profiles.itb.statusLifetimeSeconds > 0,
                         "Demo key attestation type and lifetimes are invalid")
            return profiles
        } catch {
            preconditionFailure("Demo key attestation profiles are invalid")
        }
    }()

    struct Itb: Decodable, Sendable {
        let issuer: String
        let algorithm: String
        let jwtType: String
        let lifetimeSeconds: Int64
        let statusLifetimeSeconds: Int64
        let claims: Claims
    }
    struct Eudi: Decodable, Sendable {
        let issuer: String
        let baseUrl: String
        let jwksPath: String
        let attestationPath: String
        let signingAlgorithms: [String]
    }
    struct Claims: Decodable, Sendable {
        let keyStorage: [String]
        let userAuthentication: [String]
        let certification: String
        let keyStorageStatus: KeyStorageStatus
    }
    struct KeyStorageStatus: Decodable, Sendable { let status: Status }
    struct Status: Decodable, Sendable { let statusList: StatusList }
    struct StatusList: Decodable, Sendable { let uri: String; let idx: Int }
}
