import Foundation

/// Actor-isolated entry point for the walt.id wallet SDK on iOS.
public actor WalletClient {
    /// Configuration used to create this wallet client.
    public let configuration: WalletConfiguration
    private let bridge: any WalletCoreBridge

    /// Creates a wallet client with the provided configuration.
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
    public var events: AsyncStream<WalletEvent> {
        bridge.events
    }

    /// Bootstraps wallet key material and DID state.
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
    public func receive(
        offer: URL,
        txCode: String? = nil,
        clientID: String = "wallet-client"
    ) async throws -> [String] {
        try await bridge.receive(offer: offer, txCode: txCode, clientID: clientID)
    }

    /// Lists credentials currently known to the wallet.
    public func credentials() async throws -> [Credential] {
        try await bridge.credentials()
    }

    /// Presents credentials for an OpenID4VP request URL.
    public func present(
        request: URL,
        did: String? = nil,
        runPolicies: Bool? = nil
    ) async throws -> PresentationResult {
        try await bridge.present(request: request, did: did, runPolicies: runPolicies)
    }
}
