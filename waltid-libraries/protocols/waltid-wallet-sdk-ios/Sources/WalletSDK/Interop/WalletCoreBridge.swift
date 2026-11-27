import Foundation

@available(macOS 10.15, *)
protocol WalletCoreBridge: Sendable {
    var events: AsyncStream<WalletEvent> { get }
    var credentialStatuses: AsyncThrowingStream<CredentialStatusSnapshot, Error> { get }
    func credentialStatusSnapshot() async throws -> CredentialStatusSnapshot
    func credentialStatusMaintenanceEnrollment() async throws -> CredentialStatusMaintenanceEnrollment?
    func setCredentialStatusMaintenance(_ preference: CredentialStatusMaintenancePreference) async throws -> CredentialStatusMaintenanceEnrollment?
    func reconcileCredentialStatusMaintenance(automaticChecks: Bool, resumePaused: Bool) async throws -> CredentialStatusMaintenanceEnrollment?
    func invalidateCredentialStatusSnapshot() async throws
    func acknowledgeCredentialStatusNotice(credentialID: String, version: String) async throws
    func refreshCredentialStatuses(ids: [String]?, force: Bool) async throws -> [CredentialStatusRefreshResult]
    func close() async throws
    var signingIdentityCore: any SigningIdentityCore { get }

    func keyUseAuthorizationPreflight(
        keyType: WalletKeyType,
        policy: WalletKeyUseAuthorizationPolicy
    ) async throws -> WalletKeyUseAuthorizationPreflight
    func createIssuanceHolderKeys(count: Int, keyType: WalletKeyType, didMethod: String, policy: WalletKeyUseAuthorizationPolicy) async throws -> [IssuanceHolderBinding]
    func listDeferredIssuance() async throws -> [DeferredCredential]
    func startIssuance(request: IssuanceRequest) async throws -> IssuanceOfferReview
    func resumeIssuance(id: IssuanceSessionID) async throws -> IssuanceOfferReview?
    func beginAuthorizationIssuance(sessionID: String, registration: IssuanceClientRegistration, credentials: [IssuanceCredentialSelection]?) async throws -> IssuanceAuthorization
    func continuePreAuthorizedIssuance(sessionID: String, clientID: String?, transactionCode: String?, credentials: [IssuanceCredentialSelection]?) async throws -> IssuanceOutcome
    func completeAuthorization(_ authorizationID: String, callback: URL) async throws -> IssuanceOutcome
    func cancelIssuance(sessionID: String) async throws -> IssuanceOutcome
    func resumeDeferredIssuance(deferredCredentialID: String) async throws -> IssuanceOutcome
    func credentials() async throws -> [Credential]
    func deleteCredential(id: String) async throws -> Bool
    func deleteLocalData() async throws
    func previewPresentation(request: URL) async throws -> PresentationPreviewResult
    func retainPresentation(previewHandle: PresentationPreviewHandle, selection: PresentationSelection, did: String?, owner: Wallet) async throws -> PresentationReview
    func rejectPresentation(
        previewHandle: PresentationPreviewHandle,
        error: PresentationErrorCode?,
        errorDescription: String?
    ) async throws -> PresentationResult
    func discardPresentationPreview(_ previewHandle: PresentationPreviewHandle) async throws
    func proximityPresentationCapabilities(
        configuration: ProximityConfiguration
    ) async throws -> ProximityCapabilities
    func startProximityPresentation(
        configuration: ProximityConfiguration
    ) async throws -> any ProximitySessionBridge
    func digitalCredentialCapabilities() -> DigitalCredentialCapabilities
    func previewAnnexCPresentation(
        parsedRequest: AnnexCParsedRequest,
        verifiedOrigin: String,
        selectedRegistryEntryIDs: [String]
    ) async throws -> AnnexCPresentationPreview
    func submitStatusReviewedAnnexCPresentation(_ submission: AnnexCSubmission, reviewedStatus: [String: String]) async throws -> DigitalCredentialResponse
    func submitAnnexCPresentation(_ submission: AnnexCSubmission) async throws -> DigitalCredentialResponse
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



    func keyUseAuthorizationPreflight(
        keyType: WalletKeyType,
        policy: WalletKeyUseAuthorizationPolicy
    ) async throws -> WalletKeyUseAuthorizationPreflight {
        throw unavailableError()
    }

    func createIssuanceHolderKeys(count: Int, keyType: WalletKeyType, didMethod: String, policy: WalletKeyUseAuthorizationPolicy) async throws -> [IssuanceHolderBinding] {
        throw unavailableError()
    }

    func listDeferredIssuance() async throws -> [DeferredCredential] { throw unavailableError() }

    func startIssuance(request: IssuanceRequest) async throws -> IssuanceOfferReview {
        throw unavailableError()
    }

    func beginAuthorizationIssuance(sessionID: String, registration: IssuanceClientRegistration, credentials: [IssuanceCredentialSelection]?) async throws -> IssuanceAuthorization {
        throw unavailableError()
    }

    func continuePreAuthorizedIssuance(
        sessionID: String,
        clientID: String?,
        transactionCode: String?,
        credentials: [IssuanceCredentialSelection]?
    ) async throws -> IssuanceOutcome {
        throw unavailableError()
    }

    func completeAuthorization(_ authorizationID: String, callback: URL) async throws -> IssuanceOutcome {
        throw unavailableError()
    }

    func cancelIssuance(sessionID: String) async throws -> IssuanceOutcome {
        throw unavailableError()
    }

    func resumeDeferredIssuance(deferredCredentialID: String) async throws -> IssuanceOutcome {
        throw unavailableError()
    }

    var credentialStatuses: AsyncThrowingStream<CredentialStatusSnapshot, Error> {
        AsyncThrowingStream { $0.finish(throwing: unavailableError()) }
    }
    func credentialStatusSnapshot() async throws -> CredentialStatusSnapshot { throw unavailableError() }
    func refreshCredentialStatuses(ids: [String]?, force: Bool) async throws -> [CredentialStatusRefreshResult] { throw unavailableError() }
    func close() async throws { }

    func credentials() async throws -> [Credential] {
        throw unavailableError()
    }

    func deleteCredential(id: String) async throws -> Bool {
        throw unavailableError()
    }

    func deleteLocalData() async throws {
        throw unavailableError()
    }

    func previewPresentation(request: URL) async throws -> PresentationPreviewResult {
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

    func proximityPresentationCapabilities(
        configuration: ProximityConfiguration
    ) async throws -> ProximityCapabilities {
        throw unavailableError()
    }

    func startProximityPresentation(
        configuration: ProximityConfiguration
    ) async throws -> any ProximitySessionBridge {
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

    func submitAnnexCPresentation(_ submission: AnnexCSubmission) async throws -> DigitalCredentialResponse { throw unavailableError() }

    private func unavailableError() -> WalletError {
        .internalFailure("WalletCore is only available when the iOS XCFramework is linked.")
    }
}

@available(macOS 10.15, *)
extension WalletCoreBridge {
    func credentialStatusMaintenanceEnrollment() async throws -> CredentialStatusMaintenanceEnrollment? { nil }
    func setCredentialStatusMaintenance(_ preference: CredentialStatusMaintenancePreference) async throws -> CredentialStatusMaintenanceEnrollment? { nil }
    func reconcileCredentialStatusMaintenance(automaticChecks: Bool, resumePaused: Bool) async throws -> CredentialStatusMaintenanceEnrollment? { nil }
    func invalidateCredentialStatusSnapshot() async throws {}
    func acknowledgeCredentialStatusNotice(credentialID: String, version: String) async throws {}

    var signingIdentityCore: any SigningIdentityCore { UnavailableSigningIdentityCore() }
}

extension WalletCoreBridge {
    func submitStatusReviewedAnnexCPresentation(_ submission: AnnexCSubmission, reviewedStatus: [String: String]) async throws -> DigitalCredentialResponse {
        throw WalletError.internalFailure("Status-reviewed Annex C presentation is unavailable")
    }
}

extension WalletCoreBridge {
    func resumeIssuance(id: IssuanceSessionID) async throws -> IssuanceOfferReview? {
        throw WalletError.internalFailure("Issuance session storage is unavailable")
    }


    func retainPresentation(previewHandle: PresentationPreviewHandle, selection: PresentationSelection, did: String?, owner: Wallet) async throws -> PresentationReview {
        throw WalletError.internalFailure("Retained presentation review is unavailable")
    }
}
