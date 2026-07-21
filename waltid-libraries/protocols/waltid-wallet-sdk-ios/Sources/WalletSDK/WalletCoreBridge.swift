import Foundation

@available(macOS 10.15, *)
protocol WalletCoreBridge: Sendable {
    var events: AsyncStream<WalletEvent> { get }

    func bootstrap(keyType: WalletKeyType, didMethod: String) async throws -> WalletBootstrapResult
    func resolveOffer(offer: URL) async throws -> OfferResolution
    func receive(offer: URL, txCode: String?, clientID: String) async throws -> [String]
    func credentials() async throws -> [Credential]
    func deleteLocalData() async throws
    func present(request: URL, did: String?, runPolicies: Bool?) async throws -> PresentationResult
    func previewPresentation(request: URL) async throws -> PresentationPreviewResult
    func submitPresentation(
        request: URL,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection]?,
        did: String?,
        runPolicies: Bool?
    ) async throws -> PresentationResult
    func rejectPresentation(
        request: URL,
        error: PresentationErrorCode?,
        errorDescription: String?
    ) async throws -> PresentationResult
    func digitalCredentialCapabilities() -> DigitalCredentialCapabilities
    func previewAnnexCPresentation(
        parsedRequest: AnnexCParsedRequest,
        verifiedOrigin: String,
        selectedRegistryEntryIDs: [String]
    ) async throws -> AnnexCPresentationPreview
    func submitAnnexCPresentation(
        requestID: String,
        verifiedOrigin: String,
        deviceRequestBase64URL: String,
        encryptionInfoBase64URL: String,
        selectedCredentialOptions: [PresentationCredentialSelection]
    ) async throws -> DigitalCredentialResponse
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
        request: URL,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection]?,
        did: String?,
        runPolicies: Bool?
    ) async throws -> PresentationResult {
        throw unavailableError()
    }

    func rejectPresentation(
        request: URL,
        error: PresentationErrorCode?,
        errorDescription: String?
    ) async throws -> PresentationResult {
        throw unavailableError()
    }

    func digitalCredentialCapabilities() -> DigitalCredentialCapabilities {
        DigitalCredentialCapabilities(
            platform: "unavailable",
            platformAvailable: false,
            minimumOSVersion: "iOS 26",
            registrationAvailable: false,
            capabilities: []
        )
    }

    func previewAnnexCPresentation(
        parsedRequest: AnnexCParsedRequest,
        verifiedOrigin: String,
        selectedRegistryEntryIDs: [String]
    ) async throws -> AnnexCPresentationPreview { throw unavailableError() }

    func submitAnnexCPresentation(
        requestID: String,
        verifiedOrigin: String,
        deviceRequestBase64URL: String,
        encryptionInfoBase64URL: String,
        selectedCredentialOptions: [PresentationCredentialSelection]
    ) async throws -> DigitalCredentialResponse { throw unavailableError() }

    private func unavailableError() -> WalletError {
        .internalFailure("WalletCore is only available when the iOS XCFramework is linked.")
    }
}
