import Foundation
#if os(iOS)
import IdentityDocumentServices
#endif

/// Actor-isolated entry point for the walt.id wallet SDK on iOS.
///
/// Create one wallet actor per app-level wallet identity. Its methods can interleave at suspension points.
/// The host owns its UI tasks and consent state.
@available(macOS 10.15, *)
public actor Wallet {
    /// Configuration used to create this wallet.
    public let configuration: WalletConfiguration
    /// Creates a wallet with the provided configuration.
    ///
    /// - Parameter configuration: Wallet identity, default key type, and
    ///   optional attestation settings used by the underlying wallet core.
    /// - Throws: ``WalletError/invalidInput(_:)`` for invalid configuration. Storage and key-provider
    ///   failures can also prevent opening. Caller cancellation propagates through the async bridge.
    public init(configuration: WalletConfiguration = .init()) async throws {
        try Task.checkCancellation()
        self.configuration = configuration
        self.bridge = try await DefaultWalletCoreBridgeFactory.makeBridge(configuration: configuration)
    }

    /// Signing identity creation and recovery. Options are issued and validated by the shared core.
    public var signingIdentity: SigningIdentityManager { SigningIdentityManager(core: bridge.signingIdentityCore) }

    /// Emits wallet issuance and presentation progress events.
    ///
    /// Each observer receives up to ten recent events from the core. A slow Swift observer
    /// retains the newest 42 queued events. Use operation results for decisions. Cancel the
    /// observing task when the screen or wallet closes.
    public var events: AsyncStream<WalletEvent> {
        bridge.events
    }

    /// Initial and current credential-status snapshots, with a single buffered latest value.
    ///
    /// Observation performs no issuer requests. Cancelling iteration releases the native collector.
    public var credentialStatuses: AsyncThrowingStream<CredentialStatusSnapshot, Error> { bridge.credentialStatuses }

    /// Reads local status without rebuilding credential artwork or requesting issuer data.
    public func credentialStatusSnapshot() async throws -> CredentialStatusSnapshot { try await bridge.credentialStatusSnapshot() }

    /// Loads the foreground opt-in for an existing managed wallet. Unsupported stores return nil.
    public func credentialStatusMaintenanceEnrollment() async throws -> CredentialStatusMaintenanceEnrollment? {
        try await bridge.credentialStatusMaintenanceEnrollment()
    }

    /// Changes the opt-in after foreground authorization. Store the returned binding only in app-private state.
    /// - Parameter preference: The new maintenance preference to commit to the managed wallet.
    public func setCredentialStatusMaintenance(_ preference: CredentialStatusMaintenancePreference) async throws -> CredentialStatusMaintenanceEnrollment? {
        try await bridge.setCredentialStatusMaintenance(preference)
    }

    /// Reconciles the current wallet's enrollment with the app's automatic-check setting.
    /// - Parameters:
    ///   - automaticChecks: Whether the holder enabled automatic status checks.
    ///   - resumePaused: Pass true only after the holder explicitly re-enables automatic checks.
    /// - Returns: The wallet's updated enrollment, or nil when the store cannot enroll.
    public func reconcileCredentialStatusMaintenance(
        automaticChecks: Bool, resumePaused: Bool = false
    ) async throws -> CredentialStatusMaintenanceEnrollment? {
        try await bridge.reconcileCredentialStatusMaintenance(automaticChecks: automaticChecks, resumePaused: resumePaused)
    }

    /// Asks active readers to reconcile observations another handle committed. This is a local read.
    public func invalidateCredentialStatusSnapshot() async throws {
        try await bridge.invalidateCredentialStatusSnapshot()
    }

    /// Acknowledges the displayed version so a newer notice for the same credential stays pending.
    /// - Parameters:
    ///   - credentialID: The stored credential ID from the displayed notice.
    ///   - version: The opaque version from the displayed notice.
    public func acknowledgeCredentialStatusNotice(credentialID: String, version: String) async throws {
        try await bridge.acknowledgeCredentialStatusNotice(credentialID: credentialID, version: version)
    }

    /// Runs one due-only pass against an enrolled existing wallet without creating a Wallet instance.
    /// Supply the original storage namespace and cancel this async operation when the host's execution window expires.
    /// - Parameters:
    ///   - binding: The opaque grant returned when the managed wallet was enrolled.
    ///   - crossProcessAccess: The App Group and Keychain access group used to create the wallet, if configured.
    ///   - maxItems: The maximum number of credentials to process in this pass.
    ///   - budget: The execution budget for the maintenance pass, in seconds.
    public static func refreshDueStatusesForExistingWallet(
        binding: String, crossProcessAccess: WalletCrossProcessAccess? = nil,
        maxItems: Int = 25, budget: TimeInterval = 20
    ) async throws -> CredentialStatusMaintenanceResult {
        guard (1...1000).contains(maxItems), let milliseconds = Int64(exactly: (budget * 1000).rounded(.down)), milliseconds > 0 else {
            throw WalletError.invalidInput("Invalid background status work limit")
        }
        #if canImport(WalletCore) && os(iOS)
        return try await KMPWalletCoreBridge.refreshDueStatusesForExistingWallet(
            binding: binding, crossProcessAccess: crossProcessAccess, maxItems: Int32(maxItems),
            timeoutMilliseconds: milliseconds
        )
        #else
        return .ineligible
        #endif
    }


    /// Explicitly checks wallet-owned credentials; omitted IDs select all stored credentials.
    ///
    /// Force bypasses freshness reuse for eligible checks; validity, backoff and execution limits still apply.
    /// Per-credential failures and deferred work are returned
    /// without discarding other results; the status stream also updates as work completes.
    /// - Parameters:
    ///   - ids: Stored credential IDs to check, or nil to select all stored credentials.
    ///   - force: Whether to require network revalidation instead of reusing fresh evidence.
    /// - Returns: Independent per-credential outcomes, including deferred or failed checks.
    public func refreshCredentialStatuses(ids: [String]? = nil, force: Bool = false) async throws -> [CredentialStatusRefreshResult] {
        try await bridge.refreshCredentialStatuses(ids: ids, force: force)
    }

    /// Closes this instance and its status work while preserving persisted wallet data.
    public func close() async throws { try await bridge.close() }

    /// Checks whether a key-use authorization request is supported without creating a key.
    ///
    /// - Parameters:
    ///   - keyType: Algorithm to check; defaults to the identity algorithm, P-256.
    ///   - policy: Optional authorization policy override. When omitted, the configured default is used.
    public func keyUseAuthorizationPreflight(
        keyType: WalletKeyType = .secp256r1,
        policy: WalletKeyUseAuthorizationPolicy? = nil
    ) async throws -> WalletKeyUseAuthorizationPreflight {
        try await bridge.keyUseAuthorizationPreflight(
            keyType: keyType,
            policy: policy ?? configuration.keyAuthorization.policy
        )
    }

    /// Explicitly creates holder keys without replacing the active signing identity.
    ///
    /// - Parameters:
    ///   - count: Number of holder keys to create.
    ///   - keyType: Cryptographic key type supported by the issuer.
    ///   - didMethod: DID method for the associated holder DIDs.
    ///   - policy: Key authorization policy; omission uses the wallet configuration.
    /// - Returns: Bindings suitable for explicit credential selections.
    /// - Throws: ``WalletError`` if creation fails; the core cleans up a partially created batch.
    public func createIssuanceHolderKeys(
        count: Int,
        keyType: WalletKeyType = .secp256r1,
        didMethod: String = "key",
        policy: WalletKeyUseAuthorizationPolicy? = nil
    ) async throws -> [IssuanceHolderBinding] {
        try await bridge.createIssuanceHolderKeys(count: count, keyType: keyType, didMethod: didMethod,
            policy: policy ?? configuration.keyAuthorization.policy)
    }

    /// Starts a typed pre-authorized or authorization-code issuance session.
    ///
    /// - Parameter request: Credential offer and optional holder-binding configuration.
    /// - Returns: A validated session with a typed offer preview.
    /// - Throws: ``WalletError`` when offer resolution, metadata validation, or key selection fails.
    public func startIssuance(_ request: IssuanceRequest) async throws -> IssuanceSession {
        guard !request.offer.absoluteString.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw WalletError.invalidInput("Credential offer URI must not be blank")
        }
        return try await IssuanceSession(bridge.startIssuance(request: request), wallet: self)
    }

    /// Reopens an offer in the same wallet, returning nil when the offer is no longer available.
    /// - Parameter id: Session reference saved while reviewing the offer.
    public func resumeIssuance(_ id: IssuanceSessionID) async throws -> IssuanceSession? {
        try await bridge.resumeIssuance(id: id).map { try IssuanceSession($0, wallet: self) }
    }

    /// Delivers a callback with its saved browser reference after reopening the wallet.
    /// - Parameters:
    ///   - id: Browser continuation saved before leaving the wallet.
    ///   - callback: Exact URI returned to the application.
    public func completeAuthorization(_ id: IssuanceAuthorizationID, callback: URL) async throws -> IssuanceOutcome {
        try await bridge.completeAuthorization(id.rawValue, callback: callback)
    }

    /// Cancels the retained offer and discards its authorization material.
    /// - Parameter sessionID: Reference for the offer to cancel.
    public func cancelIssuance(sessionID: IssuanceSessionID) async throws -> IssuanceOutcome {
        try await bridge.cancelIssuance(sessionID: sessionID.rawValue)
    }

    /// Lists retained deferred operations after an interrupted flow or wallet recreation.
    ///
    /// - Returns: Opaque deferred handles without token or private-key material.
    /// - Throws: ``WalletError`` if the protected continuation store cannot be read.
    public func listDeferredIssuance() async throws -> [DeferredCredential] {
        try await bridge.listDeferredIssuance()
    }

    /// Polls a deferred credential operation without exposing its access material.
    ///
    /// - Parameter id: Deferred reference from an issuance outcome or the retained listing.
    /// - Returns: A stored, still-deferred, or failed outcome.
    /// - Throws: ``WalletError`` when the SDK bridge cannot perform the transition.
    public func resumeDeferredIssuance(_ id: DeferredIssuanceID) async throws -> IssuanceOutcome {
        try await bridge.resumeDeferredIssuance(deferredCredentialID: id.rawValue)
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

    /// Resolves and previews an OpenID4VP presentation request without submitting credentials.
    ///
    /// - Parameter request: OpenID4VP authorization request URL received by the app.
    /// - Returns: A reviewable preview or a protocol error that can be returned after user interaction.
    /// - Throws: ``WalletError`` when the request cannot be resolved safely or a local wallet operation fails.
    public func previewPresentation(request: URL) async throws -> PresentationPreviewResult {
        try await bridge.previewPresentation(request: request)
    }

    /// Prepares the effective claims for this selection without signing or sending credentials.
    /// Recompute after a selection change and submit the returned review after holder confirmation.
    /// - Parameters:
    ///   - previewHandle: Handle identifying the retained, wallet-bound presentation request.
    ///   - selection: The credentials and disclosures captured for this review.
    ///   - did: Optional holder DID to use consistently with the subsequent submission.
    /// - Returns: Effective credential claims and source/received path mappings for this selection.
    /// - Throws: A wallet error when the request, selection or credential format cannot be reviewed.
    public func reviewPresentation(
        previewHandle: PresentationPreviewHandle,
        selection: PresentationSelection,
        did: String? = nil
    ) async throws -> PresentationReview {
        try await bridge.retainPresentation(previewHandle: previewHandle, selection: selection, did: did, owner: self)
    }

    /// Submits only the selection and advisories captured before the holder confirmed.
    /// - Parameter review: Wallet-issued review that the holder just confirmed.
    public func submitPresentation(_ review: PresentationReview) async throws -> PresentationResult {
        guard review.owner === self else { throw WalletError.invalidInput("Review belongs to another wallet") }
        return try await review.submit()
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
        configuration: ProximityConfiguration = .init()
    ) async throws -> ProximityCapabilities {
        try await bridge.proximityPresentationCapabilities(configuration: configuration)
    }

    /// Starts one single-use in-person proximity presentation session.
    /// - Parameter configuration: Immutable configuration for this session.
    /// - Returns: A session whose state stream drives all host presentation and actions.
    public func startProximityPresentation(
        configuration: ProximityConfiguration = .init()
    ) async throws -> ProximitySession {
        ProximitySession(
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
    /// Verifies the raw request and returns an encrypted response while the reviewed status remains current.
    ///
    /// - Parameters:
    ///   - submission: Holder-approved selections and the platform request bound to the preview.
    ///   - reviewedStatus: Selected credential IDs mapped to the semantic revisions shown during consent.
    public func submitStatusReviewedAnnexCPresentation(_ submission: AnnexCSubmission, reviewedStatus: [String: String]) async throws -> DigitalCredentialResponse {
        try await bridge.submitStatusReviewedAnnexCPresentation(submission, reviewedStatus: reviewedStatus)
    }

    /// Verifies the raw request against the retained preview and returns the HPKE response JSON.
    ///
    /// - Parameters:
    ///   - submission: Holder-approved selections and the platform request bound to the preview.
    public func submitAnnexCPresentation(_ submission: AnnexCSubmission) async throws -> DigitalCredentialResponse {
        try await bridge.submitAnnexCPresentation(submission)
    }

    init(
        configuration: WalletConfiguration = .init(),
        bridge: any WalletCoreBridge
    ) {
        self.configuration = configuration
        self.bridge = bridge
    }

    private let bridge: any WalletCoreBridge

}

extension Wallet {
    /// Accepts a browser grant and builds its request using the app's registered client and exact redirect.
    /// - Parameters:
    ///   - session: Browser grant returned by the wallet.
    ///   - registration: Client identifier and exact registered callback URI.
    ///   - credentials: Selected configurations and copy counts. Omit to use the offered defaults.
    func beginAuthorization(
        _ session: IssuanceSessionID,
        registration: IssuanceClientRegistration,
        credentials: [IssuanceCredentialSelection]? = nil
    ) async throws -> PendingIssuanceAuthorization {
        let redirect = registration.redirectURI.absoluteString
        guard !registration.clientID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !redirect.isEmpty, redirect == redirect.trimmingCharacters(in: .whitespacesAndNewlines) else {
            throw WalletError.invalidInput("A registered client ID and callback URI are required")
        }
        let request = try await bridge.beginAuthorizationIssuance(
            sessionID: session.rawValue, registration: registration, credentials: credentials
        )
        return PendingIssuanceAuthorization(id: .init(session: session), request: request, wallet: self)
    }

    /// Accepts the pre-authorized grant. Supply a client identity when the authorization server requires one.
    /// - Parameters:
    ///   - session: Pre-authorized grant returned by the wallet.
    ///   - transactionCode: Code entered by the holder when the offer requires one.
    ///   - credentials: Selected configurations and copy counts.
    ///   - clientID: Client identifier when required by the authorization server.
    func accept(
        _ session: IssuanceSessionID,
        transactionCode: String? = nil,
        credentials: [IssuanceCredentialSelection]? = nil,
        clientID: String? = nil
    ) async throws -> IssuanceOutcome {
        try await bridge.continuePreAuthorizedIssuance(
            sessionID: session.rawValue, clientID: clientID, transactionCode: transactionCode, credentials: credentials
        )
    }
}
