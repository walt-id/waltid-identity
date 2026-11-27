import Foundation
import WalletSDK

protocol WalletClient {
    var supportsClaimReview: Bool { get }
    func reviewPresentation(
        previewHandle: PresentationPreviewHandle,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection],
        did: String?
    ) async throws -> PreparedPresentationReview
    func credentialStatusUpdates() async throws -> AsyncThrowingStream<CredentialStatusSnapshot, Error>
    func credentialStatusSnapshot() async throws -> CredentialStatusSnapshot?
    func reconcileBackgroundStatus(automaticChecks: Bool, resumePaused: Bool) async throws -> CredentialStatusMaintenanceEnrollment?
    func setBackgroundStatusPreference(_ preference: CredentialStatusMaintenancePreference) async throws -> CredentialStatusMaintenanceEnrollment?
    func invalidateCredentialStatusSnapshot() async throws
    func refreshCredentialStatuses(ids: [String]?, force: Bool) async throws -> [CredentialStatusRefreshResult]
    func acknowledgeCredentialStatusNotice(credentialID: String, version: String) async throws
    func submitStatusReviewedPresentation(
        previewHandle: PresentationPreviewHandle, selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection], reviewedStatus: [String: String],
        did: String?, paymentConsentRevision: String?
    ) async throws -> PresentationResult

    func signingIdentityManager() async throws -> SigningIdentityManager?
    func bootstrap(signingProtection: WalletDemoSigningProtection) async throws -> WalletDemoBootstrapResult
    func signingProtectionAvailability(
        _ signingProtection: WalletDemoSigningProtection
    ) async throws -> WalletDemoSigningProtectionAvailability
    func credentials() async throws -> [Credential]
    func listDeferredIssuance() async throws -> [DeferredCredential]
    func startIssuance(_ request: IssuanceRequest) async throws -> IssuanceSession
    func beginAuthorizationIssuance(sessionID: String, credentials: [IssuanceCredentialSelection]?) async throws -> IssuanceAuthorization
    func continuePreAuthorizedIssuance(sessionID: String, transactionCode: String?, credentials: [IssuanceCredentialSelection]?) async throws -> IssuanceOutcome
    func continueAuthorizationIssuance(sessionID: String, callbackURI: URL) async throws -> IssuanceOutcome
    func approveSigning(sessionID: String) async throws -> IssuanceOutcome
    func declineSigning(sessionID: String) async throws -> IssuanceOutcome
    func cancelIssuance(sessionID: String) async throws -> IssuanceOutcome
    func rejectIssuedCredential(
        notificationID: String,
        accessToken: String,
        credentialIssuerBaseURL: URL?,
        notificationEndpoint: URL?,
        eventDescription: String?
    ) async throws
    func resumeDeferredIssuance(deferredCredentialID: String) async throws -> IssuanceOutcome
    func previewPresentation(request: URL) async throws -> PresentationPreviewResult
    func preparePaymentConsent(
        previewHandle: PresentationPreviewHandle,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection],
        did: String?
    ) async throws -> PaymentConsent?
    func submitPresentation(
        previewHandle: PresentationPreviewHandle,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection],
        did: String?,
        paymentConsentRevision: String?
    ) async throws -> PresentationResult
    func rejectPresentation(previewHandle: PresentationPreviewHandle) async throws -> PresentationResult
    func discardPresentationPreview(_ previewHandle: PresentationPreviewHandle) async throws
    func deleteCredential(id: String) async throws -> Bool
    func deleteLocalData() async throws
}

extension WalletClient {
    func approveSigning(sessionID: String) async throws -> IssuanceOutcome { throw WalletError.invalidInput("Signing approval unavailable") }
    func declineSigning(sessionID: String) async throws -> IssuanceOutcome { throw WalletError.invalidInput("Signing decline unavailable") }
    func reconcileBackgroundStatus(automaticChecks: Bool, resumePaused: Bool) async throws -> CredentialStatusMaintenanceEnrollment? { nil }
    func setBackgroundStatusPreference(_ preference: CredentialStatusMaintenancePreference) async throws -> CredentialStatusMaintenanceEnrollment? { nil }
    func invalidateCredentialStatusSnapshot() async throws {}

    var supportsClaimReview: Bool { false }
    func reviewPresentation(
        previewHandle: PresentationPreviewHandle,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection],
        did: String?
    ) async throws -> PreparedPresentationReview {
        throw WalletError.invalidInput("Effective review is unavailable")
    }
    func credentialStatusUpdates() async throws -> AsyncThrowingStream<CredentialStatusSnapshot, Error> { AsyncThrowingStream { $0.finish() } }
    func credentialStatusSnapshot() async throws -> CredentialStatusSnapshot? { nil }
    func refreshCredentialStatuses(ids: [String]?, force: Bool) async throws -> [CredentialStatusRefreshResult] { [] }
    func acknowledgeCredentialStatusNotice(credentialID: String, version: String) async throws {}
    func submitStatusReviewedPresentation(
        previewHandle: PresentationPreviewHandle, selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection], reviewedStatus: [String: String],
        did: String?, paymentConsentRevision: String?
    ) async throws -> PresentationResult {
        if let snapshot = try await credentialStatusSnapshot(), !selectedCredentialOptions.allSatisfy({ selection in
            snapshot.credentials.first { $0.credentialID == selection.credentialID }?.value.presentation.advisoryRevision == reviewedStatus[selection.credentialID]
        }) { throw WalletError.credentialStatusReviewChanged }
        return try await submitPresentation(previewHandle: previewHandle, selectedCredentialOptions: selectedCredentialOptions,
            selectedDisclosureOptions: selectedDisclosureOptions, did: did, paymentConsentRevision: paymentConsentRevision)
    }

    func signingIdentityManager() async throws -> SigningIdentityManager? { nil }
    func rejectIssuedCredential(
        notificationID: String,
        accessToken: String,
        credentialIssuerBaseURL: URL?,
        notificationEndpoint: URL?,
        eventDescription: String?
    ) async throws {}
}

final class SDKWalletClient: WalletClient {
    var supportsClaimReview: Bool { true }
    func reviewPresentation(
        previewHandle: PresentationPreviewHandle,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection],
        did: String?
    ) async throws -> PreparedPresentationReview {
        try await wallet().reviewPresentation(previewHandle: previewHandle, selectedCredentialOptions: selectedCredentialOptions,
            selectedDisclosureOptions: selectedDisclosureOptions, did: did)
    }
    private let configuration: WalletConfiguration
    private var cachedWallet: Wallet?

    init(configuration: WalletConfiguration) {
        self.configuration = configuration
    }

    func signingIdentityManager() async throws -> SigningIdentityManager? { try await wallet().signingIdentity }

    func bootstrap(signingProtection: WalletDemoSigningProtection) async throws -> WalletDemoBootstrapResult {
        let service = try await wallet().signingIdentity
        guard case .active(let identity) = try await service.state() else {
            throw WalletError.invalidInput("Select a signing identity before opening the wallet")
        }
        return WalletDemoBootstrapResult(keyID: identity.keyID, did: identity.did,
            publicJWK: identity.publicJWK, keyUseAuthorizationPolicy: identity.authorization)
    }

    func signingProtectionAvailability(
        _ signingProtection: WalletDemoSigningProtection
    ) async throws -> WalletDemoSigningProtectionAvailability {
        switch try await wallet().keyUseAuthorizationPreflight(policy: signingProtection.authorizationPolicy) {
        case .supported: .available
        case .unsupported(.biometricNotEnrolled): .biometricNotEnrolled
        case .unsupported(.deviceCredentialNotSet): .deviceCredentialNotSet
        case .unsupported(.biometricUnavailable): .biometricUnavailable
        case .unsupported(.unsupportedCombination): .unsupported
        }
    }

    func credentialStatusUpdates() async throws -> AsyncThrowingStream<CredentialStatusSnapshot, Error> { try await wallet().credentialStatuses }

    func reconcileBackgroundStatus(automaticChecks: Bool, resumePaused: Bool) async throws -> CredentialStatusMaintenanceEnrollment? {
        try await cachedWallet?.reconcileCredentialStatusMaintenance(automaticChecks: automaticChecks, resumePaused: resumePaused)
    }

    func setBackgroundStatusPreference(_ preference: CredentialStatusMaintenancePreference) async throws -> CredentialStatusMaintenanceEnrollment? {
        try await cachedWallet?.setCredentialStatusMaintenance(preference)
    }

    func invalidateCredentialStatusSnapshot() async throws {
        try await cachedWallet?.invalidateCredentialStatusSnapshot()
    }

    func credentialStatusSnapshot() async throws -> CredentialStatusSnapshot? { try await wallet().credentialStatusSnapshot() }
    func refreshCredentialStatuses(ids: [String]?, force: Bool) async throws -> [CredentialStatusRefreshResult] { try await wallet().refreshCredentialStatuses(ids: ids, force: force) }
    func acknowledgeCredentialStatusNotice(credentialID: String, version: String) async throws {
        try await cachedWallet?.acknowledgeCredentialStatusNotice(credentialID: credentialID, version: version)
    }
    func submitStatusReviewedPresentation(
        previewHandle: PresentationPreviewHandle, selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection], reviewedStatus: [String: String],
        did: String?, paymentConsentRevision: String?
    ) async throws -> PresentationResult {
        try await wallet().submitStatusReviewedPresentation(previewHandle: previewHandle,
            selectedCredentialOptions: selectedCredentialOptions, selectedDisclosureOptions: selectedDisclosureOptions,
            reviewedStatus: reviewedStatus, did: did, paymentConsentRevision: paymentConsentRevision)
    }

    func credentials() async throws -> [Credential] {
        try await wallet().credentials()
    }

    func listDeferredIssuance() async throws -> [DeferredCredential] { try await wallet().listDeferredIssuance() }
    func startIssuance(_ request: IssuanceRequest) async throws -> IssuanceSession { try await wallet().startIssuance(request) }
    func beginAuthorizationIssuance(sessionID: String, credentials: [IssuanceCredentialSelection]?) async throws -> IssuanceAuthorization { try await wallet().beginAuthorizationIssuance(sessionID: sessionID, credentials: credentials) }
    func continuePreAuthorizedIssuance(sessionID: String, transactionCode: String?, credentials: [IssuanceCredentialSelection]?) async throws -> IssuanceOutcome { try await wallet().continuePreAuthorizedIssuance(sessionID: sessionID, transactionCode: transactionCode, credentials: credentials) }
    func continueAuthorizationIssuance(sessionID: String, callbackURI: URL) async throws -> IssuanceOutcome { try await wallet().continueAuthorizationIssuance(sessionID: sessionID, callbackURI: callbackURI) }
    func approveSigning(sessionID: String) async throws -> IssuanceOutcome { try await wallet().retryIssuanceSigning(sessionID: sessionID) }
    func declineSigning(sessionID: String) async throws -> IssuanceOutcome { try await wallet().declineIssuanceSigning(sessionID: sessionID) }
    func cancelIssuance(sessionID: String) async throws -> IssuanceOutcome { try await wallet().cancelIssuance(sessionID: sessionID) }
    func rejectIssuedCredential(
        notificationID: String,
        accessToken: String,
        credentialIssuerBaseURL: URL?,
        notificationEndpoint: URL?,
        eventDescription: String?
    ) async throws {
        try await wallet().rejectIssuedCredential(
            notificationID: notificationID,
            accessToken: accessToken,
            credentialIssuerBaseURL: credentialIssuerBaseURL,
            notificationEndpoint: notificationEndpoint,
            eventDescription: eventDescription
        )
    }
    func resumeDeferredIssuance(deferredCredentialID: String) async throws -> IssuanceOutcome { try await wallet().resumeDeferredIssuance(deferredCredentialID: deferredCredentialID) }

    func previewPresentation(request: URL) async throws -> PresentationPreviewResult {
        try await wallet().previewPresentation(request: request)
    }

    func preparePaymentConsent(
        previewHandle: PresentationPreviewHandle,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection],
        did: String?
    ) async throws -> PaymentConsent? {
        try await wallet().preparePaymentConsent(previewHandle: previewHandle, selectedCredentialOptions: selectedCredentialOptions,
            selectedDisclosureOptions: selectedDisclosureOptions, did: did)
    }

    func submitPresentation(
        previewHandle: PresentationPreviewHandle,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection],
        did: String?,
        paymentConsentRevision: String?
    ) async throws -> PresentationResult {
        try await wallet().submitPresentation(
            previewHandle: previewHandle,
            selectedCredentialOptions: selectedCredentialOptions,
            selectedDisclosureOptions: selectedDisclosureOptions,
            did: did,
            paymentConsentRevision: paymentConsentRevision
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
        configuration: ProximityConfiguration
    ) async throws -> ProximityCapabilities {
        try await wallet().proximityPresentationCapabilities(configuration: configuration)
    }

    func startProximityPresentation(
        configuration: ProximityConfiguration
    ) async throws -> any DemoProximityPresentationSession {
        try await wallet().startProximityPresentation(configuration: configuration)
    }
}

/// Result of bootstrapping wallet key material and DID state.
struct WalletDemoBootstrapResult: Equatable, Sendable {
    /// Identifier of the created or selected wallet key.
    let keyID: String

    /// DID created for the wallet.
    let did: String

    /// Public JWK of ``keyID`` as a JSON object string. Private material is never included.
    let publicJWK: String

    /// Immutable authorization policy of the persisted signing key.
    let keyUseAuthorizationPolicy: WalletKeyUseAuthorizationPolicy

    /// Creates a bootstrap result.
    ///
    /// - Parameters:
    ///   - keyID: Identifier of the created or selected wallet key.
    ///   - did: DID created for the wallet.
    ///   - publicJWK: Public JWK of the wallet key as a JSON object string.
    ///   - keyUseAuthorizationPolicy: Authorization required for private-key use.
    init(
        keyID: String,
        did: String,
        publicJWK: String,
        keyUseAuthorizationPolicy: WalletKeyUseAuthorizationPolicy
    ) {
        self.keyID = keyID
        self.did = did
        self.publicJWK = publicJWK
        self.keyUseAuthorizationPolicy = keyUseAuthorizationPolicy
    }
}
