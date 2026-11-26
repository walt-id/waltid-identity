import XCTest
@testable import WalletSDK
#if os(iOS)
import WalletSDKKeychainRecovery
#endif

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
    func testInvalidPlatformConfigurationIsRecoverableBeforeStorage() async throws {
        let provider = KeychainIdentityRecovery(namespace: "validation")
        let base = WalletConfiguration(persistence: .init(databaseKey: .provided(UnusedDatabaseKeyProvider())))
        let invalid: [(inout WalletConfiguration) -> Void] = [
            { $0.keyAuthorization.policy = .biometricTimedReuse(timeoutSeconds: 0) },
            { $0.signingIdentity.alternativeAuthorizations = [.deviceCredential(timeoutSeconds: Int.max)] },
            { $0.signingIdentity.recoveryProviders = [provider, provider] },
            { $0.keyAuthorization.prompt.cancelText = " " },
            { $0.crossProcessAccess = .init(appGroupIdentifier: "", keychainAccessGroup: "group") },
            { $0.signingIdentity.keychain = .init(accessGroup: "") },
        ]
        for change in invalid {
            var configuration = base
            change(&configuration)
            do {
                _ = try await Wallet(configuration: configuration)
                XCTFail("Expected invalid platform configuration")
            } catch WalletError.invalidInput { }
        }
    }

    func testCancelledOpenLeavesStorageUntouched() async throws {
        let config = WalletConfiguration(persistence: .init(databaseKey: .provided(UnusedDatabaseKeyProvider())))
        let open = Task {
            withUnsafeCurrentTask { $0?.cancel() }
            return try await Wallet(configuration: config)
        }
        do {
            _ = try await open.value
            XCTFail("Expected cancellation")
        } catch is CancellationError {}
    }

    func testCancellationWhileObtainingDatabaseKeyReachesNativeOpen() async throws {
        let entered = expectation(description: "native factory requested the database key")
        let provider = PausedDatabaseKeyProvider(entered: entered)
        let config = WalletConfiguration(walletID: "cancel-open-\(UUID().uuidString)",
                                        persistence: .init(databaseKey: .provided(provider)))
        let open = Task { try await Wallet(configuration: config) }
        await fulfillment(of: [entered], timeout: 5)
        open.cancel()
        await provider.release()
        do {
            let wallet = try await open.value
            try await wallet.deleteLocalData()
            XCTFail("Expected native open to propagate cancellation")
        } catch is CancellationError { }
    }

    func testDuplicateProfilesFailBeforeOpeningStorage() async throws {
        let profile = WalletTransactionDataProfile(type: "example.transaction")
        let config = WalletConfiguration(
            persistence: .init(databaseKey: .provided(UnusedDatabaseKeyProvider())),
            presentation: .init(transactionDataProfiles: [profile, profile])
        )
        do {
            _ = try await Wallet(configuration: config)
            XCTFail("Expected duplicate transaction profiles to fail")
        } catch WalletError.invalidInput(let reason) {
            XCTAssertTrue(reason.contains("unique"))
        }
    }

    func testAuthenticatedInvalidRequestRetainsBothFailuresThroughNativeWallet() async throws {
        // Public verification material and a signed request only; no signing key is retained.
        let metadata = #"{"jwks":{"keys":[{"kty":"EC","x":"KH2qvLdBYAFx35kdYO96zr-b4Uqoe6auN4DZmGqk7C0","y":"uQ_zP-nB1_g_xmChTlvUQ8QEGJOqtpW_wji7TRvi9vw","crv":"P-256"}]}}"#
        let requestObject = "eyJhbGciOiJFUzI1NiIsInR5cCI6Im9hdXRoLWF1dGh6LXJlcStqd3QifQ.eyJjbGllbnRfaWQiOiJ2ZXJpZmllciIsImF1ZCI6Imh0dHBzOi8vc2VsZi1pc3N1ZWQubWUvdjIiLCJyZXNwb25zZV90eXBlIjoidnBfdG9rZW4iLCJyZXNwb25zZV9tb2RlIjoiZnJhZ21lbnQifQ.nZswGJNggigJzIAPrbSaTvAypdwipVxB4eUSJURugeoeY2nUTY4an_u0tXKVDQZTebh1RqVZwUqJDcyjxUO7Eg"
        let wallet = try await Wallet(configuration: WalletConfiguration(
            walletID: "validation-error-\(UUID().uuidString)",
            persistence: .init(databaseKey: .provided(ValidationDatabaseKeyProvider())),
            keyAuthorization: .init(policy: .none),
            presentation: .init(
                verifierTrust: .init(preRegisteredClientMetadataJSON: ["verifier": metadata])
            )
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
private actor PausedDatabaseKeyProvider: WalletDatabaseKeyProvider {
    let entered: XCTestExpectation
    private var waiting: CheckedContinuation<Void, Never>?
    private var released = false

    init(entered: XCTestExpectation) { self.entered = entered }

    func databaseKey(walletID: String, databaseName: String) async throws -> WalletDatabaseKey {
        if !released {
            await withCheckedContinuation { continuation in
                waiting = continuation
                entered.fulfill()
            }
        }
        return WalletDatabaseKey(keyID: "cancelled-open", material: Data(repeating: 3, count: 32))
    }

    func release() {
        released = true
        waiting?.resume()
        waiting = nil
    }
    func deleteDatabaseKey(walletID: String, databaseName: String) async throws {}
}

private struct UnusedDatabaseKeyProvider: WalletDatabaseKeyProvider {
    func databaseKey(walletID: String, databaseName: String) async throws -> WalletDatabaseKey {
        XCTFail("Invalid configuration reached the database key provider")
        throw WalletError.storage("Unexpected database open")
    }
    func deleteDatabaseKey(walletID: String, databaseName: String) async throws { XCTFail("Unexpected deletion") }
}

private struct ValidationDatabaseKeyProvider: WalletDatabaseKeyProvider {
    func databaseKey(walletID: String, databaseName: String) async throws -> WalletDatabaseKey {
        WalletDatabaseKey(keyID: "validation-test", material: Data(repeating: 7, count: 32))
    }
    func deleteDatabaseKey(walletID: String, databaseName: String) async throws {}
}
#endif
