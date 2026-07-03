import Foundation

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
        self.bridge = try DefaultWalletCoreBridgeFactory.makeBridge(configuration: configuration)
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

    /// Deletes wallet-local state and SDK-managed persistence material.
    public func deleteLocalData() async throws {
        try await bridge.deleteLocalData()
    }

    /// Presents credentials for an OpenID4VP request URL.
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
}
