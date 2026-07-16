import Foundation
import WalletSDK

protocol WalletClient {
    func bootstrap() async throws -> WalletBootstrapResult
    func credentials() async throws -> [Credential]
    func receive(offer: URL) async throws -> [String]
    func present(request: URL, did: String?) async throws -> PresentationResult
    func previewPresentation(request: URL) async throws -> PresentationPreview
    func submitPresentation(
        request: URL,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection],
        did: String?
    ) async throws -> PresentationResult
}

final class SDKWalletClient: WalletClient {
    private let configuration: WalletConfiguration
    private var cachedWallet: Wallet?

    init(configuration: WalletConfiguration) {
        self.configuration = configuration
    }

    func bootstrap() async throws -> WalletBootstrapResult {
        try await wallet().bootstrap()
    }

    func credentials() async throws -> [Credential] {
        try await wallet().credentials()
    }

    func receive(offer: URL) async throws -> [String] {
        try await wallet().receive(offer: offer)
    }

    func present(request: URL, did: String?) async throws -> PresentationResult {
        try await wallet().present(request: request, did: did)
    }

    func previewPresentation(request: URL) async throws -> PresentationPreview {
        try await wallet().previewPresentation(request: request)
    }

    func submitPresentation(
        request: URL,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection],
        did: String?
    ) async throws -> PresentationResult {
        try await wallet().submitPresentation(
            request: request,
            selectedCredentialOptions: selectedCredentialOptions,
            selectedDisclosureOptions: selectedDisclosureOptions,
            did: did
        )
    }

    private func wallet() async throws -> Wallet {
        if let cachedWallet {
            return cachedWallet
        }

        let wallet = try await Wallet(configuration: configuration)
        cachedWallet = wallet
        return wallet
    }
}
