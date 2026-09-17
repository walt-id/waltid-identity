#if os(iOS)
import Foundation
import XCTest
@testable import WalletSDK
import WalletSDKEnterpriseCustody

final class EnterpriseCustodyIntegrationTests: XCTestCase {
    enum FixtureFailure: Error { case unexpectedState(String) }
    // Matches Kotlin's synthetic private JWK, endpoint, and HTTP failure matrix.
    func testMatchesKotlinEnterpriseFixtures() async throws {
        let jwk = #"{"kty":"EC","crv":"P-256","x":"_owZzgkFGR68KYqSRXklMfJvDOziRgY56Lw5y39waoI","y":"anebTPlpuKDlOcf2L7PTCtaqj4DjDx0Siq_WiiznLqA","d":"885_2uV-GjENh_HrvebzKL4Kmc28rfTWWJzyneS4_9I"}"#
        let identity = SigningIdentity(id: "identity", keyID: "key-1", did: "did:jwk:fixture", publicJWK: "{}",
            storage: .encryptedDatabase, authorization: .none, origin: .imported, securityLevel: .software,
            authorizationEvidence: .unknown, attestation: nil, recovery: .disabled, custody: [])
        let cases: [(String, IdentityProviderError?)] = [("201", nil), ("301", .rejected), ("401", .interactionRequired),
            ("403", .rejected), ("409", .conflict), ("429", .temporarilyUnavailable), ("503", .temporarilyUnavailable)]
        for (scenario, expected) in cases {
            let configuration = URLSessionConfiguration.ephemeral
            configuration.protocolClasses = [CustodyProtocolFixture.self]
            let adapter = EnterpriseIdentityKeyCustodian(kmsResourceURL: URL(string: "https://enterprise.example/v1/org.kms")!,
                configuration: configuration) { request in
                    XCTAssertNil(request.httpBody)
                    var request = request
                    request.setValue("fixture", forHTTPHeaderField: "Authorization")
                    request.setValue(scenario, forHTTPHeaderField: "X-Test-Scenario")
                    request.setValue(jwk, forHTTPHeaderField: "X-Test-Expected-Body")
                    return request
                }
            do {
                let receipt = try await adapter.importKey(identity: identity, privateJWK: Data(jwk.utf8))
                XCTAssertNil(expected)
                XCTAssertEqual(receipt.keyReference, "https://enterprise.example/v1/org.kms.key-1")
                let publicKey = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(receipt.publicJWK.utf8)) as? [String: String])
                XCTAssertNil(publicKey["d"])
                XCTAssertEqual(publicKey["x"], "_owZzgkFGR68KYqSRXklMfJvDOziRgY56Lw5y39waoI")
            } catch let error as IdentityProviderError { XCTAssertEqual(error, expected) }
        }
    }

    func testOptionalAdapterImportsOriginalIdentityAndPreservesRecoveryState() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [CustodyProtocolFixture.self]
        let adapter = EnterpriseIdentityKeyCustodian(kmsResourceURL: URL(string: "https://enterprise.example/v1/org.kms")!,
            configuration: configuration) { request in
                XCTAssertNil(request.httpBody, "Authentication callback must not receive the private key")
                var authorized = request
                authorized.setValue("fixture", forHTTPHeaderField: "Authorization")
                return authorized
            }
        let wallet = try await Wallet(configuration: .init(walletID: "custody-contract-\(UUID())",
            persistence: .init(databaseKey: .provided(CustodyDatabaseKeyFixture())),
            signingIdentity: .init(authorization: .explicit(.none), keyCustodians: [adapter])))
        do {
            let identities = await wallet.signingIdentity
            guard case .available(let recommended, let alternatives) = try await identities.creationOptions(),
                  let option = ([recommended] + alternatives).first(where: { $0.storage == .encryptedDatabase }),
                  case .active(let original) = try await identities.create(option) else { throw FixtureFailure.unexpectedState("Software identity unavailable") }
            let choices = try await identities.custodyOptions(identityID: original.id)
            let custody = try XCTUnwrap(choices.first)
            for _ in 0..<2 {
                guard case .imported(let receipt) = try await identities.copyToCustody(custody) else { throw FixtureFailure.unexpectedState("Custody failed") }
                XCTAssertEqual(receipt.keyReference, "https://enterprise.example/v1/org.kms.\(original.keyID)")
            }
            guard case .active(let active) = try await identities.state() else { throw FixtureFailure.unexpectedState("Local identity lost") }
            XCTAssertEqual(active.did, original.did)
            XCTAssertEqual(active.publicJWK, original.publicJWK)
            XCTAssertEqual(active.custody.count, 1)
            guard case .disabled = active.recovery else { throw FixtureFailure.unexpectedState("Custody must not claim recovery") }
            try await wallet.deleteLocalData()
        } catch { try? await wallet.deleteLocalData(); throw error }
    }

    func testAdapterFailuresReachTheSharedCustodyResult() async throws {
        let cases: [(String, SigningIdentityFailure)] = [("401", .providerInteractionRequired), ("409", .providerConflict),
            ("503", .providerUnavailable), ("malformed", .providerRejected), ("oversized", .providerRejected), ("mismatch", .providerConflict)]
        for (scenario, expected) in cases {
            let configuration = URLSessionConfiguration.ephemeral
            configuration.protocolClasses = [CustodyProtocolFixture.self]
            let adapter = EnterpriseIdentityKeyCustodian(kmsResourceURL: URL(string: "https://enterprise.example/v1/org.kms")!,
                configuration: configuration) { request in
                    var authorized = request
                    authorized.setValue("fixture", forHTTPHeaderField: "Authorization")
                    authorized.setValue(scenario, forHTTPHeaderField: "X-Test-Scenario")
                    return authorized
                }
            let wallet = try await Wallet(configuration: .init(walletID: "custody-errors-\(UUID())",
                persistence: .init(databaseKey: .provided(CustodyDatabaseKeyFixture())),
            signingIdentity: .init(authorization: .explicit(.none), keyCustodians: [adapter])))
            do {
                let identities = await wallet.signingIdentity
                guard case .available(let recommended, let alternatives) = try await identities.creationOptions(),
                      let option = ([recommended] + alternatives).first(where: { $0.storage == .encryptedDatabase }),
                      case .active(let identity) = try await identities.create(option) else { throw FixtureFailure.unexpectedState("Identity unavailable") }
                let options = try await identities.custodyOptions(identityID: identity.id)
                let choice = try XCTUnwrap(options.first)
                guard case .failed(let actual) = try await identities.copyToCustody(choice) else { throw FixtureFailure.unexpectedState("Expected failure") }
                XCTAssertEqual(actual, expected, scenario)
                guard case .active(let active) = try await identities.state() else { throw FixtureFailure.unexpectedState("Lost local identity") }
                XCTAssertTrue(active.custody.isEmpty)
                try await wallet.deleteLocalData()
            } catch { try? await wallet.deleteLocalData(); throw error }
        }
    }

    func testRecoveryDiscoveryKeepsHealthyCandidatesAndTypedProviderFailures() async throws {
        let healthy = FailingRecoveryFixture(id: "healthy")
        let broken = FailingRecoveryFixture(id: "broken")
        await healthy.allowStore()
        await broken.failListing()
        let wallet = try await Wallet(configuration: .init(walletID: "discovery-contract-\(UUID())",
            persistence: .init(databaseKey: .provided(CustodyDatabaseKeyFixture())),
            signingIdentity: .init(authorization: .explicit(.none), recoveryProviders: [healthy, broken])))
        do {
            let manager = await wallet.signingIdentity
            guard case .available(let recommended, let alternatives) = try await manager.creationOptions(intent: .recoverable),
                  let option = ([recommended] + alternatives).first(where: { $0.storage == .encryptedDatabase && $0.recoveryProviderName == "healthy" }),
                  case .active = try await manager.create(option) else { throw FixtureFailure.unexpectedState("Expected healthy creation route") }
            let discovery = try await manager.discoverRecovery()
            XCTAssertEqual(discovery.candidates.map { $0.reference.providerID }, ["healthy"])
            XCTAssertEqual(discovery.failures.map { $0.providerID }, ["broken"])
            XCTAssertEqual(discovery.failures.first?.reason, .providerInteractionRequired)
            XCTAssertEqual(discovery.failures.first?.message, "Unlock or sign in to this recovery provider, then retry.")
            try await wallet.deleteLocalData()
        } catch { try? await wallet.deleteLocalData(); throw error }
    }

    func testSwiftProviderFailureSurvivesKotlinBridgeAndPendingRetry() async throws {
        let provider = FailingRecoveryFixture()
        let wallet = try await Wallet(configuration: .init(walletID: "provider-error-\(UUID())",
            persistence: .init(databaseKey: .provided(CustodyDatabaseKeyFixture())),
            signingIdentity: .init(authorization: .explicit(.none), recoveryProviders: [provider])))
        do {
            let identities = await wallet.signingIdentity
            guard case .available(let recommended, let alternatives) = try await identities.creationOptions(intent: .recoverable),
                  let selected = ([recommended] + alternatives).first(where: { $0.storage == .encryptedDatabase }),
                  case .pending(let id, let reason) = try await identities.create(selected) else { throw FixtureFailure.unexpectedState("Expected pending setup") }
            XCTAssertEqual(reason, .providerConflict)
            guard case .pending(let storedID, let storedReason) = try await identities.state() else { throw FixtureFailure.unexpectedState("Pending state lost") }
            XCTAssertEqual(storedID, id)
            XCTAssertEqual(storedReason, .providerConflict)
            await provider.allowStore()
            guard case .active(let active) = try await identities.resumePending(identityID: id) else { throw FixtureFailure.unexpectedState("Retry failed") }
            XCTAssertEqual(active.id, id)
            try await wallet.deleteLocalData()
        } catch { try? await wallet.deleteLocalData(); throw error }
    }
}

private final class CustodyProtocolFixture: URLProtocol, @unchecked Sendable {
    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "enterprise.example" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        do {
            XCTAssertEqual(request.httpMethod, "POST")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "fixture")
            XCTAssertTrue(request.url!.path.hasSuffix("/kms-service-api/keys/import/jwk"))
            var body = request.httpBody ?? Data()
            if let stream = request.httpBodyStream {
                stream.open()
                defer { stream.close() }
                var buffer = [UInt8](repeating: 0, count: 4096)
                while stream.hasBytesAvailable {
                    let count = stream.read(&buffer, maxLength: buffer.count)
                    guard count > 0 else { break }
                    body.append(contentsOf: buffer.prefix(count))
                }
            }
            if let expected = request.value(forHTTPHeaderField: "X-Test-Expected-Body") {
                XCTAssertEqual(request.url?.absoluteString, "https://enterprise.example/v1/org.kms.key-1/kms-service-api/keys/import/jwk")
                XCTAssertEqual(String(decoding: body, as: UTF8.self), expected)
            }
            var jwk = try JSONSerialization.jsonObject(with: body) as! [String: Any]
            let scenario = request.value(forHTTPHeaderField: "X-Test-Scenario") ?? "success"
            if scenario == "mismatch" { jwk["x"] = jwk["y"] }
            let response: Data
            if scenario == "malformed" { response = Data("{}".utf8) }
            else if scenario == "oversized" { response = Data(repeating: 32, count: 65_537) }
            else { response = try JSONSerialization.data(withJSONObject: ["key": ["type": "jwk", "jwk": jwk]]) }
            client?.urlProtocol(self, didReceive: HTTPURLResponse(url: request.url!, statusCode: Int(scenario) ?? 201, httpVersion: nil, headerFields: nil)!, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: response)
            client?.urlProtocolDidFinishLoading(self)
        } catch { client?.urlProtocol(self, didFailWithError: error) }
    }
    override func stopLoading() {}
}

private actor FailingRecoveryFixture: IdentityRecoveryProvider {
    nonisolated let id: String
    nonisolated var displayName: String { id }
    private var fail = true
    private var listingFailed = false
    init(id: String = "failing-contract") { self.id = id }
    func failListing() { listingFailed = true }
    private var records: [String: Data] = [:]
    func allowStore() { fail = false }
    func availability() async throws -> WalletRecoveryAvailability { .available(protection: .applicationEncrypted, scope: .custom) }
    func list() async throws -> [String] {
        if listingFailed { throw IdentityProviderError.interactionRequired }
        return Array(records.keys)
    }
    func store(recordID: String, data: Data) async throws -> WalletRecoveryReceipt {
        if fail { throw IdentityProviderError.conflict }
        records[recordID] = data
        return .acceptedLocally
    }
    func retrieve(recordID: String) async throws -> Data? { records[recordID] }
    func delete(recordID: String) async throws -> WalletRecoveryReceipt { records[recordID] = nil; return .confirmedByProvider }
}
/// Public, test-only encryption material. Each fixture has a unique wallet database.
private struct CustodyDatabaseKeyFixture: WalletDatabaseKeyProvider {
    func databaseKey(walletID: String, databaseName: String) async throws -> WalletDatabaseKey {
        WalletDatabaseKey(keyID: "fixture-\(walletID)", material: Data(repeating: 0x74, count: 32))
    }
    func deleteDatabaseKey(walletID: String, databaseName: String) async throws {}
}
#endif
