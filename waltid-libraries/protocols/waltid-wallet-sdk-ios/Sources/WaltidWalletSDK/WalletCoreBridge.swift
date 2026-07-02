import Foundation

protocol WalletCoreBridge: Sendable {
    var events: AsyncStream<WalletEvent> { get }

    func bootstrap(keyType: WalletKeyType, didMethod: String) async throws -> WalletBootstrapResult
    func receive(offer: URL, txCode: String?, clientID: String) async throws -> [String]
    func credentials() async throws -> [Credential]
    func present(request: URL, did: String?, runPolicies: Bool?) async throws -> PresentationResult
}

enum DefaultWalletCoreBridgeFactory {
    static func makeBridge(configuration: WalletConfiguration) throws -> any WalletCoreBridge {
        #if canImport(WaltidWalletCore) && os(iOS)
        return try KMPWalletCoreBridge(configuration: configuration)
        #else
        return UnavailableWalletCoreBridge()
        #endif
    }
}

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
        .internalFailure("WaltidWalletCore is only available when the iOS XCFramework is linked.")
    }
}
