import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

final class KMPWalletCoreBridge: WalletCoreBridge, @unchecked Sendable {
    let bridge: WalletSdkBridge
    var signingIdentityCore: any SigningIdentityCore { KMPSigningIdentityCore(bridge: bridge) }
    init(configuration: WalletConfiguration) async throws {
        WalletCoreRuntime.initializeIfNeeded()
        let result = try await WalletSdkBridgeFactory(
            nfcHostPlatformAdapter: nfcHost
        ).create(
            configuration: configuration.toKMPConfiguration()
        )
        self.bridge = try Self.successValue(result, as: WalletSdkBridge.self, operation: "create wallet bridge")
        if let provider = configuration.issuance.keyAttestationProvider {
            let attached = try await bridge.attachKeyAttestationProviderResolver(
                resolver: KMPKeyAttestationProviderResolverAdapter(resolver: provider)
            )
            _ = try Self.successAnyValue(attached, operation: "attach key attestation provider")
        }
    }

    var events: AsyncStream<WalletEvent> {
        AsyncStream(bufferingPolicy: .bufferingNewest(42)) { continuation in
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
            guard (policy.timeoutSeconds?.intValue ?? 0) > 0 else {
                throw WalletError.internalFailure("Invalid timed key authorization preflight result")
            }
            return .supported(
                effectivePolicy: policy.toSwiftAuthorizationPolicy(),
                reuseEnforcement: reuseEnforcement.toSwiftAuthorizationReuseEnforcement(),
                timeoutValidation: timeoutValidation.toSwiftAuthorizationTimeoutValidation()
            )
        case (true, let policy?, nil, nil, nil):
            guard (policy.timeoutSeconds?.intValue ?? 0) == 0 else {
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



    func createIssuanceHolderKeys(count: Int, keyType: WalletKeyType, didMethod: String, policy: WalletKeyUseAuthorizationPolicy) async throws -> [IssuanceHolderBinding] {
        guard let count = Int32(exactly: count), count > 0 else { throw WalletError.invalidInput("Holder key count must be a positive 32-bit integer") }
        let result = try await bridge.createIssuanceHolderKeys(count: count, keyType: keyType.toKMPKeyType(),
            didMethod: didMethod, keyUseAuthorizationPolicy: policy.toKMPAuthorizationPolicy())
        return swiftArray(try Self.successAnyValue(result, operation: "create issuance holder keys"), of: MobileWalletHolderBinding.self)
            .map { IssuanceHolderBinding(keyID: $0.keyId, did: $0.did) }
    }

    func listDeferredIssuance() async throws -> [DeferredCredential] {
        let result = try await bridge.listDeferredIssuance()
        return swiftArray(try Self.successAnyValue(result, operation: "list deferred issuance"),
            of: Waltid_openid4vc_walletWalletIssuanceContinuation.self).map { $0.toSwiftDeferredCredential() }
    }

    func startIssuance(request: IssuanceRequest) async throws -> IssuanceSession {
        let result = try await bridge.startIssuance(
            request: MobileWalletIssuanceRequest(
                offer: MobileWalletCredentialOfferUri(value: request.offer.absoluteString),
                keyId: request.keyID,
                did: request.did,
                keyPolicy: request.keyPolicy.toKMPSigningIdentityPolicy()
            )
        )
        let value = try Self.successValue(
            result,
            as: Waltid_openid4vc_walletWalletIssuanceBatchSession.self,
            operation: "start issuance"
        )
        return try value.toSwiftIssuanceSession()
    }

    func resumeIssuance(id: IssuanceSessionID) async throws -> IssuanceSession? {
        let result = try await bridge.resumeIssuance(sessionId: id.rawValue)
        let lookup = try Self.successValue(result, as: WalletBridgeIssuanceLookup.self, operation: "resume issuance")
        return try lookup.session?.toSwiftIssuanceSession()
    }

    func beginAuthorizationIssuance(sessionID: String, registration: IssuanceClientRegistration, credentials: [IssuanceCredentialSelection]?) async throws -> IssuanceAuthorization {
        let result = try await bridge.beginAuthorizationIssuance(sessionId: sessionID,
            registration: WalletCore.IssuanceClientRegistration(clientId: registration.clientID, redirectUri: registration.redirectURI.absoluteString),
            credentials: try credentials?.map { try $0.toKMPSelection() })
        let value = try Self.successValue(
            result,
            as: Waltid_openid4vc_walletWalletIssuanceAuthorization.self,
            operation: "begin authorization issuance"
        )
        return try value.toSwiftIssuanceAuthorization()
    }

    func continuePreAuthorizedIssuance(
        sessionID: String,
        transactionCode: String?,
        credentials: [IssuanceCredentialSelection]?, clientID: String?
    ) async throws -> IssuanceOutcome {
        let result = try await bridge.continuePreAuthorizedIssuance(
            sessionId: sessionID,
            transactionCode: transactionCode,
            credentials: try credentials?.map { try $0.toKMPSelection() },
            clientId: clientID
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

    var credentialStatuses: AsyncThrowingStream<CredentialStatusSnapshot, Error> {
        AsyncThrowingStream(bufferingPolicy: .bufferingNewest(1)) { continuation in
            let task = Task { [self] in
                let flow = SkieSwiftFlow<WalletBridgeCredentialStatusUpdate>(SkieKotlinFlow(bridge.credentialStatuses))
                do {
                    for await update in flow {
                        switch update {
                        case let value as WalletBridgeCredentialStatusUpdateSnapshot:
                            continuation.yield(try decodeCredentialStatusSnapshot(value.snapshotJson))
                        case let failure as WalletBridgeCredentialStatusUpdateFailure:
                            throw failure.error.toSwiftWalletError()
                        default: throw WalletError.internalFailure("Unknown credential status update")
                        }
                    }
                    continuation.finish()
                } catch { continuation.finish(throwing: error) }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func credentialStatusSnapshot() async throws -> CredentialStatusSnapshot {
        let result = try await bridge.credentialStatusSnapshot()
        guard let json = try Self.successAnyValue(result, operation: "read credential status") as? String else {
            throw WalletError.internalFailure("Unexpected credential status result")
        }
        return try decodeCredentialStatusSnapshot(json)
    }

    func refreshCredentialStatuses(ids: [String]?, force: Bool) async throws -> [CredentialStatusRefreshResult] {
        let result = try await bridge.refreshCredentialStatuses(credentialIds: ids, force: force)
        guard let json = try Self.successAnyValue(result, operation: "check credential status") as? String else {
            throw WalletError.internalFailure("Unexpected credential status refresh result")
        }
        return try decodeCredentialStatus([CredentialStatusRefreshResult].self, json: json)
    }

    func close() async throws {
        _ = try Self.successAnyValue(try await bridge.close(), operation: "close wallet")
    }

    func credentials() async throws -> [Credential] {
        let result = try await bridge.credentialReads()
        let value = try Self.successAnyValue(result, operation: "list credentials")
        let reads: [WalletBridgeCredentialRead]
        if let values = value as? [WalletBridgeCredentialRead] { reads = values }
        else if let values = value as? NSArray {
            reads = values.compactMap { $0 as? WalletBridgeCredentialRead }
            guard reads.count == values.count else { throw WalletError.internalFailure("Invalid credential read result") }
        } else { throw WalletError.internalFailure("Unexpected credentials result type") }
        return reads.map { $0.toSwiftCredential() }
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

    func previewPresentation(request: URL) async throws -> PresentationPreviewResult {
        let result = try await bridge.previewPresentation(requestUrl: request.absoluteString)
        let value = try Self.successValue(
            result,
            as: MobileWalletPresentationPreviewResult.self,
            operation: "preview presentation"
        )

        return try value.toSwiftPreviewResult()
    }

    func retainPresentation(
        previewHandle: PresentationPreviewHandle,
        selection: PresentationSelection,
        did: String?, owner: Wallet
    ) async throws -> PresentationReview {
        let result = try await bridge.reviewPresentation(
            previewHandle: MobileWalletPresentationPreviewHandle(value: previewHandle.value),
            selection: selection.toKMP(),
            did: did
        )
        let value = try Self.successValue(result, as: WalletBridgePresentationReview.self, operation: "review presentation")
        let claims = PreparedPresentationReview(credentials: value.claims.credentials.map { claim in
            PresentationCredentialClaims(queryID: claim.queryId, credentialID: claim.credentialId, format: claim.format,
                claimsJSON: claim.claimsJson, wholeCredential: claim.wholeCredential, locations: claim.locations.map {
                    PresentationClaimLocation(sourcePathJSON: $0.sourcePathJson, receivedPathJSON: $0.receivedPathJson,
                        sharedBy: $0.sharedBy, alwaysCarried: $0.alwaysCarried, isBinary: $0.isBinary)
                })
        })
        let statuses = try decodeCredentialStatusSnapshot(value.statusJson).credentials
        return PresentationReview(claims: claims, statuses: statuses, payment: try value.payment?.toSwiftPaymentConsent(), owner: owner) { [self] in
            let result = try await bridge.submitReviewedPresentation(review: value)
            return try Self.successValue(result, as: MobileWalletPresentationResult.self, operation: "submit reviewed presentation").toSwiftPresentationResult()
        }
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
        configuration: ProximityConfiguration
    ) async throws -> ProximityCapabilities {
        let result = try await bridge.proximityPresentationCapabilities(
            configuration: configuration.toKMPConfiguration()
        )
        return try Self.successValue(
            result,
            as: WalletCore.ProximityCapabilities.self,
            operation: "check proximity presentation capabilities"
        ).toSwiftCapabilities()
    }

    func startProximityPresentation(
        configuration: ProximityConfiguration
    ) async throws -> any ProximitySessionBridge {
        let result = try await bridge.startProximityPresentation(
            configuration: configuration.toKMPConfiguration()
        )
        let session = try Self.successValue(
            result,
            as: WalletCore.ProximitySession.self,
            operation: "start proximity presentation"
        )
        return KMPProximityPresentationSessionBridge(session: session, nfcHost: nfcHost)
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

    func submitStatusReviewedAnnexCPresentation(_ submission: AnnexCSubmission, reviewedStatus: [String: String]) async throws -> DigitalCredentialResponse {
        let result = try await bridge.submitStatusReviewedAnnexCPresentation(
            submission: submission.toKMPSubmission(),
            reviewedStatus: reviewedStatus
        )
        let value = try Self.successValue(
            result,
            as: MobileWalletDigitalCredentialResponse.self,
            operation: "submit Annex C presentation"
        )
        return DigitalCredentialResponse(protocolIdentifier: value.protocol, dataJSON: value.dataJson)
    }

    func submitAnnexCPresentation(_ submission: AnnexCSubmission) async throws -> DigitalCredentialResponse {
        let result = try await bridge.submitAnnexCPresentation(
            submission: submission.toKMPSubmission()
        )
        let value = try Self.successValue(
            result,
            as: MobileWalletDigitalCredentialResponse.self,
            operation: "submit Annex C presentation"
        )
        return DigitalCredentialResponse(protocolIdentifier: value.protocol, dataJSON: value.dataJson)
    }

    static func successValue<T>(
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

    private let nfcHost = IOSNfcHostPlatformAdapter()

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


private extension PresentationSelection {
    func toKMP() -> MobileWalletPresentationSelection {
        MobileWalletPresentationSelection(
            credentials: credentials.map { MobileWalletPresentationCredentialSelection(queryId: $0.queryID, credentialId: $0.credentialID) },
            disclosures: disclosures?.map { MobileWalletPresentationDisclosureSelection(queryId: $0.queryID, credentialId: $0.credentialID, path: $0.path) }
        )
    }
}

#endif
