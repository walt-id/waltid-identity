import Foundation
import WalletSDK
#if os(iOS)

/// Optional Enterprise KMS import integration. It gives the KMS an additional private-key copy;
/// it does not preserve a recovery record, configure remote signing, or synchronize credentials.
public actor EnterpriseIdentityKeyCustodian: IdentityKeyCustodian {
    /// Stable integration identifier.
    public nonisolated let id: String
    /// Destination label shown before explicit selection.
    public nonisolated let displayName: String
    private let resourceURL: URL
    private let configuration: URLSessionConfiguration
    private let authorize: @Sendable (URLRequest) async throws -> URLRequest

    /// Configures an explicit HTTPS KMS destination. The authorizer receives a body-free request.
    /// - Parameters:
    ///   - kmsResourceURL: Resource URL such as `https://enterprise.example/v1/org.kms`.
    ///   - id: Stable integration identifier.
    ///   - displayName: Destination label shown before selection.
    ///   - configuration: Host TLS and timeout settings; redirects are always refused.
    ///   - authorize: Adds authentication without changing the destination URL or method.
    public init(kmsResourceURL: URL, id: String = "enterprise-kms", displayName: String = "Enterprise KMS",
                configuration: URLSessionConfiguration = .ephemeral,
                authorize: @escaping @Sendable (URLRequest) async throws -> URLRequest) {
        precondition(kmsResourceURL.scheme == "https" && kmsResourceURL.user == nil && kmsResourceURL.password == nil)
        precondition(kmsResourceURL.query == nil && kmsResourceURL.fragment == nil && !kmsResourceURL.path.isEmpty && !kmsResourceURL.path.hasSuffix("/"))
        precondition(!id.isEmpty && !displayName.isEmpty)
        self.resourceURL = kmsResourceURL
        self.id = id
        self.displayName = displayName
        self.configuration = configuration
        self.authorize = authorize
    }

    /// Imports idempotently under the identity's stable key ID. The SDK verifies the returned public key.
    /// - Parameters:
    ///   - identity: Original identity and stable key ID.
    ///   - privateJWK: Secret P-256 JWK; never logged or returned in the receipt.
    public func importKey(identity: SigningIdentity, privateJWK: Data) async throws -> IdentityCustodyReceipt {
        guard identity.keyID.range(of: "^[A-Za-z0-9_-]{1,256}$", options: .regularExpression) != nil,
              let resource = URL(string: "\(resourceURL.absoluteString).\(identity.keyID)") else {
            throw IdentityProviderError.rejected
        }
        let endpoint = resource.appendingPathComponent("kms-service-api/keys/import/jwk")
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request = try await authorize(request)
        guard request.url == endpoint && request.httpMethod == "POST" else { throw IdentityProviderError.rejected }
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = privateJWK
        let session = URLSession(configuration: configuration, delegate: RefuseRedirects(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        do {
            let (stream, response) = try await session.bytes(for: request)
            guard let http = response as? HTTPURLResponse else { throw IdentityProviderError.rejected }
            switch http.statusCode {
            case 200, 201: break
            case 401: throw IdentityProviderError.interactionRequired
            case 403: throw IdentityProviderError.rejected
            case 409: throw IdentityProviderError.conflict
            case 429, 500...599: throw IdentityProviderError.temporarilyUnavailable
            default: throw IdentityProviderError.rejected
            }
            var body = Data()
            for try await byte in stream {
                guard body.count < 65_536 else { throw IdentityProviderError.rejected }
                body.append(byte)
            }
            defer { body.resetBytes(in: 0..<body.count) }
            guard let view = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any],
                  let key = view["key"] as? [String: Any], let jwk = key["jwk"] as? [String: Any] else {
                throw IdentityProviderError.rejected
            }
            let members = ["kty", "crv", "x", "y"]
            var publicKey: [String: String] = [:]
            for member in members {
                guard let value = jwk[member] as? String else { throw IdentityProviderError.rejected }
                publicKey[member] = value
            }
            guard publicKey["kty"] == "EC", publicKey["crv"] == "P-256",
                  let expected = (try? JSONSerialization.jsonObject(with: privateJWK)) as? [String: Any],
                  expected["kty"] as? String == "EC", expected["crv"] as? String == "P-256" else {
                throw IdentityProviderError.rejected
            }
            for coordinate in ["x", "y"] {
                guard let actual = publicKey[coordinate].flatMap(Self.coordinate),
                      let original = (expected[coordinate] as? String).flatMap(Self.coordinate) else {
                    throw IdentityProviderError.rejected
                }
                guard actual == original else { throw IdentityProviderError.conflict }
            }
            let publicData = try JSONSerialization.data(withJSONObject: publicKey, options: [.sortedKeys])
            return .init(keyReference: resource.absoluteString, publicJWK: String(decoding: publicData, as: UTF8.self))
        } catch is CancellationError { throw CancellationError() }
        catch let error as IdentityProviderError { throw error }
        catch {
            try Task.checkCancellation()
            throw IdentityProviderError.temporarilyUnavailable
        }
    }
    private static func coordinate(_ value: String) -> Data? {
        let base64 = value.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        guard let bytes = Data(base64Encoded: base64 + String(repeating: "=", count: (4 - base64.count % 4) % 4)),
              bytes.count == 32 else { return nil }
        return bytes
    }
}

private final class RefuseRedirects: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}
#endif
