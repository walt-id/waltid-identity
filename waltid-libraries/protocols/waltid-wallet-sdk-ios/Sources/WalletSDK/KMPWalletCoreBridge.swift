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

    func bootstrap(
        keyType: WalletKeyType,
        didMethod: String,
        keyUseAuthorizationPolicy: WalletKeyUseAuthorizationPolicy?
    ) async throws -> WalletBootstrapResult {
        let result = try await bridge.bootstrap(
            keyType: keyType.toKMPKeyType(),
            didMethod: didMethod,
            keyUseAuthorizationPolicy: keyUseAuthorizationPolicy?.toKMPAuthorizationPolicy()
        )
        let value = try Self.successValue(
            result,
            as: MobileWalletBootstrapResult.self,
            operation: "bootstrap wallet"
        )

        return .init(
            keyID: value.keyId,
            did: value.did,
            publicJWK: value.publicJwk,
            keyUseAuthorizationPolicy: toSwiftAuthorizationPolicy(value.keyUseAuthorizationPolicy)
        )
    }

    func keyUseAuthorizationPreflight(
        keyType: WalletKeyType,
        policy: WalletKeyUseAuthorizationPolicy
    ) async throws -> WalletKeyUseAuthorizationPreflight {
        let result = try await bridge.keyUseAuthorizationPreflight(
            keyType: keyType.toKMPKeyType(),
            policy: policy.toKMPAuthorizationPolicy()
        )
        let value = try Self.successValue(
            result,
            as: WalletBridgeKeyPreflight.self,
            operation: "key authorization preflight"
        )
        switch (value.supported, value.effectivePolicy, value.reuseEnforcement, value.timeoutValidation, value.failure) {
        case (true, let policy?, let reuseEnforcement?, let timeoutValidation?, nil):
            guard policy.type == .biometricTimedReuse else {
                throw WalletError.internalFailure("Invalid timed key authorization preflight result")
            }
            return .supported(
                effectivePolicy: policy.toSwiftAuthorizationPolicy(),
                reuseEnforcement: reuseEnforcement.toSwiftAuthorizationReuseEnforcement(),
                timeoutValidation: timeoutValidation.toSwiftAuthorizationTimeoutValidation()
            )
        case (true, let policy?, nil, nil, nil):
            guard policy.type != .biometricTimedReuse else {
                throw WalletError.internalFailure("Timed key authorization preflight lacks timeout metadata")
            }
            return .supported(
                effectivePolicy: policy.toSwiftAuthorizationPolicy(),
                reuseEnforcement: nil,
                timeoutValidation: nil
            )
        case (false, nil, nil, nil, let failure?): return .unsupported(failure.toSwiftAuthorizationUnsupportedReason())
        default: throw WalletError.internalFailure("Invalid key authorization preflight result")
        }
    }

    func startIssuance(request: IssuanceRequest) async throws -> IssuanceSession {
        let result = try await bridge.startIssuance(
            request: MobileWalletIssuanceRequest(
                offer: MobileWalletCredentialOfferUri(value: request.offer.absoluteString),
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

    func beginAuthorizationIssuance(sessionID: String) async throws -> IssuanceAuthorization {
        let result = try await bridge.beginAuthorizationIssuance(sessionId: sessionID)
        let value = try Self.successValue(
            result,
            as: Waltid_openid4vc_walletWalletIssuanceAuthorization.self,
            operation: "begin authorization issuance"
        )
        return try value.toSwiftIssuanceAuthorization()
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

    func deleteCredential(id: String) async throws -> Bool {
        let result = try await bridge.deleteCredential(credentialId: id)
        let value = try Self.successAnyValue(result, operation: "delete credential")
        if let flag = value as? KotlinBoolean {
            return flag.boolValue
        }
        if let flag = value as? Bool {
            return flag
        }
        throw WalletError.internalFailure("Unexpected delete credential result type: \(type(of: value))")
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

    func proximityPresentationCapabilities(
        configuration: ProximityPresentationConfiguration
    ) async throws -> ProximityPresentationCapabilities {
        let result = try await bridge.proximityPresentationCapabilities(
            configuration: configuration.toKMPConfiguration()
        )
        return try Self.successValue(
            result,
            as: MobileWalletProximityCapabilities.self,
            operation: "check proximity presentation capabilities"
        ).toSwiftCapabilities()
    }

    func startProximityPresentation(
        configuration: ProximityPresentationConfiguration
    ) async throws -> any ProximityPresentationSessionBridge {
        let result = try await bridge.startProximityPresentation(
            configuration: configuration.toKMPConfiguration()
        )
        let session = try Self.successValue(
            result,
            as: MobileWalletProximitySession.self,
            operation: "start proximity presentation"
        )
        return KMPProximityPresentationSessionBridge(session: session)
    }

    func digitalCredentialCapabilities() -> DigitalCredentialCapabilities {
        bridge.digitalCredentialCapabilities().toSwiftCapabilities()
    }

    func previewAnnexCPresentation(
        parsedRequest: AnnexCParsedRequest,
        verifiedOrigin: String,
        selectedRegistryEntryIDs: [String]
    ) async throws -> AnnexCPresentationPreview {
        let result = try await bridge.previewAnnexCPresentation(
            request: MobileWalletAnnexCRequest(
                parsedRequest: parsedRequest.toKMPParsedRequest(),
                verifiedOrigin: verifiedOrigin,
                selectedRegistryEntryIds: selectedRegistryEntryIDs,
                deviceRequestBase64Url: nil,
                encryptionInfoBase64Url: nil
            )
        )
        return try Self.successValue(
            result,
            as: MobileWalletAnnexCPreview.self,
            operation: "preview Annex C presentation"
        ).toSwiftPreview()
    }

    func submitAnnexCPresentation(
        requestID: String,
        verifiedOrigin: String,
        deviceRequestBase64URL: String,
        encryptionInfoBase64URL: String,
        selectedCredentialOptions: [PresentationCredentialSelection]
    ) async throws -> DigitalCredentialResponse {
        let result = try await bridge.submitAnnexCPresentation(
            submission: MobileWalletAnnexCSubmission(
                requestId: requestID,
                verifiedOrigin: verifiedOrigin,
                deviceRequestBase64Url: deviceRequestBase64URL,
                encryptionInfoBase64Url: encryptionInfoBase64URL,
                selectedCredentialOptions: selectedCredentialOptions.map {
                    MobileWalletPresentationCredentialSelection(
                        queryId: $0.queryID,
                        credentialId: $0.credentialID
                    )
                }
            )
        )
        let value = try Self.successValue(
            result,
            as: MobileWalletDigitalCredentialResponse.self,
            operation: "submit Annex C presentation"
        )
        return DigitalCredentialResponse(protocolIdentifier: value.protocol, dataJSON: value.dataJson)
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

private extension ProximityPresentationConfiguration {
    func toKMPConfiguration() -> MobileWalletProximityConfiguration {
        MobileWalletProximityConfiguration(
            profile: profile.toKMPProfile(),
            bleRoles: bleRoles.toKMPRoles(),
            bearerPolicy: bearerPolicy.toKMPPolicy(),
            engagementMethods: Set(engagementMethods.map { $0.toKMPMethod() }),
            retrievalMethods: Set(retrievalMethods.map { $0.toKMPMethod() }),
            readerPolicy: readerPolicy.toKMPPolicy(),
            deviceAuthenticationPolicy: deviceAuthenticationPolicy.toKMPPolicy(),
            readerTrustEvaluator: readerTrustEvaluator.map {
                KMPProximityReaderTrustEvaluatorAdapter(evaluator: $0)
            } ?? UnconfiguredMobileWalletProximityReaderTrustEvaluator.shared,
            credentialStatusEvaluator: credentialStatusEvaluator.map {
                KMPProximityCredentialStatusEvaluatorAdapter(evaluator: $0)
            } ?? UnconfiguredMobileWalletProximityCredentialStatusEvaluator.shared,
            applicationProfiles: MobileWalletProximityApplicationProfileRegistry(
                profiles: applicationProfiles.map(KMPProximityApplicationProfileAdapter.init)
            ),
            maximumMessageBytes: Int32(maximumMessageBytes)
        )
    }
}

private final class KMPProximityReaderTrustEvaluatorAdapter:
    MobileWalletProximityReaderTrustEvaluator,
    @unchecked Sendable {
    private let evaluator: any ProximityReaderTrustEvaluator

    init(evaluator: any ProximityReaderTrustEvaluator) {
        self.evaluator = evaluator
    }

    func __evaluate(
        evidence: MobileWalletProximityReaderEvidence
    ) async throws -> MobileWalletProximityReaderTrustDecision {
        let certificateChain = try swiftArray(evidence.certificateChainDerBase64Url, of: String.self).map {
            guard let data = Data(base64URLEncoded: $0) else {
                throw WalletError.internalFailure("Wallet core returned invalid reader certificate evidence")
            }
            return data
        }
        let decision = try await evaluator.evaluate(
            ProximityReaderEvidence(
                scope: evidence.scope.toSwiftScope(),
                documentRequestIndex: evidence.documentRequestIndex.map { Int($0.int32Value) },
                authenticationIndex: Int(evidence.authenticationIndex),
                certificateChainDER: certificateChain
            )
        )
        return MobileWalletProximityReaderTrustDecision(
            state: decision.state.toKMPState(),
            certificatePath: decision.certificatePath.toKMPState(),
            revocation: decision.revocation.toKMPState(),
            rical: decision.rical.toKMPState(),
            displayName: decision.displayName,
            reason: decision.reason
        )
    }
}

private final class KMPProximityCredentialStatusEvaluatorAdapter:
    MobileWalletProximityCredentialStatusEvaluator,
    @unchecked Sendable {
    private let evaluator: any ProximityCredentialStatusEvaluator

    init(evaluator: any ProximityCredentialStatusEvaluator) {
        self.evaluator = evaluator
    }

    func __evaluate(
        credential: MobileWalletProximityCredentialStatusInput
    ) async throws -> MobileWalletProximityCredentialStatus {
        let status = try await evaluator.evaluate(
            ProximityCredentialStatusInput(
                credentialID: credential.credentialId,
                documentType: credential.docType,
                issuer: credential.issuer,
                validFrom: credential.validFrom.toDate(),
                validUntil: credential.validUntil.toDate()
            )
        )
        switch status {
        case .valid: return .valid
        case .revoked: return .revoked
        case .indeterminate: return .indeterminate
        }
    }
}

private final class KMPProximityApplicationProfileAdapter:
    MobileWalletProximityApplicationProfile,
    @unchecked Sendable {
    private let profile: any ProximityApplicationProfile

    init(_ profile: any ProximityApplicationProfile) {
        self.profile = profile
    }

    var id: String { profile.id }

    func __evaluate(
        input: MobileWalletProximityApplicationProfileInput
    ) async throws -> any MobileWalletProximityApplicationProfileResult {
        guard let deviceRequest = Data(base64URLEncoded: input.deviceRequestBase64Url) else {
            throw WalletError.internalFailure("Wallet core returned an invalid application-profile request")
        }
        let result = try await profile.evaluate(
            ProximityApplicationProfileInput(
                deviceRequest: deviceRequest,
                credentials: swiftArray(input.credentials, of: MobileWalletProximityApplicationCredential.self).map {
                    ProximityApplicationCredential(
                        credentialID: $0.credentialId,
                        documentType: $0.docType,
                        label: $0.label
                    )
                },
                requestedDocuments: swiftArray(
                    input.requestedDocuments,
                    of: MobileWalletProximityApplicationDocumentRequest.self
                ).map { $0.toSwiftRequest() },
                readerAuthentication: swiftArray(
                    input.readerAuthentication,
                    of: MobileWalletProximityReaderAuthentication.self
                ).map { $0.toSwiftAuthentication() }
            )
        )
        switch result {
        case .notRecognized:
            return MobileWalletProximityApplicationProfileResultNotRecognized()
        case let .rejected(reason):
            return MobileWalletProximityApplicationProfileResultRejected(reason: reason)
        case let .recognized(authorization):
            return MobileWalletProximityApplicationProfileResultRecognized(
                authorization: MobileWalletProximityApplicationAuthorization(
                    profileId: authorization.profileID,
                    displayTitle: authorization.displayTitle,
                    details: authorization.details.map {
                        MobileWalletProximityApplicationAuthorizationDetail(
                            id: $0.id,
                            label: $0.label,
                            value: $0.value
                        )
                    },
                    compatibleCredentialIds: authorization.compatibleCredentialIDs,
                    deviceSignedElements: authorization.deviceSignedElements.map {
                        MobileWalletProximityDeviceSignedElement(
                            credentialId: $0.credentialID,
                            namespace: $0.namespace,
                            elementIdentifier: $0.elementIdentifier,
                            valueCborBase64Url: $0.valueCBOR.base64URLEncodedString()
                        )
                    },
                    resultBindingDigestBase64Url: authorization.resultBindingDigest.base64URLEncodedString()
                )
            )
        }
    }
}

private final class KMPProximityPresentationSessionBridge:
    ProximityPresentationSessionBridge,
    @unchecked Sendable {
    private let session: any MobileWalletProximitySession

    init(session: any MobileWalletProximitySession) {
        self.session = session
    }

    var states: AsyncStream<ProximityPresentationState> {
        AsyncStream { continuation in
            let task = Task { [session] in
                let flow = SkieSwiftFlow<any MobileWalletProximityState>(
                    SkieKotlinFlow(session.state)
                )
                for await state in flow {
                    do {
                        continuation.yield(try state.toSwiftState())
                    } catch {
                        continuation.yield(
                            .failed(
                                ProximityPresentationError(
                                    category: .internalFailure,
                                    code: "invalid_sdk_state",
                                    message: "The wallet returned an invalid proximity session state",
                                    recoverable: false
                                )
                            )
                        )
                        break
                    }
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func dispatch(
        _ action: ProximityPresentationAction
    ) async throws -> ProximityPresentationActionResult {
        try await session.dispatch(action: action.toKMPAction()).toSwiftResult()
    }

    func close() async {
        try? await session.close()
    }
}

private extension Waltid_openid4vc_walletWalletIssuanceSession {
    func toSwiftIssuanceSession() throws -> IssuanceSession {
        IssuanceSession(
            id: id,
            offer: try offer.toSwiftIssuanceOfferPreview()
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
            logoAltText: logoAltText,
            metadataProvenance: metadataProvenance.toSwiftMetadataProvenance()
        )
    }
}

private extension Waltid_openid4vc_walletWalletIssuanceMetadataProvenance {
    func toSwiftMetadataProvenance() -> MetadataProvenance {
        switch self {
        case is Waltid_openid4vc_walletWalletIssuanceMetadataProvenanceUnsigned:
            return .unsigned
        case let signed as Waltid_openid4vc_walletWalletIssuanceMetadataProvenanceSigned:
            return .signed(
                SignedMetadataProvenance(
                    compactJWT: signed.compactJwt,
                    algorithm: signed.algorithm,
                    keyID: signed.keyId,
                    trustType: signed.trustType == .trustedIssuer ? .trustedIssuer : .trustedDelegate
                )
            )
        default:
            preconditionFailure("Unsupported issuer metadata provenance: \(type(of: self))")
        }
    }
}

private extension Waltid_openid4vc_walletWalletIssuanceCredentialPreview {
    func toSwiftIssuanceCredentialPreview() -> IssuanceCredentialPreview {
        IssuanceCredentialPreview(
            configurationID: configurationId,
            format: format,
            name: name,
            descriptionText: descriptionText,
            logoURI: logoUri.flatMap(URL.init(string:)),
            logoAltText: logoAltText,
            backgroundColor: backgroundColor,
            backgroundImageURI: backgroundImageUri.flatMap(URL.init(string:)),
            textColor: textColor,
            vct: vct,
            doctype: doctype
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
            pushedAuthorizationRequestUsed: pushedAuthorizationRequestUsed,
            requestURIExpiresAt: requestUriExpiresAtEpochMilliseconds.map {
                Date(timeIntervalSince1970: TimeInterval($0.int64Value) / 1000.0)
            }
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
            issuerMetadataTrustResolver: issuerMetadataTrustResolver.map {
                KMPIssuerMetadataTrustResolverAdapter(resolver: $0)
            },
            preferredLocales: preferredLocales,
            transactionDataProfiles: transactionDataProfiles.map { $0.toKMPTransactionDataProfile() },
            clientIdTrustConfiguration: clientIDTrustConfiguration.toKMPClientIDTrustConfiguration(),
            appGroupIdentifier: crossProcessAccess?.appGroupIdentifier,
            keychainAccessGroup: crossProcessAccess?.keychainAccessGroup,
            defaultKeyUseAuthorizationPolicy: defaultKeyUseAuthorizationPolicy.toKMPAuthorizationPolicy(),
            keyUseAuthorizationPrompt: Waltid_openid4vc_wallet_persistence_mobileKeyUseAuthorizationPrompt(
                reason: keyUseAuthorizationPrompt.message,
                cancelText: keyUseAuthorizationPrompt.cancelText
            )
        )
    }
}

private final class KMPIssuerMetadataTrustResolverAdapter: WalletBridgeIssuerMetadataTrustResolver, @unchecked Sendable {
    private let resolver: any IssuerMetadataTrustResolver

    init(resolver: any IssuerMetadataTrustResolver) {
        self.resolver = resolver
    }

    func __verify(compactJwt: String, expectedCredentialIssuer: String) async throws -> WalletBridgeIssuerMetadataSigner {
        let signer = try await resolver.verify(
            compactJWT: compactJwt,
            expectedCredentialIssuer: expectedCredentialIssuer
        )
        return WalletBridgeIssuerMetadataSigner(
            keyId: signer.keyID,
            algorithm: signer.algorithm,
            trustType: signer.trustType.toKMPTrustType()
        )
    }
}

private extension MetadataTrustType {
    func toKMPTrustType() -> WalletBridgeIssuerMetadataSignerTrustType {
        switch self {
        case .trustedIssuer:
            return .trustedIssuer
        case .trustedDelegate:
            return .trustedDelegate
        }
    }
}

private extension WalletClientIDTrustConfiguration {
    func toKMPClientIDTrustConfiguration() -> WalletBridgeClientIdTrustConfiguration {
        WalletBridgeClientIdTrustConfiguration(
            x509TrustAnchorsPem: x509TrustAnchorsPEM,
            preRegisteredClientMetadataJson: preRegisteredClientMetadataJSON
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
            credentialStore: credentialStore.map { KMPWalletCredentialStoreAdapter(store: $0) },
            didStore: didStore.map { KMPWalletDidStoreAdapter(store: $0) }
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

private extension StoredCredential {
    func toKMPStoredCredential() -> WalletBridgeStoredCredential {
        WalletBridgeStoredCredential(
            id: id,
            serializedCredential: serializedCredential,
            format: format,
            label: label,
            addedAt: addedAt.map { ISO8601DateFormatter().string(from: $0) },
            metadataJson: metadataJSON
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
            addedAt: addedAt.flatMap(parseWalletISO8601Date),
            metadataJSON: metadataJson
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

private extension WalletKeyUseAuthorizationPolicy {
    func toKMPAuthorizationPolicy() -> WalletBridgeKeyUseAuthorizationPolicy {
        switch self {
        case .none:
            return WalletBridgeKeyUseAuthorizationPolicy(
                type: .none,
                timeoutSeconds: nil
            )
        case .biometricCurrentSet:
            return WalletBridgeKeyUseAuthorizationPolicy(
                type: .biometricCurrentSet,
                timeoutSeconds: nil
            )
        case .biometricTimedReuse(let timeoutSeconds):
            precondition((1...30).contains(timeoutSeconds), "Timed biometric reuse timeout must be between 1 and 30 seconds")
            return WalletBridgeKeyUseAuthorizationPolicy(
                type: .biometricTimedReuse,
                timeoutSeconds: KotlinInt(int: Int32(timeoutSeconds))
            )
        }
    }
}

private extension WalletBridgeKeyUseAuthorizationPolicy {
    func toSwiftAuthorizationPolicy() -> WalletKeyUseAuthorizationPolicy {
        switch type {
        case .none: return .none
        case .biometricCurrentSet: return .biometricCurrentSet
        case .biometricTimedReuse:
            guard let timeoutSeconds else {
                preconditionFailure("Timed biometric reuse preflight omitted its timeout")
            }
            return .biometricTimedReuse(timeoutSeconds: Int(timeoutSeconds.intValue))
        }
    }
}

private func toSwiftAuthorizationPolicy(
    _ policy: any Waltid_openid4vc_wallet_persistence_mobileKeyUseAuthorizationPolicy
) -> WalletKeyUseAuthorizationPolicy {
    switch onEnum(of: policy) {
    case .none:
        return .none
    case .biometricCurrentSet:
        return .biometricCurrentSet
    case .biometricTimedReuse(let timedReuse):
        return .biometricTimedReuse(timeoutSeconds: Int(timedReuse.timeoutSeconds))
    }
}

private extension WalletBridgeKeyUseAuthorizationReuseEnforcement {
    func toSwiftAuthorizationReuseEnforcement() -> WalletKeyUseAuthorizationReuseEnforcement {
        switch self {
        case .platformKeyStore: return .platformKeyStore
        case .providerProcess: return .providerProcess
        }
    }
}

private extension WalletBridgeKeyUseAuthorizationReuseTimeoutValidation {
    func toSwiftAuthorizationTimeoutValidation() -> WalletKeyUseAuthorizationReuseTimeoutValidation {
        switch self {
        case .independentReadback: return .independentReadback
        case .providerConfigurationOnly: return .providerConfigurationOnly
        }
    }
}

private extension Waltid_openid4vc_wallet_persistence_mobileKeyUseAuthorizationUnsupportedReason {
    func toSwiftAuthorizationUnsupportedReason() -> WalletKeyUseAuthorizationUnsupportedReason {
        switch self {
        case .unsupportedCombination: return .unsupportedCombination
        case .biometricUnavailable: return .biometricUnavailable
        case .biometricNotEnrolled: return .biometricNotEnrolled
        }
    }
}

private extension Waltid_openid4vc_wallet_persistence_mobileKeyUseAuthorizationFailure {
    func toSwiftAuthorizationFailure() -> WalletKeyUseAuthorizationFailure {
        switch self {
        case .unsupportedCombination: return .unsupportedCombination
        case .biometricUnavailable: return .biometricUnavailable
        case .biometricNotEnrolled: return .biometricNotEnrolled
        case .interactionContextUnavailable: return .interactionContextUnavailable
        case .authorizationNotCompleted: return .authorizationNotCompleted
        case .protectedKeyUnavailable: return .protectedKeyUnavailable
        case .invalidStoredKeyMetadata: return .invalidStoredKeyMetadata
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
            credentialDataJSON: requiredCredentialDataJSON(credentialDataJson),
            metadataJSON: metadataJson
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
                    request: result.request.toSwiftRequestContext(),
                    code: result.errorCode.toSwiftErrorCode(),
                    message: result.message
                )
            )
        default:
            throw WalletError.internalFailure("Unsupported presentation preview result: \(type(of: self))")
        }
    }
}

private extension AnnexCParsedRequest {
    func toKMPParsedRequest() -> MobileWalletAnnexCParsedRequest {
        MobileWalletAnnexCParsedRequest(
            documents: documents.map {
                MobileWalletAnnexCDocumentRequest(
                    docType: $0.documentType,
                    namespaces: $0.namespaces
                )
            }
        )
    }
}

private extension MobileWalletDigitalCredentialCapabilities {
    func toSwiftCapabilities() -> DigitalCredentialCapabilities {
        DigitalCredentialCapabilities(
            platform: platform,
            platformAvailable: platformAvailable,
            minimumOSVersion: minimumOsVersion,
            registrationAvailable: registrationAvailable,
            capabilities: swiftArray(capabilities, of: MobileWalletDigitalCredentialCapability.self).map {
                DigitalCredentialCapability(
                    protocolIdentifier: $0.protocol,
                    // `identifier`, not String(describing:): SKIE renders these as Swift enums, so
                    // describing them would publish compiler-derived case names as API.
                    credentialFormats: swiftArray($0.credentialFormats, of: MobileWalletDigitalCredentialFormat.self).map(\.identifier),
                    requestProtection: swiftArray($0.requestProtection, of: MobileWalletDigitalCredentialRequestProtection.self).map(\.identifier),
                    responseProtection: swiftArray($0.responseProtection, of: MobileWalletDigitalCredentialResponseProtection.self).map(\.identifier),
                    supported: $0.supported,
                    unsupportedReason: $0.unsupportedReason
                )
            }
        )
    }
}

private extension MobileWalletAnnexCPreview {
    func toSwiftPreview() -> AnnexCPresentationPreview {
        AnnexCPresentationPreview(
            requestID: requestId,
            verifiedOrigin: verifiedOrigin,
            parsedRequest: AnnexCParsedRequest(
                documents: swiftArray(parsedRequest.documents, of: MobileWalletAnnexCDocumentRequest.self).map {
                    AnnexCDocumentRequest(documentType: $0.docType, namespaces: $0.namespaces as? [String: [String]] ?? [:])
                }
            ),
            credentialOptions: swiftArray(credentialOptions, of: MobileWalletPresentationCredentialOption.self).map { $0.toSwiftCredentialOption() },
            readerTrust: readerTrust.toSwiftReaderTrust()
        )
    }
}

private extension MobileWalletReaderTrust {
    func toSwiftReaderTrust() -> ReaderTrust {
        if self is MobileWalletReaderTrustNotApplicable { return .notApplicable }
        if self is MobileWalletReaderTrustNotAuthenticated { return .notAuthenticated }
        if self is MobileWalletReaderTrustPendingRawRequest { return .pendingRawRequest }
        if let value = self as? MobileWalletReaderTrustUntrusted { return .untrusted(reason: value.reason) }
        if let value = self as? MobileWalletReaderTrustTrusted { return .trusted(certificateSubject: value.certificateSubject) }
        // A state this SDK build does not know about cannot be reported as identifying the reader,
        // and the reason string says so rather than implying a rejected trust policy.
        return .untrusted(reason: "Unrecognized reader trust state")
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

private extension MobileWalletPresentationRequestContext {
    func toSwiftRequestContext() -> PresentationRequestContext {
        PresentationRequestContext(
            clientID: clientId,
            verifierMetadata: verifierMetadata?.toSwiftVerifierMetadata(),
            requestAuthentication: requestAuthentication.toSwiftRequestAuthentication(),
            responseURI: responseUri.flatMap(URL.init(string:)),
            state: state,
            nonce: nonce,
            responseEncryption: responseEncryption.toSwiftResponseEncryption()
        )
    }
}

private extension MobileWalletPresentationRequestInfo {
    func toSwiftRequestInfo() -> PresentationRequestInfo {
        PresentationRequestInfo(
            clientID: clientId,
            verifierMetadata: verifierMetadata?.toSwiftVerifierMetadata(),
            requestAuthentication: requestAuthentication.toSwiftRequestAuthentication(),
            responseURI: responseUri.flatMap(URL.init(string:)),
            state: state,
            nonce: nonce,
            responseEncryption: responseEncryption.toSwiftResponseEncryption(),
            transactionData: swiftArray(transactionData, of: MobileWalletTransactionDataItem.self)
                .map { $0.toSwiftTransactionData() }
        )
    }
}

private extension MobileWalletRequestAuthentication {
    func toSwiftRequestAuthentication() -> PresentationRequestAuthentication {
        switch self {
        case is MobileWalletRequestAuthenticationUnauthenticated:
            return .unauthenticated
        case let authenticated as MobileWalletRequestAuthenticationAuthenticated:
            return .authenticated(
                compactRequestObject: authenticated.compactRequestObject,
                algorithm: authenticated.algorithm,
                keyID: authenticated.keyId,
                clientIDScheme: authenticated.clientIdScheme.toSwiftClientIDScheme()
            )
        default:
            preconditionFailure("Unsupported request authentication: \(type(of: self))")
        }
    }
}

private extension MobileWalletClientIdScheme {
    func toSwiftClientIDScheme() -> PresentationClientIDScheme {
        switch self {
        case .preRegistered:
            return .preRegistered
        case .redirectUri:
            return .redirectURI
        case .x509SanDns:
            return .x509SanDNS
        case .x509Hash:
            return .x509Hash
        case .decentralizedIdentifier:
            return .decentralizedIdentifier
        case .verifierAttestation:
            return .verifierAttestation
        case .openidFederation:
            return .openIDFederation
        }
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
                .map { $0.toSwiftDisclosure() },
            metadataJSON: metadataJson
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
        switch self {
        case .issuanceOfferResolved: return .issuanceOfferResolved
        case .issuanceAttestationObtained: return .issuanceAttestationObtained
        case .issuanceTokenObtained: return .issuanceTokenObtained
        case .issuanceProofSigned: return .issuanceProofSigned
        case .issuanceCredentialReceived: return .issuanceCredentialReceived
        case .issuanceDeferred: return .issuanceDeferred
        case .issuanceCredentialStored: return .issuanceCredentialStored
        case .issuanceCompleted: return .issuanceCompleted
        case .issuanceFailed: return .issuanceFailed
        case .presentationRequestParsed: return .presentationRequestParsed
        case .presentationCredentialsSelected: return .presentationCredentialsSelected
        case .presentationSigned: return .presentationSigned
        case .presentationResponsePrepared: return .presentationResponsePrepared
        case .presentationSubmitted: return .presentationSubmitted
        case .presentationCompleted: return .presentationCompleted
        case .presentationFailed: return .presentationFailed
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
        case .authorization:
            guard let authorizationFailure else {
                return .internalFailure("Authorization error did not include a failure reason")
            }
            return .keyUseAuthorization(authorizationFailure.toSwiftAuthorizationFailure())
        case .cancelled:
            return .cancelled
        case .internalFailure:
            return .internalFailure(message)
        }
    }
}

private extension ProximityPresentationProfile {
    func toKMPProfile() -> MobileWalletProximityProfile {
        switch self {
        case .iso1801352021: return .iso1801352021
        case .iso180135Edition2DIS2026: return .iso180135Edition2Dis2026
        case .eudiARF3FCAF202608: return .eudiArf3Fcaf202608
        }
    }
}

private extension MobileWalletProximityProfile {
    func toSwiftProfile() -> ProximityPresentationProfile {
        switch self {
        case .iso1801352021: return .iso1801352021
        case .iso180135Edition2Dis2026: return .iso180135Edition2DIS2026
        case .eudiArf3Fcaf202608: return .eudiARF3FCAF202608
        }
    }
}

private extension ProximityPresentationBLERoles {
    func toKMPRoles() -> MobileWalletProximityBleRoles {
        switch self {
        case .centralClient: return .centralClient
        case .peripheralServer: return .peripheralServer
        case .dual: return .dual
        }
    }
}

private extension ProximityPresentationBLEBearerPolicy {
    func toKMPPolicy() -> MobileWalletProximityBleBearerPolicy {
        switch self {
        case .gattOnly: return .gattOnly
        case .preferL2CAP: return .preferL2cap
        }
    }
}

private extension ProximityPresentationEngagementMethod {
    func toKMPMethod() -> MobileWalletProximityEngagementMethod {
        switch self {
        case .qr: return .qr
        case .nfc: return .nfc
        }
    }
}

private extension ProximityPresentationRetrievalMethod {
    func toKMPMethod() -> MobileWalletProximityRetrievalMethod {
        switch self {
        case .bluetoothLowEnergy: return .bluetoothLowEnergy
        case .nfc: return .nfc
        case .wifiAware: return .wifiAware
        }
    }
}

private extension ProximityPresentationReaderPolicy {
    func toKMPPolicy() -> MobileWalletProximityReaderPolicy {
        switch self {
        case .allowAnonymousOrUntrusted: return .allowAnonymousOrUntrusted
        case .requireTrusted: return .requireTrusted
        }
    }
}

private extension ProximityDeviceAuthenticationPolicy {
    func toKMPPolicy() -> MobileWalletProximityDeviceAuthenticationPolicy {
        switch self {
        case .signatureOnly: return .signatureOnly
        case .macOnly: return .macOnly
        case .preferSignature: return .preferSignature
        case .preferMAC: return .preferMac
        }
    }
}

private extension MobileWalletProximityReaderAuthenticationScope {
    func toSwiftScope() -> ProximityReaderAuthenticationScope {
        switch self {
        case .document: return .document
        case .wholeRequest: return .wholeRequest
        }
    }
}

private extension ProximityReaderTrustState {
    func toKMPState() -> MobileWalletProximityReaderTrustState {
        switch self {
        case .notEvaluated: return .notEvaluated
        case .validButUntrusted: return .validButUntrusted
        case .revoked: return .revoked
        case .trusted: return .trusted
        }
    }
}

private extension ProximityReaderCertificatePathState {
    func toKMPState() -> MobileWalletProximityReaderCertificatePathState {
        switch self {
        case .notEvaluated: return .notEvaluated
        case .invalid: return .invalid
        case .valid: return .valid
        }
    }
}

private extension ProximityReaderRevocationState {
    func toKMPState() -> MobileWalletProximityReaderRevocationState {
        switch self {
        case .notChecked: return .notChecked
        case .good: return .good
        case .revoked: return .revoked
        case .indeterminate: return .indeterminate
        }
    }
}

private extension ProximityRICALState {
    func toKMPState() -> MobileWalletProximityRicalState {
        switch self {
        case .notEvaluated: return .notEvaluated
        case .unavailable: return .unavailable
        case .invalid: return .invalid
        case .noMatchingAuthority: return .noMatchingAuthority
        case .matched: return .matched
        }
    }
}

private extension MobileWalletProximityCapabilities {
    func toSwiftCapabilities() -> ProximityPresentationCapabilities {
        ProximityPresentationCapabilities(
            profile: profile.toSwiftProfile(),
            qrEngagement: qrEngagement.toSwiftCapability(),
            nfcEngagement: nfcEngagement.toSwiftCapability(),
            bluetoothLowEnergy: bluetoothLowEnergy.toSwiftCapability(),
            nfcRetrieval: nfcRetrieval.toSwiftCapability(),
            wifiAwareRetrieval: wifiAwareRetrieval.toSwiftCapability()
        )
    }
}

private extension MobileWalletProximityTransportCapability {
    func toSwiftCapability() -> ProximityPresentationTransportCapability {
        ProximityPresentationTransportCapability(
            implemented: implemented,
            profilePermitted: profilePermitted,
            runtimeAvailable: runtimeAvailable,
            selected: selected,
            unavailable: unavailable?.toSwiftError(),
            remediationActions: swiftArray(
                remediationActions,
                of: MobileWalletProximityRemediationAction.self
            ).map { $0.toSwiftAction() }
        )
    }
}

private extension MobileWalletProximityRemediationAction {
    func toSwiftAction() -> ProximityPresentationRemediationAction {
        switch self {
        case .requestBluetoothPermission: return .requestBluetoothPermission
        case .openApplicationSettings: return .openApplicationSettings
        case .enableBluetooth: return .enableBluetooth
        case .useSupportedDevice: return .useSupportedDevice
        case .retry: return .retry
        }
    }
}

private extension MobileWalletProximityError {
    func toSwiftError() -> ProximityPresentationError {
        ProximityPresentationError(
            category: category.toSwiftCategory(),
            code: code,
            message: message,
            recoverable: recoverable
        )
    }
}

private extension MobileWalletProximityErrorCategory {
    func toSwiftCategory() -> ProximityPresentationErrorCategory {
        switch self {
        case .capability: return .capability
        case .engagement: return .engagement
        case .transport: return .transport
        case .protocol: return .protocolFailure
        case .readerAuthentication: return .readerAuthentication
        case .trust: return .trust
        case .credential: return .credential
        case .holderKey: return .holderKey
        case .applicationProfile: return .applicationProfile
        case .staleSubmission: return .staleSubmission
        case .policy: return .policy
        case .internal: return .internalFailure
        }
    }
}

private extension ProximityPresentationAction {
    func toKMPAction() -> any MobileWalletProximityAction {
        switch self {
        case .cancel:
            return MobileWalletProximityActionCancel()
        case .decline:
            return MobileWalletProximityActionDecline()
        case .retryPrerequisites:
            return MobileWalletProximityActionRetryPrerequisites()
        case let .reportRemediation(action, result):
            return MobileWalletProximityActionReportRemediation(
                action: action.toKMPAction(),
                result: result.toKMPResult()
            )
        case let .approve(submission):
            return MobileWalletProximityActionApprove(
                submission: MobileWalletProximitySubmission(
                    documents: submission.documents.map {
                        MobileWalletProximityDocumentSubmission(
                            requestIndex: Int32($0.requestIndex),
                            credentialId: $0.credentialID,
                            disclosedElements: Set($0.disclosedElements.map {
                                MobileWalletProximityElementReference(
                                    namespace: $0.namespace,
                                    elementIdentifier: $0.elementIdentifier
                                )
                            })
                        )
                    },
                    continueAfterResponse: submission.continueAfterResponse
                )
            )
        }
    }
}

private extension ProximityPresentationRemediationAction {
    func toKMPAction() -> MobileWalletProximityRemediationAction {
        switch self {
        case .requestBluetoothPermission: return .requestBluetoothPermission
        case .openApplicationSettings: return .openApplicationSettings
        case .enableBluetooth: return .enableBluetooth
        case .useSupportedDevice: return .useSupportedDevice
        case .retry: return .retry
        }
    }
}

private extension ProximityPresentationHostActionResult {
    func toKMPResult() -> MobileWalletProximityHostActionResult {
        switch self {
        case .completed: return .completed
        case .cancelled: return .cancelled
        case .failed: return .failed
        }
    }
}

private extension MobileWalletProximityActionResult {
    func toSwiftResult() -> ProximityPresentationActionResult {
        switch onEnum(of: self) {
        case .accepted:
            return .accepted
        case let .rejected(value):
            return .rejected(value.error.toSwiftError())
        }
    }
}

private extension MobileWalletProximityState {
    func toSwiftState() throws -> ProximityPresentationState {
        switch onEnum(of: self) {
        case let .checkingPrerequisites(value):
            return .checkingPrerequisites(value.capabilities.toSwiftCapabilities())
        case let .preparing(value):
            return .preparing(profile: value.profile.toSwiftProfile())
        case let .engagementReady(value):
            return .engagementReady(
                swiftArray(value.engagements, of: MobileWalletProximityEngagement.self).map {
                    $0.toSwiftEngagement()
                }
            )
        case let .connecting(value):
            return .connecting(
                swiftArray(value.engagements, of: MobileWalletProximityEngagement.self).map {
                    $0.toSwiftEngagement()
                }
            )
        case let .awaitingRequest(value):
            return .awaitingRequest(exchange: Int(value.exchange))
        case let .reviewRequired(value):
            return .reviewRequired(try value.review.toSwiftReview())
        case let .authorizingHolderKey(value):
            return .authorizingHolderKey(value.authorization.toSwiftAuthorization())
        case let .sendingResponse(value):
            return .sendingResponse(exchange: Int(value.exchange))
        case let .awaitingNextRequest(value):
            return .awaitingNextRequest(completedExchanges: Int(value.completedExchanges))
        case let .terminating(value):
            return .terminating(exchange: Int(value.exchange))
        case let .completed(value):
            return .completed(exchanges: Int(value.exchanges), declined: value.declined)
        case .cancelled:
            return .cancelled
        case let .failed(value):
            return .failed(value.error.toSwiftError())
        }
    }
}

private extension MobileWalletProximityEngagement {
    func toSwiftEngagement() -> ProximityPresentationEngagement {
        switch onEnum(of: self) {
        case let .qr(value): return .qr(payload: value.payload)
        case .nfc: return .nfc
        }
    }
}

private extension MobileWalletProximityHolderAuthorization {
    func toSwiftAuthorization() -> ProximityHolderAuthorization {
        ProximityHolderAuthorization(
            exchange: Int(exchange),
            requests: swiftArray(
                requests,
                of: MobileWalletProximityHolderAuthorizationRequest.self
            ).map { request in
                ProximityHolderAuthorizationRequest(
                    requestIndex: Int(request.requestIndex),
                    credentialID: request.credentialId,
                    deviceAuthentication: request.deviceAuthentication.toSwiftMethod()
                )
            }
        )
    }
}

private extension MobileWalletProximityReview {
    func toSwiftReview() throws -> ProximityPresentationReview {
        ProximityPresentationReview(
            exchange: Int(exchange),
            documents: try swiftArray(documents, of: MobileWalletProximityDocumentReview.self).map {
                try $0.toSwiftReview()
            },
            readerAuthentication: swiftArray(
                readerAuthentication,
                of: MobileWalletProximityReaderAuthentication.self
            ).map { $0.toSwiftAuthentication() },
            useCases: swiftArray(useCases, of: MobileWalletProximityUseCase.self).map {
                $0.toSwiftUseCase()
            },
            applicationAuthorizations: try swiftArray(
                applicationAuthorizations,
                of: MobileWalletProximityApplicationAuthorization.self
            ).map { try $0.toSwiftAuthorization() }
        )
    }
}

private extension MobileWalletProximityDocumentReview {
    func toSwiftReview() throws -> ProximityDocumentReview {
        ProximityDocumentReview(
            requestIndex: Int(requestIndex),
            documentType: docType,
            credentialOptions: try swiftArray(
                credentialOptions,
                of: MobileWalletProximityCredentialOption.self
            ).map { try $0.toSwiftOption() }
        )
    }
}

private extension MobileWalletProximityCredentialOption {
    func toSwiftOption() throws -> ProximityCredentialOption {
        ProximityCredentialOption(
            credentialID: credentialId,
            label: label,
            issuer: issuer,
            validUntil: validUntil.toDate(),
            deviceAuthentication: deviceAuthentication.toSwiftMethod(),
            requestedElements: swiftArray(
                requestedElements,
                of: MobileWalletProximityRequestedElement.self
            ).map { $0.toSwiftElement() }
        )
    }
}

private extension MobileWalletProximityDeviceAuthenticationMethod {
    func toSwiftMethod() -> ProximityDeviceAuthenticationMethod {
        switch self {
        case .signature: return .signature
        case .mac: return .mac
        }
    }
}

private extension MobileWalletProximityApplicationDocumentRequest {
    func toSwiftRequest() -> ProximityApplicationDocumentRequest {
        ProximityApplicationDocumentRequest(
            requestIndex: Int(requestIndex),
            documentType: docType,
            requestedElements: swiftArray(
                requestedElements,
                of: MobileWalletProximityRequestedElement.self
            ).map { $0.toSwiftElement() }
        )
    }
}

private extension MobileWalletProximityRequestedElement {
    func toSwiftElement() -> ProximityRequestedElement {
        ProximityRequestedElement(
            namespace: namespace,
            elementIdentifier: elementIdentifier,
            intentToRetain: intentToRetain,
            satisfiesRequestedElements: swiftArray(
                satisfiesRequestedElements,
                of: MobileWalletProximityElementReference.self
            ).map { ProximityElementReference(namespace: $0.namespace, elementIdentifier: $0.elementIdentifier) }
        )
    }
}

private extension MobileWalletProximityReaderAuthentication {
    func toSwiftAuthentication() -> ProximityReaderAuthentication {
        ProximityReaderAuthentication(
            scope: scope.toSwiftScope(),
            documentRequestIndex: documentRequestIndex.map { Int($0.int32Value) },
            authenticationIndex: Int(authenticationIndex),
            validity: validity.toSwiftValidity(),
            trust: trust.toSwiftTrust(),
            certificatePath: certificatePath.toSwiftPath(),
            revocation: revocation.toSwiftRevocation(),
            rical: rical.toSwiftRICAL(),
            displayName: displayName,
            reason: reason
        )
    }
}

private extension MobileWalletProximityReaderAuthenticationValidity {
    func toSwiftValidity() -> ProximityReaderAuthenticationValidity {
        switch self {
        case .absent: return .absent
        case .malformed: return .malformed
        case .invalid: return .invalid
        case .valid: return .valid
        }
    }
}

private extension MobileWalletProximityReaderTrustState {
    func toSwiftTrust() -> ProximityReaderTrustState {
        switch self {
        case .notEvaluated: return .notEvaluated
        case .validButUntrusted: return .validButUntrusted
        case .revoked: return .revoked
        case .trusted: return .trusted
        }
    }
}

private extension MobileWalletProximityReaderCertificatePathState {
    func toSwiftPath() -> ProximityReaderCertificatePathState {
        switch self {
        case .notEvaluated: return .notEvaluated
        case .invalid: return .invalid
        case .valid: return .valid
        }
    }
}

private extension MobileWalletProximityReaderRevocationState {
    func toSwiftRevocation() -> ProximityReaderRevocationState {
        switch self {
        case .notChecked: return .notChecked
        case .good: return .good
        case .revoked: return .revoked
        case .indeterminate: return .indeterminate
        }
    }
}

private extension MobileWalletProximityRicalState {
    func toSwiftRICAL() -> ProximityRICALState {
        switch self {
        case .notEvaluated: return .notEvaluated
        case .unavailable: return .unavailable
        case .invalid: return .invalid
        case .noMatchingAuthority: return .noMatchingAuthority
        case .matched: return .matched
        }
    }
}

private extension MobileWalletProximityUseCase {
    func toSwiftUseCase() -> ProximityUseCase {
        ProximityUseCase(
            index: Int(index),
            mandatory: mandatory,
            documentRequestIndices: swiftArray(documentRequestIndices, of: KotlinInt.self).map {
                Int($0.int32Value)
            },
            purposeHints: swiftArray(purposeHints, of: MobileWalletProximityPurposeHint.self).map {
                ProximityPurposeHint(
                    type: $0.type,
                    code: Int($0.code),
                    readerAsserted: $0.readerAsserted
                )
            }
        )
    }
}

private extension MobileWalletProximityApplicationAuthorization {
    func toSwiftAuthorization() throws -> ProximityApplicationAuthorization {
        let digest = try decodedBase64URL(resultBindingDigestBase64Url, context: "application binding digest")
        return ProximityApplicationAuthorization(
            profileID: profileId,
            displayTitle: displayTitle,
            details: swiftArray(
                details,
                of: MobileWalletProximityApplicationAuthorizationDetail.self
            ).map { ProximityApplicationAuthorizationDetail(id: $0.id, label: $0.label, value: $0.value) },
            compatibleCredentialIDs: swiftSet(compatibleCredentialIds, of: String.self),
            deviceSignedElements: try swiftArray(
                deviceSignedElements,
                of: MobileWalletProximityDeviceSignedElement.self
            ).map {
                ProximityDeviceSignedElement(
                    credentialID: $0.credentialId,
                    namespace: $0.namespace,
                    elementIdentifier: $0.elementIdentifier,
                    valueCBOR: try decodedBase64URL($0.valueCborBase64Url, context: "device-signed value")
                )
            },
            resultBindingDigest: digest
        )
    }
}

private extension KotlinInstant {
    func toDate() -> Date {
        Date(timeIntervalSince1970: TimeInterval(epochSeconds) + TimeInterval(nanosecondsOfSecond) / 1_000_000_000)
    }
}

private extension Data {
    init?(base64URLEncoded value: String) {
        var base64 = value.replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        base64.append(String(repeating: "=", count: (4 - base64.count % 4) % 4))
        self.init(base64Encoded: base64)
    }

    func base64URLEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}

private func decodedBase64URL(_ value: String, context: String) throws -> Data {
    guard let data = Data(base64URLEncoded: value) else {
        throw WalletError.internalFailure("Wallet core returned an invalid \(context)")
    }
    return data
}

private func swiftSet<T: Hashable>(_ value: Any, of type: T.Type) -> Set<T> {
    if let values = value as? Set<T> {
        return values
    }
    if let values = value as? NSSet {
        return Set(values.compactMap { $0 as? T })
    }
    return []
}

#endif
