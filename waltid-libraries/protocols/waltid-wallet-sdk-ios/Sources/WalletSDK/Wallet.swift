import Foundation
#if os(iOS)
import IdentityDocumentServices
#endif

/// Actor-isolated entry point for the walt.id wallet SDK on iOS.
///
/// Create one wallet actor per app-level wallet identity. The actor serializes
/// calls into the Kotlin Multiplatform wallet core and exposes native Swift
/// models and errors to app code.
@available(macOS 10.15, *)
public actor Wallet {
    /// Configuration used to create this wallet.
    public let configuration: WalletConfiguration
    private let bridge: any WalletCoreBridge

    /// Creates a wallet with the provided configuration.
    ///
    /// - Parameter configuration: Wallet identity, default key type, and
    ///   optional attestation settings used by the underlying wallet core.
    /// - Throws: ``WalletError/internalFailure(_:)`` when the wallet core
    ///   bridge cannot be created.
    public init(configuration: WalletConfiguration = .init()) async throws {
        self.configuration = configuration
        self.bridge = try await DefaultWalletCoreBridgeFactory.makeBridge(configuration: configuration)
    }

    init(
        configuration: WalletConfiguration = .init(),
        bridge: any WalletCoreBridge
    ) {
        self.configuration = configuration
        self.bridge = bridge
    }

    /// Emits wallet issuance and presentation progress events.
    ///
    /// The stream is backed by the wallet core event flow. Iteration ends when
    /// the underlying flow completes or when the consuming task is cancelled.
    public var events: AsyncStream<WalletEvent> {
        bridge.events
    }

    /// Bootstraps wallet key material and DID state.
    ///
    /// - Parameters:
    ///   - keyType: Optional key type override. When omitted, the wallet uses
    ///     ``WalletConfiguration/defaultKeyType``.
    ///   - didMethod: DID method to create for the bootstrapped wallet DID.
    ///   - keyUseAuthorizationPolicy: Optional per-bootstrap authorization
    ///     policy override. When omitted, the configured default is used.
    /// - Returns: Persisted key and DID information for subsequent wallet
    ///   operations.
    /// - Throws: ``WalletError`` when key creation, DID creation, persistence,
    ///   or bridge communication fails.
    public func bootstrap(
        keyType: WalletKeyType? = nil,
        didMethod: String = "key",
        keyUseAuthorizationPolicy: WalletKeyUseAuthorizationPolicy? = nil
    ) async throws -> WalletBootstrapResult {
        try await bridge.bootstrap(
            keyType: keyType ?? configuration.defaultKeyType,
            didMethod: didMethod,
            keyUseAuthorizationPolicy: keyUseAuthorizationPolicy
        )
    }

    /// Checks whether a key-use authorization request is supported without creating a key.
    ///
    /// - Parameters:
    ///   - keyType: Optional key type override. When omitted, the configured default is used.
    ///   - policy: Optional authorization policy override. When omitted, the configured default is used.
    public func keyUseAuthorizationPreflight(
        keyType: WalletKeyType? = nil,
        policy: WalletKeyUseAuthorizationPolicy? = nil
    ) async throws -> WalletKeyUseAuthorizationPreflight {
        try await bridge.keyUseAuthorizationPreflight(
            keyType: keyType ?? configuration.defaultKeyType,
            policy: policy ?? configuration.defaultKeyUseAuthorizationPolicy
        )
    }

    /// Starts a typed pre-authorized or authorization-code issuance session.
    ///
    /// - Parameter request: Offer, callback, client, and holder-binding configuration.
    /// - Returns: A validated session with a typed offer preview.
    /// - Throws: ``WalletError`` when offer resolution, metadata validation, or key selection fails.
    public func startIssuance(_ request: IssuanceRequest) async throws -> IssuanceSession {
        try await bridge.startIssuance(request: request)
    }

    /// Starts the authorization-code browser request for an accepted issuance session.
    ///
    /// Offer resolution and local review do not create authorization-server state. Call this
    /// method only after the user accepts an authorization-code offer, then open the returned URL
    /// in the browser and deliver the callback to ``continueAuthorizationIssuance(sessionID:callbackURI:)``.
    ///
    /// - Parameter sessionID: Opaque identifier returned by ``startIssuance(_:)``.
    /// - Returns: Browser authorization data, including the callback binding and PKCE state.
    /// - Throws: ``WalletError`` when authorization initiation cannot be completed.
    public func beginAuthorizationIssuance(sessionID: String) async throws -> IssuanceAuthorization {
        try await bridge.beginAuthorizationIssuance(sessionID: sessionID)
    }

    /// Continues a reviewed pre-authorized issuance session.
    ///
    /// - Parameters:
    ///   - sessionID: Opaque identifier returned by ``startIssuance(_:)``.
    ///   - transactionCode: Separately delivered transaction code when required by the offer.
    /// - Returns: A typed stored, deferred, cancelled, or failed outcome.
    /// - Throws: ``WalletError`` when the SDK bridge cannot perform the transition.
    public func continuePreAuthorizedIssuance(
        sessionID: String,
        transactionCode: String? = nil
    ) async throws -> IssuanceOutcome {
        try await bridge.continuePreAuthorizedIssuance(
            sessionID: sessionID,
            transactionCode: transactionCode
        )
    }

    /// Strictly validates and consumes a browser authorization callback.
    ///
    /// - Parameters:
    ///   - sessionID: Opaque identifier returned by ``startIssuance(_:)``.
    ///   - callbackURI: Complete callback URI received from the browser session.
    /// - Returns: A typed stored, deferred, cancelled, or failed outcome.
    /// - Throws: ``WalletError`` when the SDK bridge cannot perform the transition.
    public func continueAuthorizationIssuance(
        sessionID: String,
        callbackURI: URL
    ) async throws -> IssuanceOutcome {
        try await bridge.continueAuthorizationIssuance(sessionID: sessionID, callbackURI: callbackURI)
    }

    /// Cancels an active issuance session and removes its deferred continuations.
    ///
    /// - Parameter sessionID: Opaque identifier of the session to cancel.
    /// - Returns: A cancelled outcome, or a typed failure for an invalid session.
    /// - Throws: ``WalletError`` when the SDK bridge cannot perform the transition.
    public func cancelIssuance(sessionID: String) async throws -> IssuanceOutcome {
        try await bridge.cancelIssuance(sessionID: sessionID)
    }

    /// Polls a deferred credential operation without exposing its access material.
    ///
    /// - Parameter deferredCredentialID: Opaque identifier returned in a deferred outcome.
    /// - Returns: A stored, still-deferred, or failed outcome.
    /// - Throws: ``WalletError`` when the SDK bridge cannot perform the transition.
    public func resumeDeferredIssuance(deferredCredentialID: String) async throws -> IssuanceOutcome {
        try await bridge.resumeDeferredIssuance(deferredCredentialID: deferredCredentialID)
    }

    /// Lists credentials currently known to the wallet.
    ///
    /// - Returns: Credential metadata currently stored by the wallet.
    /// - Throws: ``WalletError`` when local storage cannot be read.
    public func credentials() async throws -> [Credential] {
        try await bridge.credentials()
    }

    /// Removes one stored credential by wallet-local identifier.
    ///
    /// - Parameter id: Stable wallet-local credential identifier.
    /// - Returns: `true` when the credential was removed.
    /// - Throws: ``WalletError`` when local storage cannot be updated.
    public func deleteCredential(id: String) async throws -> Bool {
        try await bridge.deleteCredential(id: id)
    }

    /// Deletes wallet-local state and managed persistence material.
    ///
    /// - Throws: ``WalletError`` when local wallet material cannot be deleted.
    public func deleteLocalData() async throws {
        try await bridge.deleteLocalData()
    }

    /// Presents credentials for an OpenID4VP request URL.
    ///
    /// This immediate submission API is intended for callers that already handled
    /// request review and user consent. Apps that need to display verifier
    /// details, credential choices, selective disclosures, or transaction data
    /// should use ``previewPresentation(request:)`` followed by an action with its preview handle.
    ///
    /// - Parameters:
    ///   - request: OpenID4VP authorization request URL received by the app.
    ///   - did: Optional wallet DID to use for presentation. When omitted,
    ///     wallet core selects an available DID.
    ///   - runPolicies: Optional policy execution override for presentation.
    /// - Returns: Presentation outcome, including verifier redirect and raw
    ///   verifier response details when available.
    /// - Throws: ``WalletError`` when the request is invalid, credential
    ///   selection fails, signing fails, or verifier communication fails.
    public func present(
        request: URL,
        did: String? = nil,
        runPolicies: Bool? = nil
    ) async throws -> PresentationResult {
        try await bridge.present(request: request, did: did, runPolicies: runPolicies)
    }

    /// Resolves and previews an OpenID4VP presentation request without submitting credentials.
    ///
    /// - Parameter request: OpenID4VP authorization request URL received by the app.
    /// - Returns: A reviewable preview or a protocol error that can be returned after user interaction.
    /// - Throws: ``WalletError`` when the request cannot be resolved safely or a local wallet operation fails.
    public func previewPresentation(request: URL) async throws -> PresentationPreviewResult {
        try await bridge.previewPresentation(request: request)
    }

    /// Submits a presentation with user-selected credential options.
    ///
    /// - Parameters:
    ///   - previewHandle: Handle returned by ``previewPresentation(request:)``.
    ///   - selectedCredentialOptions: Credential options selected from
    ///     ``previewPresentation(request:)``.
    ///   - selectedDisclosureOptions: Optional selectively disclosable claims
    ///     selected from ``previewPresentation(request:)``. Passing `nil`
    ///     preserves the wallet core's default request-matched disclosure set.
    ///   - did: Optional wallet DID to use for presentation.
    ///   - runPolicies: Optional policy execution override for presentation.
    /// - Returns: Presentation outcome.
    /// - Throws: ``WalletError`` when selection, signing, or verifier communication fails.
    public func submitPresentation(
        previewHandle: PresentationPreviewHandle,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection]? = nil,
        did: String? = nil,
        runPolicies: Bool? = nil
    ) async throws -> PresentationResult {
        try await bridge.submitPresentation(
            previewHandle: previewHandle,
            selectedCredentialOptions: selectedCredentialOptions,
            selectedDisclosureOptions: selectedDisclosureOptions,
            did: did,
            runPolicies: runPolicies
        )
    }

    /// Rejects a reviewed presentation request and consumes its preview handle.
    ///
    /// - Parameters:
    ///   - previewHandle: Handle returned by ``previewPresentation(request:)``.
    ///   - error: Optional authorization error code. Omit it to use the error detected during an invalid
    ///     preview, or `access_denied` for a valid request declined by the user.
    ///   - errorDescription: Optional verifier-facing error description.
    /// - Returns: Error-response delivery outcome.
    /// - Throws: ``WalletError`` when the handle is invalid or the error response cannot be sent.
    public func rejectPresentation(
        previewHandle: PresentationPreviewHandle,
        error: PresentationErrorCode? = nil,
        errorDescription: String? = nil
    ) async throws -> PresentationResult {
        try await bridge.rejectPresentation(
            previewHandle: previewHandle,
            error: error,
            errorDescription: errorDescription
        )
    }

    /// Discards a reviewed presentation after local dismissal.
    ///
    /// - Parameter previewHandle: Handle returned by ``previewPresentation(request:)``.
    /// - Throws: ``WalletError`` when the handle cannot be discarded.
    public func discardPresentationPreview(_ previewHandle: PresentationPreviewHandle) async throws {
        try await bridge.discardPresentationPreview(previewHandle)
    }

    /// Checks the selected proximity prerequisites without creating keys, UUIDs, or radio resources.
    /// - Parameter configuration: Capability dimensions and policy to evaluate.
    /// - Returns: A truthful capability report for every modeled dimension.
    public func proximityPresentationCapabilities(
        configuration: ProximityPresentationConfiguration = .init()
    ) async throws -> ProximityPresentationCapabilities {
        try await bridge.proximityPresentationCapabilities(configuration: configuration)
    }

    /// Starts one single-use in-person proximity presentation session.
    /// - Parameter configuration: Immutable configuration for this session.
    /// - Returns: A session whose state stream drives all host presentation and actions.
    public func startProximityPresentation(
        configuration: ProximityPresentationConfiguration = .init()
    ) async throws -> ProximityPresentationSession {
        ProximityPresentationSession(
            bridge: try await bridge.startProximityPresentation(configuration: configuration)
        )
    }
    /// Returns the current IdentityDocumentServices capability snapshot.
    public func digitalCredentialCapabilities() async -> DigitalCredentialCapabilities {
        #if canImport(WalletCore) && os(iOS)
        if #available(iOS 26.0, *),
           let appGroupIdentifier = configuration.crossProcessAccess?.appGroupIdentifier {
            let status = await IdentityDocumentProviderRegistrationStore().status
            DigitalCredentialRegistrationStorage.persist(
                status: status,
                appGroupIdentifier: appGroupIdentifier
            )
        }
        #endif
        return bridge.digitalCredentialCapabilities()
    }

    /// Retains Apple's parsed Annex C request until the user consents to raw request access.
    ///
    /// - Parameters:
    ///   - parsedRequest: Apple's pre-consent parsed document request.
    ///   - verifiedOrigin: Platform-verified origin bound to the request.
    ///   - selectedRegistryEntryIDs: Optional platform-selected credential metadata IDs.
    public func previewAnnexCPresentation(
        parsedRequest: AnnexCParsedRequest,
        verifiedOrigin: String,
        selectedRegistryEntryIDs: [String] = []
    ) async throws -> AnnexCPresentationPreview {
        try await bridge.previewAnnexCPresentation(
            parsedRequest: parsedRequest,
            verifiedOrigin: verifiedOrigin,
            selectedRegistryEntryIDs: selectedRegistryEntryIDs
        )
    }

    /// Verifies the raw request against the retained preview and returns the HPKE response JSON.
    ///
    /// - Parameters:
    ///   - requestID: Handle returned by ``previewAnnexCPresentation(parsedRequest:verifiedOrigin:selectedRegistryEntryIDs:)``.
    ///   - verifiedOrigin: Platform-verified origin bound to the preview.
    ///   - deviceRequestBase64URL: Raw post-consent device request.
    ///   - encryptionInfoBase64URL: Raw post-consent encryption information.
    ///   - selectedCredentialOptions: User-approved credentials, one per requested document.
    public func submitAnnexCPresentation(
        requestID: String,
        verifiedOrigin: String,
        deviceRequestBase64URL: String,
        encryptionInfoBase64URL: String,
        selectedCredentialOptions: [PresentationCredentialSelection]
    ) async throws -> DigitalCredentialResponse {
        try await bridge.submitAnnexCPresentation(
            requestID: requestID,
            verifiedOrigin: verifiedOrigin,
            deviceRequestBase64URL: deviceRequestBase64URL,
            encryptionInfoBase64URL: encryptionInfoBase64URL,
            selectedCredentialOptions: selectedCredentialOptions
        )
    }

}
