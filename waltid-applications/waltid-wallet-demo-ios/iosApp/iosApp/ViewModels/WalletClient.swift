import Foundation
import WalletSDK

protocol WalletClient {
    func bootstrap() async throws -> WalletBootstrapResult
    func credentials() async throws -> [Credential]
    func startIssuance(_ request: IssuanceRequest) async throws -> IssuanceSession
    func continuePreAuthorizedIssuance(sessionID: String, transactionCode: String?) async throws -> IssuanceOutcome
    func continueAuthorizationIssuance(sessionID: String, callbackURI: URL) async throws -> IssuanceOutcome
    func cancelIssuance(sessionID: String) async throws -> IssuanceOutcome
    func resumeDeferredIssuance(deferredCredentialID: String) async throws -> IssuanceOutcome
    func present(request: URL, did: String?) async throws -> PresentationResult
    func previewPresentation(request: URL) async throws -> PresentationPreviewResult
    func submitPresentation(
        previewHandle: PresentationPreviewHandle,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection],
        did: String?
    ) async throws -> PresentationResult
    func rejectPresentation(previewHandle: PresentationPreviewHandle) async throws -> PresentationResult
    func discardPresentationPreview(_ previewHandle: PresentationPreviewHandle) async throws
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

    func startIssuance(_ request: IssuanceRequest) async throws -> IssuanceSession { try await wallet().startIssuance(request) }
    func continuePreAuthorizedIssuance(sessionID: String, transactionCode: String?) async throws -> IssuanceOutcome { try await wallet().continuePreAuthorizedIssuance(sessionID: sessionID, transactionCode: transactionCode) }
    func continueAuthorizationIssuance(sessionID: String, callbackURI: URL) async throws -> IssuanceOutcome { try await wallet().continueAuthorizationIssuance(sessionID: sessionID, callbackURI: callbackURI) }
    func cancelIssuance(sessionID: String) async throws -> IssuanceOutcome { try await wallet().cancelIssuance(sessionID: sessionID) }
    func resumeDeferredIssuance(deferredCredentialID: String) async throws -> IssuanceOutcome { try await wallet().resumeDeferredIssuance(deferredCredentialID: deferredCredentialID) }

    func present(request: URL, did: String?) async throws -> PresentationResult {
        try await wallet().present(request: request, did: did)
    }

    func previewPresentation(request: URL) async throws -> PresentationPreviewResult {
        try await wallet().previewPresentation(request: request)
    }

    func submitPresentation(
        previewHandle: PresentationPreviewHandle,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection],
        did: String?
    ) async throws -> PresentationResult {
        try await wallet().submitPresentation(
            previewHandle: previewHandle,
            selectedCredentialOptions: selectedCredentialOptions,
            selectedDisclosureOptions: selectedDisclosureOptions,
            did: did
        )
    }

    func rejectPresentation(previewHandle: PresentationPreviewHandle) async throws -> PresentationResult {
        try await wallet().rejectPresentation(previewHandle: previewHandle)
    }

    func discardPresentationPreview(_ previewHandle: PresentationPreviewHandle) async throws {
        try await wallet().discardPresentationPreview(previewHandle)
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
