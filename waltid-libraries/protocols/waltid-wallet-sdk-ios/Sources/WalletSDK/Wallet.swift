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
    /// - Returns: Persisted key and DID information for subsequent wallet
    ///   operations.
    /// - Throws: ``WalletError`` when key creation, DID creation, persistence,
    ///   or bridge communication fails.
    public func bootstrap(
        keyType: WalletKeyType? = nil,
        didMethod: String = "key"
    ) async throws -> WalletBootstrapResult {
        try await bridge.bootstrap(
            keyType: keyType ?? configuration.defaultKeyType,
            didMethod: didMethod
        )
    }

    /// Resolves a credential offer before issuance.
    ///
    /// - Parameter offer: OpenID4VCI credential offer URL received by the app.
    /// - Returns: The issuer, offered credential identifiers, and transaction-code requirement.
    /// - Throws: ``WalletError`` when the offer is invalid or issuer communication fails.
    public func resolveOffer(offer: URL) async throws -> OfferResolution {
        try await bridge.resolveOffer(offer: offer)
    }

    /// Receives credentials from an OpenID4VCI credential offer URL.
    ///
    /// - Parameters:
    ///   - offer: OpenID4VCI credential offer URL received by the app.
    ///   - txCode: Optional transaction code requested by the issuer.
    ///   - clientID: Client identifier to use for issuer interactions.
    /// - Returns: Local credential identifiers stored by the wallet.
    /// - Throws: ``WalletError`` when the offer is invalid, issuer
    ///   communication fails, issuance fails, or local persistence fails.
    public func receive(
        offer: URL,
        txCode: String? = nil,
        clientID: String = "wallet-client"
    ) async throws -> [String] {
        try await bridge.receive(offer: offer, txCode: txCode, clientID: clientID)
    }

    /// Lists credentials currently known to the wallet.
    ///
    /// - Returns: Credential metadata currently stored by the wallet.
    /// - Throws: ``WalletError`` when local storage cannot be read.
    public func credentials() async throws -> [Credential] {
        try await bridge.credentials()
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
    /// should use ``previewPresentation(request:)`` followed by
    /// ``submitPresentation(request:selectedCredentialOptions:selectedDisclosureOptions:did:runPolicies:)``.
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
    ///   - request: OpenID4VP authorization request URL received by the app.
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
        request: URL,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection]? = nil,
        did: String? = nil,
        runPolicies: Bool? = nil
    ) async throws -> PresentationResult {
        try await bridge.submitPresentation(
            request: request,
            selectedCredentialOptions: selectedCredentialOptions,
            selectedDisclosureOptions: selectedDisclosureOptions,
            did: did,
            runPolicies: runPolicies
        )
    }

    /// Sends an OpenID4VP error response for a previously previewed request.
    ///
    /// - Parameters:
    ///   - request: OpenID4VP authorization request URL received by the app.
    ///   - error: Optional authorization error code. Omit it to use the error detected during an invalid
    ///     preview, or `access_denied` for a valid request declined by the user.
    ///   - errorDescription: Optional verifier-facing error description.
    /// - Returns: Error-response delivery outcome.
    /// - Throws: ``WalletError`` when the request cannot be resolved or the error response cannot be sent.
    public func rejectPresentation(
        request: URL,
        error: PresentationErrorCode? = nil,
        errorDescription: String? = nil
    ) async throws -> PresentationResult {
        try await bridge.rejectPresentation(
            request: request,
            error: error,
            errorDescription: errorDescription
        )
    }

    /// Returns the current IdentityDocumentServices capability snapshot.
    public func digitalCredentialCapabilities() async -> DigitalCredentialCapabilities {
        #if os(iOS)
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
