import XCTest
@testable import WalletSDK

final class WalletAPITests: XCTestCase {
    func testPublicConfigurationUsesStableDefaults() {
        let configuration = WalletConfiguration()

        acceptsSendable(configuration)
        XCTAssertEqual(configuration.walletID, "default")
        XCTAssertEqual(configuration.defaultKeyType, .secp256r1)
        XCTAssertTrue(configuration.persistence.databaseKey.isManaged)
        XCTAssertNil(configuration.persistence.stores.credentials)
        XCTAssertNil(configuration.persistence.stores.dids)
        XCTAssertNil(configuration.persistence.stores.keys)
        XCTAssertNil(configuration.attestation)
        XCTAssertTrue(configuration.transactionDataProfiles.isEmpty)
    }

    func testPublicPersistenceConfigurationUsesEncryptedDefault() {
        let configuration = WalletConfiguration(persistence: WalletPersistence(databaseKey: .managed))

        acceptsSendable(configuration.persistence)
        XCTAssertTrue(configuration.persistence.databaseKey.isManaged)
    }

    func testPublicPersistenceConfigurationAcceptsProvidedDatabaseKeyProvider() {
        let provider = FakeDatabaseKeyProvider()
        let configuration = WalletConfiguration(
            persistence: WalletPersistence(databaseKey: .provided(provider))
        )

        acceptsSendable(configuration.persistence)
        XCTAssertTrue(configuration.persistence.databaseKey.isProvided)
    }

    func testPublicPersistenceConfigurationAcceptsCustomCredentialStore() {
        let store = FakeCredentialStore()
        let configuration = WalletConfiguration(
            persistence: WalletPersistence(stores: WalletStores(credentials: store))
        )

        acceptsSendable(configuration.persistence)
        XCTAssertNotNil(configuration.persistence.stores.credentials)
    }

    func testPublicPersistenceConfigurationCombinesProvidedDatabaseKeyAndCustomCredentialStore() {
        let provider = FakeDatabaseKeyProvider()
        let store = FakeCredentialStore()
        let configuration = WalletConfiguration(
            persistence: WalletPersistence(
                databaseKey: .provided(provider),
                stores: WalletStores(credentials: store)
            )
        )

        acceptsSendable(configuration.persistence)
        XCTAssertTrue(configuration.persistence.databaseKey.isProvided)
        XCTAssertNotNil(configuration.persistence.stores.credentials)
    }

    func testPublicWalletStoresExposeCredentialDidAndKeyOverrides() {
        let credentialStore = FakeCredentialStore()
        let didStore = FakeDidStore()
        let keyStore = FakeKeyStore()
        let keys = WalletKeys(store: keyStore) { keyType in
            StoredKey(
                keyID: "generated-\(keyType)",
                keyType: keyType,
                algorithm: nil,
                serializedKeyJSON: #"{"type":"jwk","jwk":{"kid":"generated"}}"#
            )
        }
        let stores = WalletStores(
            credentials: credentialStore,
            dids: didStore,
            keys: keys
        )

        acceptsSendable(stores)
        XCTAssertNotNil(stores.credentials)
        XCTAssertNotNil(stores.dids)
        XCTAssertNotNil(stores.keys)
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

    func testStoredKeyDescriptionRedactsSerializedKeyJSON() {
        let key = StoredKey(
            keyID: "key-1",
            keyType: .secp256r1,
            algorithm: "ES256",
            serializedKeyJSON: #"{"type":"jwk","jwk":{"kid":"key-1","d":"secret"}}"#
        )

        XCTAssertEqual(
            String(describing: key),
            "StoredKey(keyID: key-1, keyType: secp256r1, algorithm: ES256, serializedKeyJSON: <redacted>)"
        )
        XCTAssertEqual(String(reflecting: key), String(describing: key))
        XCTAssertFalse(String(describing: key).contains("secret"))
        XCTAssertFalse(String(reflecting: key).contains("secret"))
    }

    func testPublicModelsAreValueTypesAndEquatable() {
        let credential = Credential(
            id: "credential-1",
            format: "vc+sd-jwt",
            issuer: "https://issuer.example",
            subject: "did:key:subject",
            label: "PID",
            addedAt: nil,
            credentialDataJSON: #"{"given_name":"Ada"}"#
        )

        acceptsSendable(credential)
        XCTAssertEqual(credential.id, "credential-1")
        XCTAssertEqual(credential.credentialDataJSON, #"{"given_name":"Ada"}"#)
        XCTAssertEqual(credential, credential)

        let storedCredential = StoredCredential(
            id: "stored-credential-1",
            serializedCredential: #"{"type":["VerifiableCredential"]}"#,
            format: "jwt_vc_json",
            label: "PID",
            addedAt: Date(timeIntervalSince1970: 1_700_000_000)
        )
        acceptsSendable(storedCredential)
        XCTAssertEqual(storedCredential, storedCredential)

        let storedDid = StoredDid(
            did: "did:key:wallet",
            documentJSON: #"{"id":"did:key:wallet"}"#
        )
        acceptsSendable(storedDid)
        XCTAssertEqual(storedDid.id, "did:key:wallet")
        XCTAssertEqual(storedDid, storedDid)

        let storedKey = StoredKey(
            keyID: "key-1",
            keyType: .secp256r1,
            algorithm: "ES256",
            serializedKeyJSON: #"{"type":"jwk","jwk":{"kid":"key-1"}}"#
        )
        acceptsSendable(storedKey)
        XCTAssertEqual(storedKey.id, "key-1")
        XCTAssertEqual(storedKey, storedKey)
    }

    func testPresentationPreviewModelsAreValueTypesAndEquatable() {
        let preview = PresentationPreview(
            request: .init(
                clientID: "https://verifier.example",
                verifierName: "Example Verifier",
                responseURI: URL(string: "https://verifier.example/direct-post"),
                state: "state-1",
                nonce: "nonce-1"
            ),
            credentialOptions: [
                .init(
                    queryID: "pid",
                    credentialID: "credential-1",
                    multiple: true,
                    format: "vc+sd-jwt",
                    issuer: "https://issuer.example",
                    subject: "did:key:subject",
                    label: "PID",
                    credentialDataJSON: #"{"given_name":"Ada"}"#,
                    disclosures: [
                        .init(
                            path: "$.given_name",
                            name: "given_name",
                            valueJSON: #""Ada""#,
                            displayValue: "Ada",
                            selectivelyDisclosable: true
                        )
                    ]
                )
            ],
            credentialRequirements: [
                PresentationCredentialRequirement(options: [["pid"]])
            ]
        )

        acceptsSendable(preview)
        XCTAssertEqual(preview.credentialOptions.single?.credentialID, "credential-1")
        XCTAssertEqual(preview.credentialOptions.single?.multiple, true)
        XCTAssertEqual(preview.credentialOptions.single?.selection, PresentationCredentialSelection(queryID: "pid", credentialID: "credential-1"))
        XCTAssertEqual(preview.credentialOptions.single?.id, preview.credentialOptions.single?.selection.id)
        XCTAssertEqual(preview.credentialRequirements.single?.options, [["pid"]])
    }

    func testWalletHasAsyncFacadeShape() async {
        let wallet = Wallet(configuration: .init(), bridge: FakeWalletCoreBridge())

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

    func testResolveOfferForwardsOfferAndReturnsResolution() async throws {
        let offer = URL(string: "openid-credential-offer://issuer.example")!
        let bridge = FakeWalletCoreBridge()
        let resolution = OfferResolution(
            transactionCodeRequired: true,
            credentialIssuer: "https://issuer.example",
            offeredCredentials: ["ExampleCredential"]
        )
        bridge.offerResolutionResult = resolution
        let wallet = Wallet(bridge: bridge)

        let result = try await wallet.resolveOffer(offer: offer)

        XCTAssertEqual(result, resolution)
        XCTAssertEqual(bridge.resolvedOffers, [offer])
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
            addedAt: Date(timeIntervalSince1970: 1_700_000_000),
            credentialDataJSON: #"{"given_name":"Ada"}"#
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
        let redirectURL = URL(string: "https://verifier.example/continue")!
        let bridge = FakeWalletCoreBridge()
        bridge.presentResult = .transmitted(
            .succeeded(
                verifierResponseJSON: #"{"status":"ok"}"#,
                redirectURL: redirectURL
            )
        )
        let wallet = Wallet(bridge: bridge)

        let result = try await wallet.present(
            request: request,
            did: "did:key:wallet",
            runPolicies: true
        )

        XCTAssertEqual(
            result,
            .transmitted(
                .succeeded(
                    verifierResponseJSON: #"{"status":"ok"}"#,
                    redirectURL: redirectURL
                )
            )
        )
        XCTAssertEqual(bridge.presentCalls.count, 1)
        XCTAssertEqual(bridge.presentCalls.first?.request, request)
        XCTAssertEqual(bridge.presentCalls.first?.did, "did:key:wallet")
        XCTAssertEqual(bridge.presentCalls.first?.runPolicies, true)
    }

    func testPreviewPresentationForwardsRequestAndReturnsPreview() async throws {
        let request = URL(string: "openid4vp://verifier.example?request_uri=abc")!
        let bridge = FakeWalletCoreBridge()
        bridge.previewResult = .ready(
            .init(
                request: .init(
                    clientID: "https://verifier.example",
                    verifierName: "Example Verifier",
                    responseURI: nil,
                    state: nil,
                    nonce: "nonce-1"
                ),
                credentialOptions: [
                    .init(
                        queryID: "pid",
                        credentialID: "credential-1",
                        multiple: true,
                        format: "vc+sd-jwt",
                        issuer: nil,
                        subject: nil,
                        label: "PID",
                        credentialDataJSON: "{}",
                        disclosures: []
                    )
                ]
            )
        )
        let wallet = Wallet(bridge: bridge)

        let result = try await wallet.previewPresentation(request: request)

        guard case .ready(let preview) = result else {
            return XCTFail("Expected a ready preview")
        }
        XCTAssertEqual(preview.request.clientID, "https://verifier.example")
        XCTAssertEqual(preview.credentialOptions.single?.credentialID, "credential-1")
        XCTAssertEqual(preview.credentialOptions.single?.multiple, true)
        XCTAssertEqual(bridge.previewCalls, [request])
    }

    func testPreviewPresentationReturnsTypedProtocolError() async throws {
        let request = URL(string: "openid4vp://verifier.example?request_uri=abc")!
        let requestInfo = PresentationRequestInfo(
            clientID: "https://verifier.example",
            verifierName: "Example Verifier"
        )
        let bridge = FakeWalletCoreBridge()
        bridge.previewResult = .invalid(
            .init(
                request: requestInfo,
                code: .invalidTransactionData,
                message: "Unsupported transaction_data type"
            )
        )
        let wallet = Wallet(bridge: bridge)

        let result = try await wallet.previewPresentation(request: request)

        XCTAssertEqual(
            result,
            .invalid(.init(
                request: requestInfo,
                code: .invalidTransactionData,
                message: "Unsupported transaction_data type"
            ))
        )
        XCTAssertEqual(bridge.previewCalls, [request])
    }

    func testSubmitPresentationForwardsSelectionAndReturnsResult() async throws {
        let request = URL(string: "openid4vp://verifier.example?request_uri=abc")!
        let bridge = FakeWalletCoreBridge()
        let wallet = Wallet(bridge: bridge)

        _ = try await wallet.submitPresentation(
            request: request,
            selectedCredentialOptions: [PresentationCredentialSelection(queryID: "pid", credentialID: "credential-1")],
            selectedDisclosureOptions: [PresentationDisclosureSelection(queryID: "pid", credentialID: "credential-1", path: "$.given_name")],
            did: "did:key:wallet",
            runPolicies: false
        )

        XCTAssertEqual(bridge.submitCalls.count, 1)
        XCTAssertEqual(bridge.submitCalls.first?.request, request)
        XCTAssertEqual(bridge.submitCalls.first?.selectedCredentialOptions, [PresentationCredentialSelection(queryID: "pid", credentialID: "credential-1")])
        XCTAssertEqual(bridge.submitCalls.first?.selectedDisclosureOptions, [PresentationDisclosureSelection(queryID: "pid", credentialID: "credential-1", path: "$.given_name")])
        XCTAssertEqual(bridge.submitCalls.first?.did, "did:key:wallet")
        XCTAssertEqual(bridge.submitCalls.first?.runPolicies, false)
    }

    func testPresentationErrorCodesMatchOpenID4VPValues() {
        XCTAssertEqual(
            [
                PresentationErrorCode.accessDenied.errorCode,
                PresentationErrorCode.invalidRequest.errorCode,
                PresentationErrorCode.invalidClient.errorCode,
                PresentationErrorCode.invalidScope.errorCode,
                PresentationErrorCode.unauthorizedClient.errorCode,
                PresentationErrorCode.unsupportedResponseType.errorCode,
                PresentationErrorCode.serverError.errorCode,
                PresentationErrorCode.temporarilyUnavailable.errorCode,
                PresentationErrorCode.vpFormatsNotSupported.errorCode,
                PresentationErrorCode.invalidRequestURIMethod.errorCode,
                PresentationErrorCode.invalidTransactionData.errorCode,
                PresentationErrorCode.walletUnavailable.errorCode,
            ],
            [
                "access_denied",
                "invalid_request",
                "invalid_client",
                "invalid_scope",
                "unauthorized_client",
                "unsupported_response_type",
                "server_error",
                "temporarily_unavailable",
                "vp_formats_not_supported",
                "invalid_request_uri_method",
                "invalid_transaction_data",
                "wallet_unavailable",
            ]
        )
    }

    func testRejectPresentationForwardsErrorDetailsAndReturnsResult() async throws {
        let request = URL(string: "openid4vp://verifier.example?request_uri=abc")!
        let bridge = FakeWalletCoreBridge()
        let responseURL = URL(string: "https://verifier.example/callback?error=access_denied")!
        bridge.rejectResult = .prepared(.openURL(responseURL))
        let wallet = Wallet(bridge: bridge)

        let result = try await wallet.rejectPresentation(
            request: request,
            error: .accessDenied,
            errorDescription: "User declined"
        )

        XCTAssertEqual(bridge.rejectCalls.count, 1)
        XCTAssertEqual(bridge.rejectCalls.first?.request, request)
        XCTAssertEqual(bridge.rejectCalls.first?.error, .accessDenied)
        XCTAssertEqual(bridge.rejectCalls.first?.errorDescription, "User declined")
        XCTAssertEqual(result, .prepared(.openURL(responseURL)))
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

private extension WalletDatabaseKeyConfiguration {
    var isManaged: Bool {
        switch self {
        case .managed:
            return true
        case .provided:
            return false
        }
    }

    var isProvided: Bool {
        switch self {
        case .managed:
            return false
        case .provided:
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

private final class FakeCredentialStore: WalletCredentialStore, @unchecked Sendable {
    private var entries: [StoredCredential] = []

    func credential(id: String) async throws -> StoredCredential? {
        entries.first { $0.id == id }
    }

    func credentials() async throws -> [StoredCredential] {
        entries
    }

    func addCredential(_ credential: StoredCredential) async throws {
        entries.removeAll { $0.id == credential.id }
        entries.append(credential)
    }

    func removeCredential(id: String) async throws -> Bool {
        let originalCount = entries.count
        entries.removeAll { $0.id == id }
        return entries.count != originalCount
    }
}

private final class FakeDidStore: WalletDidStore, @unchecked Sendable {
    private var entries: [StoredDid] = []

    func did(id: String) async throws -> StoredDid? {
        entries.first { $0.id == id }
    }

    func dids() async throws -> [StoredDid] {
        entries
    }

    func addDid(_ did: StoredDid) async throws {
        entries.removeAll { $0.id == did.id }
        entries.append(did)
    }

    func removeDid(id: String) async throws -> Bool {
        let originalCount = entries.count
        entries.removeAll { $0.id == id }
        return entries.count != originalCount
    }
}

private final class FakeKeyStore: WalletKeyStore, @unchecked Sendable {
    private var entries: [StoredKey] = []

    func key(id: String) async throws -> StoredKey? {
        entries.first { $0.id == id }
    }

    func keys() async throws -> [WalletKeyInfo] {
        entries.map { WalletKeyInfo(keyID: $0.keyID, keyType: $0.keyType, algorithm: $0.algorithm) }
    }

    func addKey(_ key: StoredKey) async throws -> String {
        entries.removeAll { $0.id == key.id }
        entries.append(key)
        return key.keyID
    }

    func removeKey(id: String) async throws -> Bool {
        let originalCount = entries.count
        entries.removeAll { $0.id == id }
        return entries.count != originalCount
    }
}

private extension Array {
    var single: Element? {
        count == 1 ? first : nil
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

    struct SubmitCall {
        let request: URL
        let selectedCredentialOptions: [PresentationCredentialSelection]
        let selectedDisclosureOptions: [PresentationDisclosureSelection]?
        let did: String?
        let runPolicies: Bool?
    }

    struct RejectCall {
        let request: URL
        let error: PresentationErrorCode?
        let errorDescription: String?
    }

    var events: AsyncStream<WalletEvent>
    var error: WalletError?
    var bootstrapResult = WalletBootstrapResult(keyID: "key", did: "did:key:wallet")
    var offerResolutionResult = OfferResolution(
        transactionCodeRequired: false,
        credentialIssuer: "https://issuer.example",
        offeredCredentials: ["ExampleCredential"]
    )
    var receiveResult: [String] = []
    var credentialsResult: [Credential] = []
    var presentResult = PresentationResult.transmitted(.succeeded(verifierResponseJSON: "{}"))
    var previewResult = PresentationPreviewResult.ready(
        PresentationPreview(request: .init(clientID: nil), credentialOptions: [])
    )
    var submitResult = PresentationResult.transmitted(.succeeded(verifierResponseJSON: "{}"))
    var rejectResult = PresentationResult.transmitted(.succeeded(verifierResponseJSON: "{}"))
    private(set) var bootstrapCalls: [BootstrapCall] = []
    private(set) var resolvedOffers: [URL] = []
    private(set) var receiveCalls: [ReceiveCall] = []
    private(set) var credentialsCallCount = 0
    private(set) var deleteLocalDataCallCount = 0
    private(set) var presentCalls: [PresentCall] = []
    private(set) var previewCalls: [URL] = []
    private(set) var submitCalls: [SubmitCall] = []
    private(set) var rejectCalls: [RejectCall] = []

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

    func resolveOffer(offer: URL) async throws -> OfferResolution {
        if let error {
            throw error
        }

        resolvedOffers.append(offer)
        return offerResolutionResult
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

    func previewPresentation(request: URL) async throws -> PresentationPreviewResult {
        if let error {
            throw error
        }

        previewCalls.append(request)
        return previewResult
    }

    func submitPresentation(
        request: URL,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection]?,
        did: String?,
        runPolicies: Bool?
    ) async throws -> PresentationResult {
        if let error {
            throw error
        }

        submitCalls.append(
            .init(
                request: request,
                selectedCredentialOptions: selectedCredentialOptions,
                selectedDisclosureOptions: selectedDisclosureOptions,
                did: did,
                runPolicies: runPolicies
            )
        )
        return submitResult
    }

    func rejectPresentation(
        request: URL,
        error: PresentationErrorCode?,
        errorDescription: String?
    ) async throws -> PresentationResult {
        if let failure = self.error {
            throw failure
        }

        rejectCalls.append(
            .init(
                request: request,
                error: error,
                errorDescription: errorDescription
            )
        )
        return rejectResult
    }
}
