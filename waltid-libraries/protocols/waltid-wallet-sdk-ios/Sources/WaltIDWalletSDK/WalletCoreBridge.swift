import Foundation

@available(macOS 10.15, *)
protocol WalletCoreBridge: Sendable {
    var events: AsyncStream<WalletEvent> { get }

    func bootstrap(keyType: WalletKeyType, didMethod: String) async throws -> WalletBootstrapResult
    func receive(offer: URL, txCode: String?, clientID: String) async throws -> [String]
    func credentials() async throws -> [Credential]
    func present(request: URL, did: String?, runPolicies: Bool?) async throws -> PresentationResult
}

@available(macOS 10.15, *)
enum DefaultWalletCoreBridgeFactory {
    static func makeBridge(configuration: WalletConfiguration) throws -> any WalletCoreBridge {
        #if canImport(WaltIDWalletCore) && os(iOS)
        return try KMPWalletCoreBridge(configuration: configuration)
        #else
        return UnavailableWalletCoreBridge()
        #endif
    }
}

@available(macOS 10.15, *)
struct UnavailableWalletCoreBridge: WalletCoreBridge {
    var events: AsyncStream<WalletEvent> {
        AsyncStream { continuation in
            continuation.finish()
        }
    }

    func bootstrap(keyType: WalletKeyType, didMethod: String) async throws -> WalletBootstrapResult {
        throw unavailableError()
    }

    func receive(offer: URL, txCode: String?, clientID: String) async throws -> [String] {
        throw unavailableError()
    }

    func credentials() async throws -> [Credential] {
        throw unavailableError()
    }

    func present(request: URL, did: String?, runPolicies: Bool?) async throws -> PresentationResult {
        throw unavailableError()
    }

    private func unavailableError() -> WalletError {
        .internalFailure("WaltIDWalletCore is only available when the iOS XCFramework is linked.")
    }
}
