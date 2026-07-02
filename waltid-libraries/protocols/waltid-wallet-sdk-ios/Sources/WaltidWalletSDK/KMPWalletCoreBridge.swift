import Foundation

#if canImport(WaltidWalletCore) && os(iOS)
@preconcurrency import WaltidWalletCore

final class KMPWalletCoreBridge: WalletCoreBridge, @unchecked Sendable {
    private let bridge: WalletSdkBridge

    init(configuration: WalletConfiguration) throws {
        let result = WalletSdkBridgeFactory().create(
            configuration: configuration.toKMPConfiguration()
        )
        self.bridge = try Self.successValue(result, as: WalletSdkBridge.self, operation: "create wallet bridge")
    }

    var events: AsyncStream<WalletEvent> {
        AsyncStream { continuation in
            let task = Task { [self] in
                let flow = SkieSwiftFlow<WalletBridgeEvent>(
                    SkieKotlinFlow<WalletBridgeEvent>(bridge.events())
                )

                for await event in flow {
                    continuation.yield(event.toSwiftEvent())
                }

                continuation.finish()
            }

            continuation.onTermination = { _ in
                task.cancel()
            }
        }
    }

    func bootstrap(keyType: WalletKeyType, didMethod: String) async throws -> WalletBootstrapResult {
        let result = try await bridge.bootstrap(
            keyType: keyType.toKMPKeyType(),
            didMethod: didMethod
        )
        let value = try Self.successValue(
            result,
            as: WalletBridgeBootstrapResult.self,
            operation: "bootstrap wallet"
        )

        return .init(keyID: value.keyId, did: value.did)
    }

    func receive(offer: URL, txCode: String?, clientID: String) async throws -> [String] {
        let result = try await bridge.receive(
            offerUrl: offer.absoluteString,
            txCode: txCode,
            clientId: clientID
        )
        let value = try Self.successAnyValue(result, operation: "receive credentials")

        if let credentialIDs = value as? [String] {
            return credentialIDs
        }
        if let credentialIDs = value as? NSArray {
            return credentialIDs.compactMap { $0 as? String }
        }

        throw WalletError.internalFailure("Unexpected receive result type: \(type(of: value))")
    }

    func credentials() async throws -> [Credential] {
        let result = try await bridge.credentials()
        let value = try Self.successAnyValue(result, operation: "list credentials")

        if let credentials = value as? [WalletBridgeCredential] {
            return credentials.map { $0.toSwiftCredential() }
        }
        if let credentials = value as? NSArray {
            return credentials.compactMap { ($0 as? WalletBridgeCredential)?.toSwiftCredential() }
        }

        throw WalletError.internalFailure("Unexpected credentials result type: \(type(of: value))")
    }

    func present(request: URL, did: String?, runPolicies: Bool?) async throws -> PresentationResult {
        let result = try await bridge.present(
            requestUrl: request.absoluteString,
            did: did,
            runPolicies: runPolicies.map { KotlinBoolean(bool: $0) }
        )
        let value = try Self.successValue(
            result,
            as: WalletBridgePresentationResult.self,
            operation: "present credentials"
        )

        return .init(
            success: value.success,
            redirectTo: value.redirectTo.flatMap(URL.init(string:)),
            verifierResponseJSON: value.verifierResponseJson
        )
    }

    private static func successValue<T>(
        _ result: any WalletBridgeResult,
        as type: T.Type,
        operation: String
    ) throws -> T {
        let value = try successAnyValue(result, operation: operation)
        guard let typedValue = value as? T else {
            throw WalletError.internalFailure(
                "Unexpected \(operation) result type: \(Swift.type(of: value))"
            )
        }

        return typedValue
    }

    private static func successAnyValue(
        _ result: any WalletBridgeResult,
        operation: String
    ) throws -> Any {
        if let failure = result as? WalletBridgeResultFailure {
            throw failure.error.toSwiftWalletError()
        }
        guard let success = result as? WalletBridgeResultSuccess<AnyObject> else {
            throw WalletError.internalFailure("Unexpected \(operation) result wrapper: \(type(of: result))")
        }
        guard let value = success.value else {
            throw WalletError.internalFailure("Wallet bridge returned an empty \(operation) result.")
        }

        return value
    }
}

private extension WalletConfiguration {
    func toKMPConfiguration() -> WalletBridgeConfiguration {
        WalletBridgeConfiguration(
            walletId: walletID,
            defaultKeyType: defaultKeyType.toKMPKeyType(),
            attestation: attestation?.toKMPAttestationConfiguration()
        )
    }
}

private extension WalletAttestationConfiguration {
    func toKMPAttestationConfiguration() -> WalletBridgeAttestationConfiguration {
        WalletBridgeAttestationConfiguration(
            enterpriseBaseUrl: enterpriseBaseURL,
            attesterPath: attesterPath,
            bearerToken: bearerToken,
            enterpriseHostHeader: enterpriseHostHeader
        )
    }
}

private extension WalletKeyType {
    func toKMPKeyType() -> WalletBridgeKeyType {
        switch self {
        case .ed25519:
            return .ed25519
        case .secp256k1:
            return .secp256k1
        case .secp256r1:
            return .secp256r1
        case .secp384r1:
            return .secp384r1
        case .secp521r1:
            return .secp521r1
        case .rsa:
            return .rsa
        case .rsa3072:
            return .rsa3072
        case .rsa4096:
            return .rsa4096
        }
    }
}

private extension WalletBridgeCredential {
    func toSwiftCredential() -> Credential {
        Credential(
            id: id,
            format: format,
            issuer: issuer,
            subject: subject,
            label: label,
            addedAt: addedAt.flatMap { ISO8601DateFormatter().date(from: $0) }
        )
    }
}

private extension WalletBridgeEvent {
    func toSwiftEvent() -> WalletEvent {
        WalletEvent(
            name: name,
            phase: phase.toSwiftPhase(),
            status: status.toSwiftStatus()
        )
    }
}

private extension WalletBridgeEventPhase {
    func toSwiftPhase() -> WalletEventPhase {
        switch self {
        case .presentation:
            return .presentation
        case .issuance:
            return .issuance
        }
    }
}

private extension WalletBridgeEventStatus {
    func toSwiftStatus() -> WalletEventStatus {
        switch self {
        case .completed:
            return .completed
        case .failed:
            return .failed
        case .progress:
            return .progress
        }
    }
}

private extension WalletBridgeError {
    func toSwiftWalletError() -> WalletError {
        switch category {
        case .invalidInput:
            return .invalidInput(message)
        case .network:
            return .network(message)
        case .issuer:
            return .issuer(message)
        case .verifier:
            return .verifier(message)
        case .storage:
            return .storage(message)
        case .crypto:
            return .crypto(message)
        case .credentialNotFound:
            return .credentialNotFound(message)
        case .cancelled:
            return .cancelled
        case .internalFailure:
            return .internalFailure(message)
        }
    }
}
#endif
