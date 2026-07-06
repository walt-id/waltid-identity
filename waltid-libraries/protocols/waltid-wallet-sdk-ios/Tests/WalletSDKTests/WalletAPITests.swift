import XCTest
@testable import WalletSDK

final class WalletAPITests: XCTestCase {
    func testPublicConfigurationUsesStableDefaults() {
        let configuration = WalletConfiguration()

        acceptsSendable(configuration)
        XCTAssertEqual(configuration.walletID, "default")
        XCTAssertEqual(configuration.defaultKeyType, .secp256r1)
        XCTAssertTrue(configuration.persistence.isSdkManagedEncrypted)
        XCTAssertNil(configuration.attestation)
    }

    func testPublicPersistenceConfigurationUsesEncryptedDefault() {
        let configuration = WalletConfiguration(persistence: .sdkManagedEncrypted)

        acceptsSendable(configuration.persistence)
        XCTAssertTrue(configuration.persistence.isSdkManagedEncrypted)
    }

    func testPublicPersistenceConfigurationAcceptsIntegratorManagedKeyProvider() {
        let provider = FakeDatabaseKeyProvider()
        let configuration = WalletConfiguration(persistence: .integratorManagedKey(provider))

        acceptsSendable(configuration.persistence)
        XCTAssertTrue(configuration.persistence.isIntegratorManagedKey)
    }

    func testWalletDatabaseKeyDescriptionRedactsMaterial() {
        let key = WalletDatabaseKey(
            keyID: "consumer-wallet:wallet_consumer-wallet",
            material: Data([1, 2, 3, 4])
        )

        XCTAssertEqual(
            String(describing: key),
            "WalletDatabaseKey(keyID: consumer-wallet:wallet_consumer-wallet, material: <redacted>)"
        )
        XCTAssertEqual(String(reflecting: key), String(describing: key))
        XCTAssertFalse(String(describing: key).contains("1 bytes"))
        XCTAssertFalse(String(describing: key).contains("4 bytes"))
    }

    func testPublicModelsAreValueTypesAndEquatable() {
        let credential = Credential(
            id: "credential-1",
            format: "vc+sd-jwt",
            issuer: "https://issuer.example",
            subject: "did:key:subject",
            label: "PID",
            addedAt: nil
        )

        acceptsSendable(credential)
        XCTAssertEqual(credential.id, "credential-1")
        XCTAssertEqual(credential, credential)
    }

    func testWalletHasAsyncFacadeShape() async {
        let wallet = try? await Wallet(configuration: .init())

        XCTAssertNotNil(wallet)
    }

    func testBootstrapForwardsDefaultKeyTypeAndDidMethod() async throws {
        let bridge = FakeWalletCoreBridge()
        bridge.bootstrapResult = .init(keyID: "key-1", did: "did:jwk:abc")
        let wallet = Wallet(
            configuration: .init(defaultKeyType: .ed25519),
            bridge: bridge
        )

        let result = try await wallet.bootstrap(didMethod: "jwk")

        XCTAssertEqual(result, .init(keyID: "key-1", did: "did:jwk:abc"))
        XCTAssertEqual(bridge.bootstrapCalls.count, 1)
        XCTAssertEqual(bridge.bootstrapCalls.first?.keyType, .ed25519)
        XCTAssertEqual(bridge.bootstrapCalls.first?.didMethod, "jwk")
    }

    func testBootstrapForwardsExplicitKeyType() async throws {
        let bridge = FakeWalletCoreBridge()
        let wallet = Wallet(
            configuration: .init(defaultKeyType: .secp256r1),
            bridge: bridge
        )

        _ = try await wallet.bootstrap(keyType: .rsa4096)

        XCTAssertEqual(bridge.bootstrapCalls.first?.keyType, .rsa4096)
    }

    func testReceiveForwardsOfferAndReturnsCredentialIDs() async throws {
        let offer = URL(string: "openid-credential-offer://issuer.example?credential_offer=abc")!
        let bridge = FakeWalletCoreBridge()
        bridge.receiveResult = ["credential-1", "credential-2"]
        let wallet = Wallet(bridge: bridge)

        let result = try await wallet.receive(
            offer: offer,
            txCode: "1234",
            clientID: "ios-client"
        )

        XCTAssertEqual(result, ["credential-1", "credential-2"])
        XCTAssertEqual(bridge.receiveCalls.count, 1)
        XCTAssertEqual(bridge.receiveCalls.first?.offer, offer)
        XCTAssertEqual(bridge.receiveCalls.first?.txCode, "1234")
        XCTAssertEqual(bridge.receiveCalls.first?.clientID, "ios-client")
    }

    func testCredentialsReturnsWalletCredentials() async throws {
        let credential = Credential(
            id: "credential-1",
            format: "vc+sd-jwt",
            issuer: "https://issuer.example",
            subject: "did:key:subject",
            label: "PID",
            addedAt: Date(timeIntervalSince1970: 1_700_000_000)
        )
        let bridge = FakeWalletCoreBridge()
        bridge.credentialsResult = [credential]
        let wallet = Wallet(bridge: bridge)

        let result = try await wallet.credentials()

        XCTAssertEqual(result, [credential])
        XCTAssertEqual(bridge.credentialsCallCount, 1)
    }

    func testDeleteLocalDataForwardsToBridge() async throws {
        let bridge = FakeWalletCoreBridge()
        let wallet = Wallet(bridge: bridge)

        try await wallet.deleteLocalData()

        XCTAssertEqual(bridge.deleteLocalDataCallCount, 1)
    }

    func testPresentForwardsRequestAndReturnsPresentationResult() async throws {
        let request = URL(string: "openid4vp://verifier.example?request_uri=abc")!
        let redirect = URL(string: "https://verifier.example/callback")!
        let bridge = FakeWalletCoreBridge()
        bridge.presentResult = .init(
            success: true,
            redirectTo: redirect,
            verifierResponseJSON: #"{"status":"ok"}"#
        )
        let wallet = Wallet(bridge: bridge)

        let result = try await wallet.present(
            request: request,
            did: "did:key:wallet",
            runPolicies: true
        )

        XCTAssertEqual(result.redirectTo, redirect)
        XCTAssertEqual(result.verifierResponseJSON, #"{"status":"ok"}"#)
        XCTAssertEqual(bridge.presentCalls.count, 1)
        XCTAssertEqual(bridge.presentCalls.first?.request, request)
        XCTAssertEqual(bridge.presentCalls.first?.did, "did:key:wallet")
        XCTAssertEqual(bridge.presentCalls.first?.runPolicies, true)
    }

    func testBridgeErrorsSurfaceAsWalletErrors() async {
        let bridge = FakeWalletCoreBridge()
        bridge.error = .invalidInput("missing offer")
        let wallet = Wallet(bridge: bridge)
        let offer = URL(string: "openid-credential-offer://issuer.example")!

        do {
            _ = try await wallet.receive(offer: offer)
            XCTFail("Expected receive to throw")
        } catch let error as WalletError {
            XCTAssertEqual(error, .invalidInput("missing offer"))
        } catch {
            XCTFail("Expected WalletError, got \(error)")
        }
    }

    func testEventsReturnsBridgeEventStream() async {
        let event = WalletEvent(name: "receive", phase: .issuance, status: .progress)
        let bridge = FakeWalletCoreBridge(events: [event])
        let wallet = Wallet(bridge: bridge)

        let events = await wallet.events
        var iterator = events.makeAsyncIterator()

        let first = await iterator.next()
        let second = await iterator.next()

        XCTAssertEqual(first, event)
        XCTAssertNil(second)
    }

    private func acceptsSendable<T: Sendable>(_ value: T) {
        _ = value
    }
}

private extension WalletPersistenceConfiguration {
    var isSdkManagedEncrypted: Bool {
        switch self {
        case .sdkManagedEncrypted:
            return true
        case .integratorManagedKey:
            return false
        }
    }

    var isIntegratorManagedKey: Bool {
        switch self {
        case .sdkManagedEncrypted:
            return false
        case .integratorManagedKey:
            return true
        }
    }
}

private struct FakeDatabaseKeyProvider: WalletDatabaseKeyProvider {
    func databaseKey(walletID: String, databaseName: String) async throws -> WalletDatabaseKey {
        WalletDatabaseKey(keyID: "\(walletID)-\(databaseName)", material: Data(repeating: 7, count: 32))
    }

    func deleteDatabaseKey(walletID: String, databaseName: String) async throws {
        _ = walletID
        _ = databaseName
    }
}

private final class FakeWalletCoreBridge: WalletCoreBridge, @unchecked Sendable {
    struct BootstrapCall {
        let keyType: WalletKeyType
        let didMethod: String
    }

    struct ReceiveCall {
        let offer: URL
        let txCode: String?
        let clientID: String
    }

    struct PresentCall {
        let request: URL
        let did: String?
        let runPolicies: Bool?
    }

    var events: AsyncStream<WalletEvent>
    var error: WalletError?
    var bootstrapResult = WalletBootstrapResult(keyID: "key", did: "did:key:wallet")
    var receiveResult: [String] = []
    var credentialsResult: [Credential] = []
    var presentResult = PresentationResult(success: true, redirectTo: nil, verifierResponseJSON: nil)
    private(set) var bootstrapCalls: [BootstrapCall] = []
    private(set) var receiveCalls: [ReceiveCall] = []
    private(set) var credentialsCallCount = 0
    private(set) var deleteLocalDataCallCount = 0
    private(set) var presentCalls: [PresentCall] = []

    init(events: [WalletEvent] = []) {
        self.events = AsyncStream { continuation in
            for event in events {
                continuation.yield(event)
            }
            continuation.finish()
        }
    }

    func bootstrap(keyType: WalletKeyType, didMethod: String) async throws -> WalletBootstrapResult {
        if let error {
            throw error
        }

        bootstrapCalls.append(.init(keyType: keyType, didMethod: didMethod))
        return bootstrapResult
    }

    func receive(offer: URL, txCode: String?, clientID: String) async throws -> [String] {
        if let error {
            throw error
        }

        receiveCalls.append(.init(offer: offer, txCode: txCode, clientID: clientID))
        return receiveResult
    }

    func credentials() async throws -> [Credential] {
        if let error {
            throw error
        }

        credentialsCallCount += 1
        return credentialsResult
    }

    func deleteLocalData() async throws {
        if let error {
            throw error
        }

        deleteLocalDataCallCount += 1
    }

    func present(request: URL, did: String?, runPolicies: Bool?) async throws -> PresentationResult {
        if let error {
            throw error
        }

        presentCalls.append(.init(request: request, did: did, runPolicies: runPolicies))
        return presentResult
    }
}
