import Foundation
import WalletSDK

protocol WalletClient {
    func bootstrap(signingProtection: WalletDemoSigningProtection) async throws -> WalletBootstrapResult
    func signingProtectionAvailability(
        _ signingProtection: WalletDemoSigningProtection
    ) async throws -> WalletDemoSigningProtectionAvailability
    func credentials() async throws -> [Credential]
    func startIssuance(_ request: IssuanceRequest) async throws -> IssuanceSession
    func beginAuthorizationIssuance(sessionID: String) async throws -> IssuanceAuthorization
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
    func deleteCredential(id: String) async throws -> Bool
    func deleteLocalData() async throws
}

final class SDKWalletClient: WalletClient {
    private let configuration: WalletConfiguration
    private var cachedWallet: Wallet?

    init(configuration: WalletConfiguration) {
        self.configuration = configuration
    }

    func bootstrap(signingProtection: WalletDemoSigningProtection) async throws -> WalletBootstrapResult {
        try await wallet().bootstrap(keyUseAuthorizationPolicy: signingProtection.authorizationPolicy)
    }

    func signingProtectionAvailability(
        _ signingProtection: WalletDemoSigningProtection
    ) async throws -> WalletDemoSigningProtectionAvailability {
        switch try await wallet().keyUseAuthorizationPreflight(policy: signingProtection.authorizationPolicy) {
        case .supported: .available
        case .unsupported(.biometricNotEnrolled): .biometricNotEnrolled
        case .unsupported(.biometricUnavailable): .biometricUnavailable
        case .unsupported(.unsupportedCombination): .unsupported
        }
    }

    func credentials() async throws -> [Credential] {
        try await wallet().credentials()
    }

    func startIssuance(_ request: IssuanceRequest) async throws -> IssuanceSession { try await wallet().startIssuance(request) }
    func beginAuthorizationIssuance(sessionID: String) async throws -> IssuanceAuthorization { try await wallet().beginAuthorizationIssuance(sessionID: sessionID) }
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

    func deleteCredential(id: String) async throws -> Bool {
        try await wallet().deleteCredential(id: id)
    }

    func deleteLocalData() async throws {
        try await wallet().deleteLocalData()
        cachedWallet = nil
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

@MainActor
extension SDKWalletClient: ProximityWalletClient {
    func proximityPresentationCapabilities(
        configuration: ProximityPresentationConfiguration
    ) async throws -> ProximityPresentationCapabilities {
        try await wallet().proximityPresentationCapabilities(configuration: configuration)
    }

    func startProximityPresentation(
        configuration: ProximityPresentationConfiguration
    ) async throws -> any DemoProximityPresentationSession {
        try await wallet().startProximityPresentation(configuration: configuration)
    }
}
