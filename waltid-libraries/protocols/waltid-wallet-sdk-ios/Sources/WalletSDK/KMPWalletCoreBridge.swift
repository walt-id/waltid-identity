import Foundation

func parseWalletISO8601Date(_ value: String) -> Date? {
    let formatter = ISO8601DateFormatter()
    if let date = formatter.date(from: value) {
        return date
    }

    formatter.formatOptions.insert(.withFractionalSeconds)
    return formatter.date(from: value)
}

#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

final class KMPWalletCoreBridge: WalletCoreBridge, @unchecked Sendable {
    private let bridge: WalletSdkBridge

    init(configuration: WalletConfiguration) async throws {
        let result = try await WalletSdkBridgeFactory().create(
            configuration: configuration.toKMPConfiguration()
        )
        self.bridge = try Self.successValue(result, as: WalletSdkBridge.self, operation: "create wallet bridge")
    }

    var events: AsyncStream<WalletEvent> {
        AsyncStream { continuation in
            let task = Task { [self] in
                let flow = SkieSwiftFlow<MobileWalletEvent>(
                    SkieKotlinFlow(bridge.events)
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
            as: MobileWalletBootstrapResult.self,
            operation: "bootstrap wallet"
        )

        return .init(keyID: value.keyId, did: value.did)
    }

    func startIssuance(request: IssuanceRequest) async throws -> IssuanceSession {
        let result = try await bridge.startIssuance(
            request: MobileWalletIssuanceRequest(
                offerUrl: request.offer.absoluteString,
                clientId: request.clientID,
                redirectUri: request.redirectURI.absoluteString,
                keyId: request.keyID,
                did: request.did
            )
        )
        let value = try Self.successValue(
            result,
            as: Waltid_openid4vc_walletWalletIssuanceSession.self,
            operation: "start issuance"
        )
        return try value.toSwiftIssuanceSession()
    }

    func continuePreAuthorizedIssuance(
        sessionID: String,
        transactionCode: String?
    ) async throws -> IssuanceOutcome {
        let result = try await bridge.continuePreAuthorizedIssuance(
            sessionId: sessionID,
            transactionCode: transactionCode
        )
        return try Self.issuanceOutcome(result, operation: "continue pre-authorized issuance")
    }

    func continueAuthorizationIssuance(
        sessionID: String,
        callbackURI: URL
    ) async throws -> IssuanceOutcome {
        let result = try await bridge.continueAuthorizationIssuance(
            sessionId: sessionID,
            callbackUri: callbackURI.absoluteString
        )
        return try Self.issuanceOutcome(result, operation: "continue authorization issuance")
    }

    func cancelIssuance(sessionID: String) async throws -> IssuanceOutcome {
        let result = try await bridge.cancelIssuance(sessionId: sessionID)
        return try Self.issuanceOutcome(result, operation: "cancel issuance")
    }

    func resumeDeferredIssuance(deferredCredentialID: String) async throws -> IssuanceOutcome {
        let result = try await bridge.resumeDeferredIssuance(deferredCredentialId: deferredCredentialID)
        return try Self.issuanceOutcome(result, operation: "resume deferred issuance")
    }

    func credentials() async throws -> [Credential] {
        let result = try await bridge.credentials()
        let value = try Self.successAnyValue(result, operation: "list credentials")

        if let credentials = value as? [MobileWalletCredential] {
            return credentials.map { $0.toSwiftCredential() }
        }
        if let credentials = value as? NSArray {
            return credentials.compactMap { ($0 as? MobileWalletCredential)?.toSwiftCredential() }
        }

        throw WalletError.internalFailure("Unexpected credentials result type: \(type(of: value))")
    }

    func deleteLocalData() async throws {
        let result = try await bridge.deleteWallet()
        _ = try Self.successAnyValue(result, operation: "delete local wallet data")
    }

    func present(request: URL, did: String?, runPolicies: Bool?) async throws -> PresentationResult {
        let result = try await bridge.present(
            requestUrl: request.absoluteString,
            did: did,
            runPolicies: runPolicies.map { KotlinBoolean(bool: $0) }
        )
        let value = try Self.successValue(
            result,
            as: MobileWalletPresentationResult.self,
            operation: "present credentials"
        )

        return try value.toSwiftPresentationResult()
    }

    func previewPresentation(request: URL) async throws -> PresentationPreviewResult {
        let result = try await bridge.previewPresentation(requestUrl: request.absoluteString)
        let value = try Self.successValue(
            result,
            as: MobileWalletPresentationPreviewResult.self,
            operation: "preview presentation"
        )

        return try value.toSwiftPreviewResult()
    }

    func submitPresentation(
        previewHandle: PresentationPreviewHandle,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection]?,
        did: String?,
        runPolicies: Bool?
    ) async throws -> PresentationResult {
        let result = try await bridge.submitPresentation(
            previewHandle: MobileWalletPresentationPreviewHandle(value: previewHandle.value),
            selectedCredentialOptions: selectedCredentialOptions.map {
                MobileWalletPresentationCredentialSelection(
                    queryId: $0.queryID,
                    credentialId: $0.credentialID
                )
            },
            selectedDisclosureOptions: selectedDisclosureOptions?.map {
                MobileWalletPresentationDisclosureSelection(
                    queryId: $0.queryID,
                    credentialId: $0.credentialID,
                    path: $0.path
                )
            },
            did: did,
            runPolicies: runPolicies.map { KotlinBoolean(bool: $0) }
        )
        let value = try Self.successValue(
            result,
            as: MobileWalletPresentationResult.self,
            operation: "submit presentation"
        )

        return try value.toSwiftPresentationResult()
    }

    func rejectPresentation(
        previewHandle: PresentationPreviewHandle,
        error: PresentationErrorCode?,
        errorDescription: String?
    ) async throws -> PresentationResult {
        let result = try await bridge.rejectPresentation(
            previewHandle: MobileWalletPresentationPreviewHandle(value: previewHandle.value),
            errorCode: error?.toKMPErrorCode(),
            errorDescription: errorDescription
        )
        let value = try Self.successValue(
            result,
            as: MobileWalletPresentationResult.self,
            operation: "reject presentation"
        )

        return try value.toSwiftPresentationResult()
    }

    func discardPresentationPreview(_ previewHandle: PresentationPreviewHandle) async throws {
        let result = try await bridge.discardPresentationPreview(
            previewHandle: MobileWalletPresentationPreviewHandle(value: previewHandle.value)
        )
        _ = try Self.successAnyValue(result, operation: "discard presentation preview")
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

    private static func issuanceOutcome(
        _ result: any WalletBridgeResult,
        operation: String
    ) throws -> IssuanceOutcome {
        let value = try successAnyValue(result, operation: operation)
        guard let outcome = value as? any Waltid_openid4vc_walletWalletIssuanceOutcome else {
            throw WalletError.internalFailure("Unexpected \(operation) result type: \(type(of: value))")
        }
        return try outcome.toSwiftIssuanceOutcome()
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

private extension Waltid_openid4vc_walletWalletIssuanceSession {
    func toSwiftIssuanceSession() throws -> IssuanceSession {
        IssuanceSession(
            id: id,
            offer: try offer.toSwiftIssuanceOfferPreview(),
            authorization: try authorization?.toSwiftIssuanceAuthorization()
        )
    }
}

private extension Waltid_openid4vc_walletWalletIssuanceOfferPreview {
    func toSwiftIssuanceOfferPreview() throws -> IssuanceOfferPreview {
        IssuanceOfferPreview(
            grant: grant == .authorizationCode ? .authorizationCode : .preAuthorizedCode,
            issuer: issuer.toSwiftIssuanceIssuerPreview(),
            credentials: swiftArray(credentials, of: Waltid_openid4vc_walletWalletIssuanceCredentialPreview.self)
                .map { $0.toSwiftIssuanceCredentialPreview() },
            transactionCode: transactionCode?.toSwiftIssuanceTransactionCode()
        )
    }
}

private extension Waltid_openid4vc_walletWalletIssuanceIssuerPreview {
    func toSwiftIssuanceIssuerPreview() -> IssuanceIssuerPreview {
        IssuanceIssuerPreview(
            identifier: identifier,
            name: name,
            locale: locale,
            logoURI: logoUri.flatMap(URL.init(string:)),
            logoAltText: logoAltText
        )
    }
}

private extension Waltid_openid4vc_walletWalletIssuanceCredentialPreview {
    func toSwiftIssuanceCredentialPreview() -> IssuanceCredentialPreview {
        IssuanceCredentialPreview(
            configurationID: configurationId,
            format: format,
            name: name,
            descriptionText: descriptionText,
            logoURI: logoUri.flatMap(URL.init(string:))
        )
    }
}

private extension Waltid_openid4vc_walletWalletIssuanceTransactionCode {
    func toSwiftIssuanceTransactionCode() -> IssuanceTransactionCode {
        IssuanceTransactionCode(
            inputMode: inputMode,
            length: length.map { Int($0.int32Value) },
            descriptionText: descriptionText
        )
    }
}

private extension Waltid_openid4vc_walletWalletIssuanceAuthorization {
    func toSwiftIssuanceAuthorization() throws -> IssuanceAuthorization {
        guard let authorizationURL = URL(string: url), let callbackURL = URL(string: redirectUri) else {
            throw WalletError.internalFailure("Wallet core returned an invalid issuance URL.")
        }
        return IssuanceAuthorization(
            url: authorizationURL,
            state: state,
            redirectURI: callbackURL,
            pkce: IssuancePKCEState(
                codeChallenge: pkce.codeChallenge,
                codeChallengeMethod: pkce.codeChallengeMethod
            ),
            pushedAuthorizationRequestUsed: pushedAuthorizationRequestUsed
        )
    }
}

private extension Waltid_openid4vc_walletWalletIssuanceOutcome {
    func toSwiftIssuanceOutcome() throws -> IssuanceOutcome {
        switch onEnum(of: self) {
        case let .stored(value):
            return .stored(
                sessionID: value.sessionId,
                credentialIDs: swiftArray(value.credentialIds, of: String.self)
            )
        case let .deferred(value):
            return .deferred(
                sessionID: value.sessionId,
                storedCredentialIDs: swiftArray(value.storedCredentialIds, of: String.self),
                credentials: swiftArray(
                    value.credentials,
                    of: Waltid_openid4vc_walletWalletDeferredCredential.self
                ).map { credential in
                    DeferredCredential(
                        id: credential.id,
                        credentialConfigurationID: credential.credentialConfigurationId,
                        intervalSeconds: credential.intervalSeconds?.int64Value
                    )
                }
            )
        case let .cancelled(value):
            return .cancelled(sessionID: value.sessionId)
        case let .failed(value):
            return .failed(
                sessionID: value.sessionId,
                error: IssuanceFailure(
                    code: value.error.code.toSwiftIssuanceErrorCode(),
                    message: value.error.message
                ),
                storedCredentialIDs: swiftArray(value.storedCredentialIds, of: String.self)
            )
        }
    }
}

private extension Waltid_openid4vc_walletWalletIssuanceErrorCode {
    func toSwiftIssuanceErrorCode() -> IssuanceErrorCode {
        switch self {
        case .invalidSession: return .invalidSession
        case .invalidCallback: return .invalidCallback
        case .invalidInput: return .invalidInput
        case .authorizationFailed: return .authorizationFailed
        case .issuerMetadata: return .issuerMetadata
        case .issuerResponse: return .issuerResponse
        case .network: return .network
        case .crypto: return .crypto
        case .storage: return .storage
        case .protocol: return .protocol
        }
    }
}

private extension WalletConfiguration {
    func toKMPConfiguration() -> WalletBridgeConfiguration {
        WalletBridgeConfiguration(
            walletId: walletID,
            defaultKeyType: defaultKeyType.toKMPKeyType(),
            persistence: persistence.toKMPPersistence(),
            databaseKeyProvider: persistence.toKMPDatabaseKeyProvider(),
            attestation: attestation?.toKMPAttestationConfiguration(),
            preferredLocales: preferredLocales,
            transactionDataProfiles: transactionDataProfiles.map { $0.toKMPTransactionDataProfile() }
        )
    }
}

private extension WalletTransactionDataProfile {
    func toKMPTransactionDataProfile() -> MobileWalletTransactionDataProfile {
        MobileWalletTransactionDataProfile(
            type: type,
            displayName: displayName,
            fields: fields
        )
    }
}

private extension WalletPersistence {
    func toKMPPersistence() -> WalletBridgePersistence {
        WalletBridgePersistence(
            databaseKey: databaseKey.toKMPDatabaseKeyConfiguration(),
            stores: stores.toKMPStores()
        )
    }

    func toKMPDatabaseKeyProvider() -> WalletBridgeDatabaseEncryptionKeyProvider? {
        databaseKey.toKMPDatabaseKeyProvider()
    }
}

private extension WalletDatabaseKeyConfiguration {
    func toKMPDatabaseKeyConfiguration() -> WalletBridgeDatabaseKeyConfiguration {
        switch self {
        case .managed:
            return .managed
        case .provided:
            return .provided
        }
    }

    func toKMPDatabaseKeyProvider() -> WalletBridgeDatabaseEncryptionKeyProvider? {
        switch self {
        case .managed:
            return nil
        case let .provided(provider):
            return KMPWalletDatabaseKeyProviderAdapter(provider: provider)
        }
    }
}

private extension WalletStores {
    func toKMPStores() -> WalletBridgeStores {
        WalletBridgeStores(
            credentials: credentials.map { KMPWalletCredentialStoreAdapter(store: $0) },
            dids: dids.map { KMPWalletDidStoreAdapter(store: $0) },
            keys: keys.map { keyOverride in
                WalletBridgeKeys(
                    store: KMPWalletKeyStoreAdapter(store: keyOverride.store),
                    generate: KMPWalletKeyGeneratorAdapter(generate: keyOverride.generate)
                )
            }
        )
    }
}

private final class KMPWalletDatabaseKeyProviderAdapter: WalletBridgeDatabaseEncryptionKeyProvider, @unchecked Sendable {
    private let provider: any WalletDatabaseKeyProvider

    init(provider: any WalletDatabaseKeyProvider) {
        self.provider = provider
    }

    func __getOrCreateKey(walletId: String, databaseName: String) async throws -> WalletBridgeDatabaseEncryptionKey {
        let key = try await provider.databaseKey(walletID: walletId, databaseName: databaseName)
        return WalletBridgeDatabaseEncryptionKey(
            keyId: key.keyID,
            material: key.material.toKotlinByteArray()
        )
    }

    func __deleteKey(walletId: String, databaseName: String) async throws {
        try await provider.deleteDatabaseKey(walletID: walletId, databaseName: databaseName)
    }
}

private final class KMPWalletCredentialStoreAdapter: WalletBridgeCredentialStore, @unchecked Sendable {
    private let store: any WalletCredentialStore

    init(store: any WalletCredentialStore) {
        self.store = store
    }

    func __getCredential(id: String) async throws -> WalletBridgeStoredCredential? {
        try await store.credential(id: id)?.toKMPStoredCredential()
    }

    func __listCredentials() async throws -> [WalletBridgeStoredCredential] {
        try await store.credentials().map { $0.toKMPStoredCredential() }
    }

    func __addCredential(entry: WalletBridgeStoredCredential) async throws {
        try await store.addCredential(entry.toSwiftStoredCredential())
    }

    func __removeCredential(id: String) async throws -> KotlinBoolean {
        KotlinBoolean(bool: try await store.removeCredential(id: id))
    }
}

private final class KMPWalletDidStoreAdapter: WalletBridgeDidStore, @unchecked Sendable {
    private let store: any WalletDidStore

    init(store: any WalletDidStore) {
        self.store = store
    }

    func __getDid(did: String) async throws -> WalletBridgeStoredDid? {
        try await store.did(id: did)?.toKMPStoredDid()
    }

    func __listDids() async throws -> [WalletBridgeStoredDid] {
        try await store.dids().map { $0.toKMPStoredDid() }
    }

    func __addDid(entry: WalletBridgeStoredDid) async throws {
        try await store.addDid(entry.toSwiftStoredDid())
    }

    func __removeDid(did: String) async throws -> KotlinBoolean {
        KotlinBoolean(bool: try await store.removeDid(id: did))
    }
}

private final class KMPWalletKeyStoreAdapter: WalletBridgeKeyStore, @unchecked Sendable {
    private let store: any WalletKeyStore

    init(store: any WalletKeyStore) {
        self.store = store
    }

    func __getKey(keyId: String) async throws -> WalletBridgeStoredKey? {
        try await store.key(id: keyId)?.toKMPStoredKey()
    }

    func __listKeys() async throws -> [WalletBridgeKeyInfo] {
        try await store.keys().map { $0.toKMPKeyInfo() }
    }

    func __addKey(entry: WalletBridgeStoredKey) async throws -> String {
        try await store.addKey(entry.toSwiftStoredKey())
    }

    func __removeKey(keyId: String) async throws -> KotlinBoolean {
        KotlinBoolean(bool: try await store.removeKey(id: keyId))
    }
}

private final class KMPWalletKeyGeneratorAdapter: WalletBridgeKeyGenerator, @unchecked Sendable {
    private let generate: @Sendable (WalletKeyType) async throws -> StoredKey

    init(generate: @escaping @Sendable (WalletKeyType) async throws -> StoredKey) {
        self.generate = generate
    }

    func __generateKey(keyType: MobileWalletKeyType) async throws -> WalletBridgeStoredKey {
        try await generate(keyType.toSwiftKeyType()).toKMPStoredKey()
    }
}

private extension StoredCredential {
    func toKMPStoredCredential() -> WalletBridgeStoredCredential {
        WalletBridgeStoredCredential(
            id: id,
            serializedCredential: serializedCredential,
            format: format,
            label: label,
            addedAt: addedAt.map { ISO8601DateFormatter().string(from: $0) }
        )
    }
}

private extension WalletBridgeStoredCredential {
    func toSwiftStoredCredential() -> StoredCredential {
        StoredCredential(
            id: id,
            serializedCredential: serializedCredential,
            format: format,
            label: label,
            addedAt: addedAt.flatMap(parseWalletISO8601Date)
        )
    }
}

private extension StoredDid {
    func toKMPStoredDid() -> WalletBridgeStoredDid {
        WalletBridgeStoredDid(
            did: did,
            documentJson: documentJSON
        )
    }
}

private extension WalletBridgeStoredDid {
    func toSwiftStoredDid() -> StoredDid {
        StoredDid(
            did: did,
            documentJSON: documentJson
        )
    }
}

private extension WalletKeyInfo {
    func toKMPKeyInfo() -> WalletBridgeKeyInfo {
        WalletBridgeKeyInfo(
            keyId: keyID,
            keyType: keyType.bridgeName,
            algorithm: algorithm
        )
    }
}

private extension StoredKey {
    func toKMPStoredKey() -> WalletBridgeStoredKey {
        WalletBridgeStoredKey(
            keyId: keyID,
            keyType: keyType.bridgeName,
            algorithm: algorithm,
            serializedKeyJson: serializedKeyJSON
        )
    }
}

private extension WalletBridgeStoredKey {
    func toSwiftStoredKey() throws -> StoredKey {
        StoredKey(
            keyID: keyId,
            keyType: try WalletKeyType(bridgeName: keyType),
            algorithm: algorithm,
            serializedKeyJSON: serializedKeyJson
        )
    }
}

private extension Data {
    func toKotlinByteArray() -> KotlinByteArray {
        let bytes = [UInt8](self)
        let array = KotlinByteArray(size: Int32(bytes.count))

        for (index, byte) in bytes.enumerated() {
            array.set(index: Int32(index), value: Int8(bitPattern: byte))
        }

        return array
    }
}

private extension WalletAttestationConfiguration {
    func toKMPAttestationConfiguration() -> WalletAttestationConfig {
        WalletAttestationConfig(
            baseUrl: baseURL,
            attesterPath: attesterPath,
            bearerToken: bearerToken,
            hostHeader: hostHeader
        )
    }
}

private extension WalletKeyType {
    init(bridgeName: String) throws {
        switch bridgeName {
        case "Ed25519":
            self = .ed25519
        case "secp256k1":
            self = .secp256k1
        case "secp256r1":
            self = .secp256r1
        case "secp384r1":
            self = .secp384r1
        case "secp521r1":
            self = .secp521r1
        case "RSA":
            self = .rsa
        case "RSA3072":
            self = .rsa3072
        case "RSA4096":
            self = .rsa4096
        default:
            throw WalletError.invalidInput("Unsupported wallet key type: \(bridgeName)")
        }
    }

    var bridgeName: String {
        switch self {
        case .ed25519:
            return "Ed25519"
        case .secp256k1:
            return "secp256k1"
        case .secp256r1:
            return "secp256r1"
        case .secp384r1:
            return "secp384r1"
        case .secp521r1:
            return "secp521r1"
        case .rsa:
            return "RSA"
        case .rsa3072:
            return "RSA3072"
        case .rsa4096:
            return "RSA4096"
        }
    }

    func toKMPKeyType() -> MobileWalletKeyType {
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

private extension MobileWalletKeyType {
    func toSwiftKeyType() -> WalletKeyType {
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

private extension MobileWalletCredential {
    func toSwiftCredential() -> Credential {
        Credential(
            id: id,
            format: format,
            issuer: issuer,
            subject: subject,
            label: label,
            addedAt: addedAt.flatMap(parseWalletISO8601Date),
            credentialDataJSON: requiredCredentialDataJSON(credentialDataJson)
        )
    }
}

private extension MobileWalletPresentationPreviewResult {
    func toSwiftPreviewResult() throws -> PresentationPreviewResult {
        switch self {
        case let result as MobileWalletPresentationPreviewResultReady:
            return .ready(result.preview.toSwiftPreview())
        case let result as MobileWalletPresentationPreviewResultInvalid:
            return .invalid(
                PresentationPreviewError(
                    previewHandle: PresentationPreviewHandle(value: result.previewHandle.value),
                    request: result.request.toSwiftRequestInfo(),
                    code: result.errorCode.toSwiftErrorCode(),
                    message: result.message
                )
            )
        default:
            throw WalletError.internalFailure("Unsupported presentation preview result: \(type(of: self))")
        }
    }
}

private extension MobileWalletPresentationPreview {
    func toSwiftPreview() -> PresentationPreview {
        PresentationPreview(
            previewHandle: PresentationPreviewHandle(value: previewHandle.value),
            request: request.toSwiftRequestInfo(),
            credentialOptions: swiftArray(credentialOptions, of: MobileWalletPresentationCredentialOption.self)
                .map { $0.toSwiftCredentialOption() },
            credentialRequirements: swiftArray(credentialRequirements, of: MobileWalletPresentationCredentialRequirement.self)
                .map { $0.toSwiftCredentialRequirement() }
        )
    }
}

private extension MobileWalletPresentationRequestInfo {
    func toSwiftRequestInfo() -> PresentationRequestInfo {
        PresentationRequestInfo(
            clientID: clientId,
            verifierMetadata: verifierMetadata?.toSwiftVerifierMetadata(),
            responseURI: responseUri.flatMap(URL.init(string:)),
            state: state,
            nonce: nonce,
            responseEncryption: responseEncryption.toSwiftResponseEncryption(),
            transactionData: swiftArray(transactionData, of: MobileWalletTransactionDataItem.self)
                .map { $0.toSwiftTransactionData() }
        )
    }
}

private extension MobileWalletResponseEncryption {
    func toSwiftResponseEncryption() -> PresentationResponseEncryption {
        guard isRequired else { return .notRequired }
        guard let keyManagementAlgorithm,
              let contentEncryptionAlgorithm,
              let verifierKeyThumbprint else {
            preconditionFailure("Required response encryption is missing selected metadata")
        }
        return .required(
            ResponseEncryptionDetails(
                keyManagementAlgorithm: keyManagementAlgorithm,
                contentEncryptionAlgorithm: contentEncryptionAlgorithm,
                verifierKeyID: verifierKeyId,
                verifierKeyThumbprint: verifierKeyThumbprint
            )
        )
    }
}

private extension MobileWalletMetadataDisplay {
    func toSwiftMetadataDisplay() -> MetadataDisplay {
        MetadataDisplay(
            name: name,
            locale: locale,
            logoURI: logoUri,
            logoAltText: logoAltText,
            description: descriptionText,
            backgroundColor: backgroundColor,
            backgroundImageURI: backgroundImageUri,
            textColor: textColor
        )
    }
}

private extension MobileWalletVerifierMetadata {
    func toSwiftVerifierMetadata() -> VerifierMetadata {
        VerifierMetadata(
            display: display?.toSwiftMetadataDisplay(),
            clientURI: clientUri,
            policyURI: policyUri,
            termsOfServiceURI: termsOfServiceUri
        )
    }
}

private extension MobileWalletPresentationCredentialOption {
    func toSwiftCredentialOption() -> PresentationCredentialOption {
        PresentationCredentialOption(
            queryID: queryId,
            credentialID: credentialId,
            multiple: multiple,
            format: format,
            issuer: issuer,
            subject: subject,
            label: label,
            credentialDataJSON: requiredCredentialDataJSON(credentialDataJson),
            disclosures: swiftArray(disclosures, of: MobileWalletPresentationDisclosure.self)
                .map { $0.toSwiftDisclosure() }
        )
    }
}

private extension MobileWalletPresentationCredentialRequirement {
    func toSwiftCredentialRequirement() -> PresentationCredentialRequirement {
        PresentationCredentialRequirement(options: swiftStringMatrix(options))
    }
}

private func requiredCredentialDataJSON(_ value: String?) -> String {
    guard let value else {
        assertionFailure("KMP wallet returned nil credential data JSON for a non-null SDK field")
        return "{}"
    }
    return value
}

private func swiftStringMatrix(_ value: Any) -> [[String]] {
    swiftArray(value, of: Any.self).map { option in
        swiftArray(option, of: String.self)
    }
}

private extension MobileWalletPresentationDisclosure {
    func toSwiftDisclosure() -> PresentationDisclosure {
        PresentationDisclosure(
            path: path,
            name: name,
            valueJSON: valueJson,
            displayValue: displayValue,
            selectivelyDisclosable: selectivelyDisclosable,
            required: required,
            selectable: selectable
        )
    }
}

private extension MobileWalletTransactionDataItem {
    func toSwiftTransactionData() -> PresentationTransactionData {
        PresentationTransactionData(
            type: type,
            displayName: displayName,
            credentialQueryIDs: swiftArray(credentialQueryIds, of: String.self),
            supportedFields: swiftArray(supportedFields, of: String.self),
            rawJSON: rawJson,
            detailsJSON: detailsJson
        )
    }
}

private extension PresentationErrorCode {
    func toKMPErrorCode() -> MobileWalletPresentationErrorCode {
        switch self {
        case .accessDenied:
            return .accessDenied
        case .invalidRequest:
            return .invalidRequest
        case .invalidClient:
            return .invalidClient
        case .invalidScope:
            return .invalidScope
        case .unauthorizedClient:
            return .unauthorizedClient
        case .unsupportedResponseType:
            return .unsupportedResponseType
        case .serverError:
            return .serverError
        case .temporarilyUnavailable:
            return .temporarilyUnavailable
        case .vpFormatsNotSupported:
            return .vpFormatsNotSupported
        case .invalidRequestURIMethod:
            return .invalidRequestUriMethod
        case .invalidTransactionData:
            return .invalidTransactionData
        case .walletUnavailable:
            return .walletUnavailable
        }
    }
}

private extension MobileWalletPresentationErrorCode {
    func toSwiftErrorCode() -> PresentationErrorCode {
        switch self {
        case .accessDenied:
            return .accessDenied
        case .invalidRequest:
            return .invalidRequest
        case .invalidClient:
            return .invalidClient
        case .invalidScope:
            return .invalidScope
        case .unauthorizedClient:
            return .unauthorizedClient
        case .unsupportedResponseType:
            return .unsupportedResponseType
        case .serverError:
            return .serverError
        case .temporarilyUnavailable:
            return .temporarilyUnavailable
        case .vpFormatsNotSupported:
            return .vpFormatsNotSupported
        case .invalidRequestUriMethod:
            return .invalidRequestURIMethod
        case .invalidTransactionData:
            return .invalidTransactionData
        case .walletUnavailable:
            return .walletUnavailable
        }
    }
}

private extension MobileWalletPresentationResult {
    func toSwiftPresentationResult() throws -> PresentationResult {
        switch self {
        case let result as MobileWalletPresentationResultTransmittedSucceeded:
            let redirectURL: URL?
            if let value = result.redirectUrl {
                guard let url = URL(string: value) else {
                    throw WalletError.invalidInput("Invalid presentation redirect URL: \(value)")
                }
                redirectURL = url
            } else {
                redirectURL = nil
            }
            return .transmitted(
                .succeeded(
                    verifierResponseJSON: result.verifierResponseJson,
                    redirectURL: redirectURL
                )
            )
        case let result as MobileWalletPresentationResultPreparedOpenUrl:
            guard let url = URL(string: result.url) else {
                throw WalletError.invalidInput("Invalid presentation continuation URL: \(result.url)")
            }
            return .prepared(.openURL(url))
        case let result as MobileWalletPresentationResultPreparedSubmitForm:
            return .prepared(.submitForm(html: result.html))
        case let result as MobileWalletPresentationResultTransmittedFailed:
            return .transmitted(.failed(verifierResponseJSON: result.verifierResponseJson))
        default:
            throw WalletError.internalFailure("Unknown presentation result type: \(type(of: self))")
        }
    }
}

private func swiftArray<T>(_ value: Any, of type: T.Type) -> [T] {
    if let values = value as? [T] {
        return values
    }
    if let values = value as? NSArray {
        return values.compactMap { $0 as? T }
    }
    return []
}

private extension MobileWalletEvent {
    func toSwiftEvent() -> WalletEvent {
        WalletEvent(
            name: name,
            phase: phase.toSwiftPhase(),
            status: status.toSwiftStatus()
        )
    }
}

private extension MobileWalletEventPhase {
    func toSwiftPhase() -> WalletEventPhase {
        switch self {
        case .presentation:
            return .presentation
        case .issuance:
            return .issuance
        }
    }
}

private extension MobileWalletEventStatus {
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
