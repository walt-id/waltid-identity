import Foundation

@available(macOS 10.15, *)
protocol WalletCoreBridge: Sendable {
    var events: AsyncStream<WalletEvent> { get }

    func bootstrap(keyType: WalletKeyType, didMethod: String) async throws -> WalletBootstrapResult
    func resolveOffer(offer: URL) async throws -> OfferResolution
    func receive(offer: URL, txCode: String?, clientID: String) async throws -> [String]
    func receive(previewHandle: IssuancePreviewHandle, txCode: String?, clientID: String) async throws -> [String]
    func discardIssuancePreview(_ previewHandle: IssuancePreviewHandle) async throws
    func credentials() async throws -> [Credential]
    func deleteLocalData() async throws
    func present(request: URL, did: String?, runPolicies: Bool?) async throws -> PresentationResult
    func previewPresentation(request: URL) async throws -> PresentationPreviewResult
    func submitPresentation(
        previewHandle: PresentationPreviewHandle,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection]?,
        did: String?,
        runPolicies: Bool?
    ) async throws -> PresentationResult
    func rejectPresentation(
        previewHandle: PresentationPreviewHandle,
        error: PresentationErrorCode?,
        errorDescription: String?
    ) async throws -> PresentationResult
    func discardPresentationPreview(_ previewHandle: PresentationPreviewHandle) async throws
}

@available(macOS 10.15, *)
enum DefaultWalletCoreBridgeFactory {
    static func makeBridge(configuration: WalletConfiguration) async throws -> any WalletCoreBridge {
        #if canImport(WalletCore) && os(iOS)
        return try await KMPWalletCoreBridge(configuration: configuration)
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

    func resolveOffer(offer: URL) async throws -> OfferResolution {
        throw unavailableError()
    }

    func receive(offer: URL, txCode: String?, clientID: String) async throws -> [String] {
        throw unavailableError()
    }

    func receive(previewHandle: IssuancePreviewHandle, txCode: String?, clientID: String) async throws -> [String] {
        throw unavailableError()
    }

    func discardIssuancePreview(_ previewHandle: IssuancePreviewHandle) async throws {
        throw unavailableError()
    }

    func credentials() async throws -> [Credential] {
        throw unavailableError()
    }

    func deleteLocalData() async throws {
        throw unavailableError()
    }

    func present(request: URL, did: String?, runPolicies: Bool?) async throws -> PresentationResult {
        throw unavailableError()
    }

    func previewPresentation(request: URL) async throws -> PresentationPreviewResult {
        throw unavailableError()
    }

    func submitPresentation(
        previewHandle: PresentationPreviewHandle,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection]?,
        did: String?,
        runPolicies: Bool?
    ) async throws -> PresentationResult {
        throw unavailableError()
    }

    func rejectPresentation(
        previewHandle: PresentationPreviewHandle,
        error: PresentationErrorCode?,
        errorDescription: String?
    ) async throws -> PresentationResult {
        throw unavailableError()
    }

    func discardPresentationPreview(_ previewHandle: PresentationPreviewHandle) async throws {
        throw unavailableError()
    }
    private func unavailableError() -> WalletError {
        .internalFailure("WalletCore is only available when the iOS XCFramework is linked.")
    }
}
