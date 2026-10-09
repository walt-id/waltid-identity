import Foundation
import Combine
import WalletDemoIdentityDocumentSupport
import WalletDemoSharingUI
import WalletSDK
import WalletSDKKeychainRecovery

enum WalletTab: Hashable {
    case credentials
    case receive
    case present
}

enum WalletStatusKind: Hashable {
    case busy
    case info
    case success
    case error
}

private struct WalletStatusBannerModel {
    let message: String
    let kind: WalletStatusKind
    let occurrenceId: UInt64

    var key: String { "\(kind):\(message):\(occurrenceId)" }
}

private enum WalletDeepLinkScheme: String {
    case credentialOffer = "openid-credential-offer"
    case presentationRequest = "openid4vp"
    case authorizationCallback = "openid"
}

private enum WalletStatusText {
    static let startingWallet = "Starting wallet..."
    static let walletReady = "Wallet ready"
    static let resolvingCredentialOffer = "Resolving credential offer..."
    static let reviewCredentialOffer = "Review credential offer"
    static let credentialOfferDeclined = "Credential offer declined"
    static let receivingCredential = "Receiving credential..."
    static let resolvingPresentation = "Resolving presentation..."
    static let presentingCredential = "Presenting credential..."
    static let decliningPresentation = "Declining presentation..."
    static let bootstrappingWallet = "Bootstrapping wallet..."
    static let reviewPresentationRequest = "Review presentation request"
    static let reviewPresentationError = "Review presentation error"
    static let presentationSent = "Presentation sent"
    static let verifierNotified = "Verifier notified"
    static let presentationReviewCancelled = "Presentation review cancelled"
    static let presentationRejected = "Presentation rejected"
    static let presentationFinishedWithoutVerifierConfirmation = "Presentation finished without verifier confirmation"
    static let rejectionFinishedWithoutVerifierConfirmation = "Rejection finished without verifier confirmation"
    static let receiveFailed = "Receive failed"
    static let previewFailed = "Preview failed"
    static let presentFailed = "Present failed"
    static let rejectFailed = "Reject failed"
    static let presentationContinuationFailed = "Could not deliver the verifier response"
    static let bootstrapFailed = "Bootstrap failed"
    static let resetWalletFailed = "Reset wallet failed"
    static let signingProtectionChangeFailed = "Signing protection change failed"
    static let deleteCredentialFailed = "Delete credential failed"
    static let invalidOfferURL = "invalid offer URL"
    static let invalidRequestURL = "invalid request URL"
    static let selectCredentialForEveryRequest = "select a credential for every requested credential"
    static let biometricUnlockNotAuthorized = "Biometric unlock was not authorized. Use the PIN instead."
    static let receivedCredentialsUnavailable = "received credentials are not available locally"
    static let transactionDataProfilesUnavailable = "Transaction data profiles could not be loaded; transaction-data presentation requests will be rejected."

    static func receivedCredentials(_ count: Int) -> String {
        "Received \(count) credential(s)"
    }

    static func issuanceProgress(saved: Int, pending: Int, notAttempted: Int? = nil) -> String {
        let progress = "Saved credentials: \(saved). Pending targets: \(pending)."
        guard let notAttempted else { return progress }
        return progress + " Failed targets: 1. Not attempted: \(notAttempted)."
    }

    static func issuanceFailure(_ error: IssuanceFailure, saved: Int, pending: Int) -> String {
        guard saved > 0 || pending > 0 || error.targetFailure != nil else { return error.message }
        return error.message + "\n" + issuanceProgress(saved: saved, pending: pending,
            notAttempted: error.targetFailure?.notAttempted.count)
    }

    static func failure(_ prefix: String, _ reason: String) -> String {
        "\(prefix): \(reason)"
    }

    static func failure(_ prefix: String, _ error: Error) -> String {
        failure(prefix, error.localizedDescription)
    }
}

@MainActor
class WalletViewModel: ObservableObject {
    let proximityPresentation: ProximityPresentationViewModel
    let readerTrustSettings: DemoReaderTrustSettingsController
    @Published var identityScreen: WalletIdentityScreenModel?
    @Published var isReady = false
    @Published var did = ""
    @Published var keyID = ""
    @Published var publicJWK = ""
    @Published var credentials: [Credential] = []
    @Published var statusMessage = WalletStatusText.startingWallet
    @Published var isLoading = false
    @Published var isError = false
    @Published private(set) var signingProtectionMode: WalletDemoSigningProtectionMode
    @Published var selectedSigningProtection: WalletDemoSigningProtection
    @Published private(set) var appliedSigningProtection: WalletDemoSigningProtection?
    @Published private(set) var pendingSigningProtectionChange: WalletDemoSigningProtection?
    @Published private(set) var signingProtectionReprovisionTarget: WalletDemoSigningProtection?
    @Published private(set) var isChangingSigningProtection = false
    @Published var signingProtectionError: String?
    @Published private(set) var biometricSigningAvailability: WalletDemoSigningProtectionAvailability? = nil
    @Published private(set) var signingProtectionWarning: String? = nil
    @Published var offerUrl = "" {
        didSet {
            guard offerUrl != oldValue else { return }
            receiveTask?.cancel()
            cancelIssuanceIfPresent()
            txCode = ""
            offerPreview = nil
        }
    }
    @Published var txCode = ""
    @Published var presentationRequestUrl = ""
    @Published private(set) var presentationReview: PresentationPreviewResult? {
        didSet {
            paymentConsentTask?.cancel()
            paymentReview = .notRequired
        }
    }
    @Published var selectedPresentationCredentialOptions: Set<PresentationCredentialSelection> = []
    @Published var selectedPresentationDisclosureOptions: Set<PresentationDisclosureSelection> = []
    @Published var externalFlow: WalletExternalFlow?
    @Published var incomingLinkNotice: String?
    @Published private(set) var flowIsCommitting = false
    @Published var selectedTab: WalletTab = .credentials
    @Published var issuanceCopyCounts: [String: Int] = [:]
    @Published var offerPreview: IssuanceOfferPreview?
    @Published private(set) var authorizationRequestURL: URL?
    @Published var deferredCredentials: [DeferredCredential] = []
    @Published var issuanceReceipt: IssuanceReceipt?
    @Published var lastReceivedCredentialIDs: [String] = []
    @Published var receiveCompleted = false
    @Published var presentationCompleted = false
    @Published var receiveNavigationResetKey = 0
    @Published var presentationNavigationResetKey = 0
    @Published var inputFocusResetKey = 0
    @Published var transactionDataProfilesWarning: String?
    @Published var statusExpanded = false
    let access: WalletAccessController
    private var accessObservation: AnyCancellable?
    var auth: WalletAuthState { get { access.auth } set { access.auth = newValue } }
    var pin: String { get { access.pin } set { access.pin = newValue } }
    var pinConfirmation: String { get { access.confirmation } set { access.confirmation = newValue } }
    var pinSetupStep: PinSetupStep { access.step }
    var biometricSigningRecoveryAvailability: DemoBiometricAvailability {
        biometricSigningAvailability?.recoveryAvailability(unlock: access.biometricAvailability) ?? .unavailable
    }
    var isBiometricUnlockAvailable: Bool { access.biometricAvailable }
    @Published var showDcApiPresentationPreview: Bool = DemoSharingSettings.showDcApiPresentationPreview(
        appGroupIdentifier: IdentityDocumentSharedConfiguration.appGroupIdentifier
    ) {
        didSet {
            DemoSharingSettings.setShowDcApiPresentationPreview(
                showDcApiPresentationPreview,
                appGroupIdentifier: IdentityDocumentSharedConfiguration.appGroupIdentifier
            )
        }
    }
    @Published var proximityTransportProfile: WalletDemoProximityTransportProfile =
        DemoSharingSettings.proximityTransportProfile(
            appGroupIdentifier: IdentityDocumentSharedConfiguration.appGroupIdentifier
        ) {
            didSet {
                DemoSharingSettings.setProximityTransportProfile(
                    proximityTransportProfile,
                    appGroupIdentifier: IdentityDocumentSharedConfiguration.appGroupIdentifier
                )
                proximityPresentation.refreshPreferences()
            }
        }
    @Published var proximityApprovalMode: WalletDemoProximityApprovalMode = DemoSharingSettings.proximityApprovalMode(
        appGroupIdentifier: IdentityDocumentSharedConfiguration.appGroupIdentifier
    ) {
        didSet {
            DemoSharingSettings.setProximityApprovalMode(proximityApprovalMode,
                appGroupIdentifier: IdentityDocumentSharedConfiguration.appGroupIdentifier)
            proximityPresentation.refreshPreferences()
        }
    }
    var pinError: String? { access.pinError }
    var isAuthenticating: Bool { access.isBusy }
    @Published private(set) var pendingPresentationContinuationURL: URL?
    @Published private(set) var pendingPresentationFormPostHTML: String?
    private var statusTab: WalletTab?
    private var statusOccurrenceId: UInt64 = 0
    private var statusDismissedKey: String?
    private var statusHideTask: Task<Void, Never>?
    private var receiveTask: Task<Void, Never>?
    private var issuanceSession: IssuanceSession?
    private var pendingPresentationSuccessMessage: String?
    private var presentationTask: Task<Void, Never>?
    private var paymentConsentTask: Task<Void, Never>?
    @Published var paymentReview: PaymentReviewState = .notRequired
    private var biometricSigningAvailabilityTask: Task<Void, Never>?
    private var foregroundSequence = 0
    private var lastWarnedForegroundSequence: Int?

    var presentationPreview: PresentationPreview? {
        if case .ready(let preview)? = presentationReview { return preview }
        return nil
    }

    var presentationError: PresentationPreviewError? {
        if case .invalid(let error)? = presentationReview { return error }
        return nil
    }

    var receiveUrlEntryEnabled: Bool {
        !isLoading && offerPreview == nil
    }

    var receiveActionEnabled: Bool {
        isReady &&
            !offerUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            receiveUrlEntryEnabled
    }

    var offerReviewEnabled: Bool {
        !isLoading && offerPreview != nil
    }

    var acceptOfferEnabled: Bool {
        offerReviewEnabled && hasValidTransactionCode &&
            (offerPreview?.credentials.contains { (issuanceCopyCounts[$0.configurationID] ?? 1) > 0 } ?? false)
    }

    private var hasValidTransactionCode: Bool {
        guard let requirement = offerPreview?.transactionCode else { return true }
        let normalizedCode = normalizedTransactionCode(txCode, requirement: requirement)
        guard !normalizedCode.isEmpty else { return false }
        guard let length = requirement.length else { return true }
        return normalizedCode.count == length
    }

    var receivedCredentials: [Credential] {
        var credentialsByID: [String: Credential] = [:]
        credentials.forEach { credential in
            credentialsByID[credential.id] = credential
        }
        return lastReceivedCredentialIDs.compactMap { credentialsByID[$0] }
    }

    var presentationUrlEntryEnabled: Bool {
        !isLoading && presentationReview == nil
    }

    var presentationPreviewActionEnabled: Bool {
        isReady &&
            !presentationRequestUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            presentationUrlEntryEnabled
    }

    var presentationReviewEnabled: Bool {
        !isLoading && presentationReview != nil
    }

    /// The review the shared SwiftUI surface renders for a ready OpenID4VP preview.
    var presentationSharingReview: SharingReviewModel? {
        presentationPreview?.sharingReview()
    }

    /// Credentials and disclosures the user has chosen so far.
    var presentationSharingSelection: SharingSelection {
        SharingSelection(
            credentials: selectedPresentationCredentialOptions,
            disclosures: selectedPresentationDisclosureOptions
        )
    }

    var presentationCredentialSelectionComplete: Bool {
        presentationSharingReview?.hasCompleteCredentialSelection(selectedPresentationCredentialOptions) == true
    }

    func statusMessage(for tab: WalletTab) -> String {
        statusApplies(to: tab) ? statusMessage : fallbackStatusMessage(for: tab)
    }

    func statusIsLoading(for tab: WalletTab) -> Bool {
        isLoading && statusApplies(to: tab)
    }

    func statusIsError(for tab: WalletTab) -> Bool {
        isError && statusApplies(to: tab)
    }

    func isStatusVisible(for tab: WalletTab) -> Bool {
        guard let banner = statusBanner(for: tab) else { return false }
        return statusDismissedKey != banner.key
    }

    func statusKind(for tab: WalletTab) -> WalletStatusKind? {
        statusBanner(for: tab)?.kind
    }

    func dismissStatus() {
        guard let banner = statusBanner(for: selectedTab) else { return }
        guard banner.kind == .success || banner.kind == .error else { return }
        statusHideTask?.cancel()
        statusDismissedKey = banner.key
        statusExpanded = false
    }

    func toggleStatusExpanded() {
        guard statusBanner(for: selectedTab)?.kind == .error, isStatusVisible(for: selectedTab) else { return }
        statusExpanded.toggle()
    }

    func deleteCredential(id: String) {
        Task {
            do {
                let removed = try await walletClient.deleteCredential(id: id)
                guard removed else { return }
                credentials = try await walletClient.credentials()
                try await reconcileIdentityDocumentRegistrations()
                if presentationReview != nil {
                    discardPresentationPreviewIfPresent()
                    presentationReview = nil
                    selectedPresentationCredentialOptions = []
                    selectedPresentationDisclosureOptions = []
                    presentationCompleted = false
                    presentationNavigationResetKey += 1
                }
            } catch {
                setError(WalletStatusText.failure(WalletStatusText.deleteCredentialFailed, error), tab: selectedTab)
            }
        }
    }

    func resetWallet() {
        access.cancelAttempt()
        receiveTask?.cancel()
        paymentConsentTask?.cancel()
        presentationTask?.cancel()
        cancelIssuanceIfPresent()
        discardPresentationPreviewIfPresent()
        clearPendingPresentationContinuation()
        Task {
            cancelActiveWalletOperations()
            do {
                await proximityPresentation.closeAndAwait()
                try await walletClient.deleteLocalData()
                identityScreen = nil
                access.reset()
                clearWalletState()
                refreshBiometricSigningAvailability()
                do {
                    try await reconcileIdentityDocumentRegistrations()
                } catch {
                    setError(WalletStatusText.failure(WalletStatusText.resetWalletFailed, error))
                }
            } catch {
                // Cleanup may have removed keys before failing. Keep reset available, but do not
                // present the old identity and credentials as a usable wallet.
                identityScreen = nil
                clearWalletState()
                setError(WalletStatusText.failure(WalletStatusText.resetWalletFailed, error))
            }
        }
    }

    func lock() {
        externalFlow = nil
        selectedTab = .credentials
        access.cancelAttempt()
        proximityPresentation.dismiss()
        receiveTask?.cancel()
        paymentConsentTask?.cancel()
        presentationTask?.cancel()
        cancelIssuanceIfPresent()
        discardPresentationPreviewIfPresent()
        offerUrl = ""
        txCode = ""
        offerPreview = nil
        presentationRequestUrl = ""
        presentationReview = nil
        selectedPresentationCredentialOptions = []
        selectedPresentationDisclosureOptions = []
        lastReceivedCredentialIDs = []
        issuanceReceipt = nil
        receiveCompleted = false
        presentationCompleted = false
        clearPendingPresentationContinuation()
        receiveNavigationResetKey += 1
        presentationNavigationResetKey += 1
        isLoading = false
        isError = false
        statusTab = nil
        statusMessage = isReady ? WalletStatusText.walletReady : WalletStatusText.startingWallet
        statusExpanded = false
        statusHideTask?.cancel()
        access.lock()
    }

    func updatePin(_ value: String) { access.updatePin(value) }
    func updatePinConfirmation(_ value: String) { access.updatePin(value, confirming: true) }
    func editSetupPin() { access.back() }
    func clearPin() { access.clearPin() }
    func submitPin() { access.submitPin() }
    func unlockWithBiometrics(force: Bool = false) { access.unlockWithBiometrics(force: force) }
    func retryBiometricSetup() { access.retryBiometricSetup() }
    func continueWithoutBiometrics() { access.continueWithoutBiometrics() }
    func startPinChange() { access.startPinChange() }
    func cancelPinChange() { access.cancelPinChange() }
    func setBiometricUnlockEnabled(_ enabled: Bool) { access.setBiometricUnlock(enabled) }

    func unlockForTests(pin: String = "1234") {
        updatePin(pin)
        if auth == .setup && pinSetupStep == .confirm { updatePinConfirmation(pin) }
    }

    func promptBiometricUnlockIfNeeded() { access.unlockWithBiometrics() }

    func handleApplicationBecameActive() {
        refreshBiometricAvailability()
        foregroundSequence += 1
        refreshBiometricSigningAvailability(warningSequence: foregroundSequence)
    }

    var isBiometricUnlockEnabled: Bool { access.biometricEnabled }
    var shouldPromptBiometricUnlock: Bool { access.shouldPromptBiometrics }
    func refreshBiometricAvailability() { access.refreshBiometrics() }

    var isBiometricSigningAvailable: Bool {
        biometricSigningAvailability == .available
    }

    func selectSigningProtection(_ protection: WalletDemoSigningProtection) {
        guard auth == .setup,
              signingProtectionMode == .optional,
              !isAuthenticating,
              !protection.requiresBiometrics || isBiometricSigningAvailable else { return }
        selectedSigningProtection = protection
        signingProtectionError = nil
    }

    func dismissSigningProtectionWarning() {
        signingProtectionWarning = nil
    }

    func requestSigningProtectionChange(_ protection: WalletDemoSigningProtection) {
        guard signingProtectionMode.allows(protection), !isChangingSigningProtection, !isLoading else { return }
        guard !protection.requiresBiometrics || isBiometricSigningAvailable else { return }
        if signingProtectionReprovisionTarget != nil {
            reprovisionWallet(
                target: protection,
                previousSelection: selectedSigningProtection,
                recovering: true
            )
            return
        }
        guard protection != appliedSigningProtection else {
            selectedSigningProtection = protection
            signingProtectionError = nil
            return
        }

        let previousSelection = selectedSigningProtection
        selectedSigningProtection = protection
        isChangingSigningProtection = true
        signingProtectionError = nil
        Task {
            defer { isChangingSigningProtection = false }
            guard await validateSigningProtection(protection) else {
                selectedSigningProtection = previousSelection
                return
            }
            if isReady {
                pendingSigningProtectionChange = protection
            } else {
                signingProtectionStore.save(protection)
                bootstrap(signingProtection: protection)
            }
        }
    }

    func cancelSigningProtectionChange() {
        pendingSigningProtectionChange = nil
        selectedSigningProtection = appliedSigningProtection ?? signingProtectionMode.resolve(signingProtectionStore.load())
        signingProtectionError = nil
    }

    func confirmSigningProtectionChange() {
        guard let target = pendingSigningProtectionChange, !isChangingSigningProtection, !isLoading else { return }
        pendingSigningProtectionChange = nil
        reprovisionWallet(
            target: target,
            previousSelection: appliedSigningProtection
                ?? signingProtectionMode.resolve(signingProtectionStore.load()),
            recovering: false
        )
    }

    private func reprovisionWallet(
        target: WalletDemoSigningProtection,
        previousSelection: WalletDemoSigningProtection,
        recovering: Bool
    ) {
        isChangingSigningProtection = true
        signingProtectionError = nil

        Task {
            defer { isChangingSigningProtection = false }
            guard await validateSigningProtection(target) else {
                if recovering {
                    selectedSigningProtection = target
                    signingProtectionReprovisionTarget = target
                    setError(signingProtectionError ?? WalletStatusText.signingProtectionChangeFailed)
                } else {
                    selectedSigningProtection = previousSelection
                }
                return
            }

            cancelActiveWalletOperations()
            signingProtectionStore.save(target)
            selectedSigningProtection = target
            do {
                try await walletClient.deleteLocalData()
                identityScreen = nil
                clearWalletState()
                setLoading(WalletStatusText.bootstrappingWallet)
                try await loadWallet(
                    signingProtection: target,
                    requiredAppliedSigningProtection: target
                )
                signingProtectionReprovisionTarget = nil
                setSuccess(WalletStatusText.walletReady)
            } catch {
                clearWalletState()
                try? await reconcileIdentityDocumentRegistrations()
                selectedSigningProtection = target
                signingProtectionReprovisionTarget = target
                signingProtectionError = WalletStatusText.failure(
                    WalletStatusText.signingProtectionChangeFailed,
                    error
                )
                setError(signingProtectionError ?? WalletStatusText.signingProtectionChangeFailed)
            }
        }
    }

    private let walletClient: any WalletClient
    private let signingProtectionStore: any WalletDemoSigningProtectionStore
    private let identityDocumentRegistrationUpdate: @Sendable () async throws -> Void

    init(
        walletID: String = "default",
        attestationBaseUrl: String? = nil,
        attestationAttesterPath: String? = nil,
        attestationBearerToken: String? = nil,
        attestationHostHeader: String? = nil,
        transactionDataProfilesUrl: String? = nil,
        signingProtectionMode: WalletDemoSigningProtectionMode = .disabled,
        signingProtectionStore: (any WalletDemoSigningProtectionStore)? = nil,
        walletClient: (any WalletClient)? = nil,
        proximityWalletClient: (any ProximityWalletClient)? = nil,
        readerTrustSettingsPersistence: (any DemoReaderTrustSettingsPersistence)? = nil,
        identityDocumentRegistrationUpdate: (@Sendable () async throws -> Void)? = nil,
        pinStore: DemoPinStore? = nil,
        biometricAuthenticator: (any DemoBiometricAuthenticator)? = nil
    ) {
        let resolvedStore = signingProtectionStore ?? UserDefaultsWalletDemoSigningProtectionStore(walletID: walletID)
        let storedProtection = resolvedStore.load()
        let selectedProtection = signingProtectionMode.resolve(storedProtection)
        let transactionDataProfiles: TransactionDataProfilesConfiguration
        if walletClient == nil {
            transactionDataProfiles = Self.resolveTransactionDataProfiles(from: transactionDataProfilesUrl)
        } else {
            transactionDataProfiles = TransactionDataProfilesConfiguration(profiles: [])
        }
        let configuration = WalletConfiguration(
            walletID: walletID,
            attestation: Self.attestationConfiguration(
                baseUrl: attestationBaseUrl,
                attesterPath: attestationAttesterPath,
                bearerToken: attestationBearerToken,
                hostHeader: attestationHostHeader
            ),
            clientIDTrustConfiguration: DemoClientIdTrust.clientIDTrustConfiguration,
            transactionDataProfiles: transactionDataProfiles.profiles,
            paymentCredentialIssuers: [WalletPaymentCredentialIssuer(
                issuer: "https://issuer2.demo.walt.id/openid4vci",
                publicJWKJSON: #"{"kty":"EC","crv":"P-256","x":"G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM","y":"ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"}"#
            )],
            crossProcessAccess: Self.crossProcessAccessConfiguration(),
            defaultKeyUseAuthorizationPolicy: signingProtectionMode.defaultSelection.authorizationPolicy,
            keyUseAuthorizationPrompt: WalletKeyUseAuthorizationPrompt(
                message: "Authorize wallet signing",
                cancelText: "Cancel"
            ),
            signingIdentity: .init(alternativeAuthorizations: signingProtectionMode.alternativeAuthorizations,
                keychain: .init(accessGroup: Self.crossProcessAccessConfiguration().keychainAccessGroup),
                recoveryProviders: [KeychainIdentityRecovery(namespace: "wallet-demo",
                    accessGroup: Self.crossProcessAccessConfiguration().keychainAccessGroup)])
        )
        let resolvedWalletClient = walletClient ?? SDKWalletClient(configuration: configuration)
        self.signingProtectionMode = signingProtectionMode
        selectedSigningProtection = selectedProtection
        appliedSigningProtection = nil
        pendingSigningProtectionChange = nil
        signingProtectionReprovisionTarget = nil
        self.signingProtectionStore = resolvedStore
        self.walletClient = resolvedWalletClient
        let readerTrustSettings = DemoReaderTrustSettingsController(
            persistence: readerTrustSettingsPersistence
                ?? UserDefaultsDemoReaderTrustSettingsPersistence(
                    appGroupIdentifier: IdentityDocumentSharedConfiguration.appGroupIdentifier
                )
        )
        self.readerTrustSettings = readerTrustSettings
        self.proximityPresentation = ProximityPresentationViewModel(
            client: proximityWalletClient
                ?? (resolvedWalletClient as? any ProximityWalletClient)
                ?? UnavailableProximityWalletClient(),
            configurationProvider: {
                try readerTrustSettings.sessionSnapshot().applying(
                    to: DemoSharingSettings.proximityTransportProfile(
                        appGroupIdentifier: IdentityDocumentSharedConfiguration.appGroupIdentifier
                    ).configuration.withApproval(DemoSharingSettings.proximityApprovalMode(
                        appGroupIdentifier: IdentityDocumentSharedConfiguration.appGroupIdentifier
                    ).approval)
                )
            }
        )
        self.identityDocumentRegistrationUpdate = identityDocumentRegistrationUpdate ?? {
            try await Self.defaultIdentityDocumentRegistrationUpdate()
        }
        let accessStore = pinStore ?? (
            walletClient == nil
                ? UserDefaultsDemoPinStore(walletID: walletID)
                : InMemoryDemoPinStore()
        )
        let accessBiometrics = biometricAuthenticator ?? (
            walletClient == nil
                ? LocalAuthenticationBiometricAuthenticator()
                : UnavailableDemoBiometricAuthenticator()
        )
        self.access = WalletAccessController(store: accessStore, biometrics: accessBiometrics)
        transactionDataProfilesWarning = transactionDataProfiles.warning
        access.beforeSetupSave = { [weak self] in
            guard let self else { return }
            let selection = self.signingProtectionMode.resolve(self.selectedSigningProtection)
            self.signingProtectionStore.save(selection)
            self.selectedSigningProtection = selection
        }
        access.onUnlocked = { [weak self] in
            guard let self else { return }
            showBiometricSigningWarningIfNeeded(warningSequence: foregroundSequence > 0 ? foregroundSequence : nil)
            bootstrapIfNeeded()
        }
        accessObservation = access.objectWillChange.sink { [weak self] _ in self?.objectWillChange.send() }
        refreshBiometricAvailability()
        refreshBiometricSigningAvailability()
    }

    private static func resolveTransactionDataProfiles(from urlString: String?) -> TransactionDataProfilesConfiguration {
        guard let trimmed = urlString?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmed.isEmpty,
              let url = URL(string: trimmed) else {
            return transactionDataProfilesUnavailable("TRANSACTION_DATA_PROFILES_URL is not configured")
        }

        let semaphore = DispatchSemaphore(value: 0)
        var fetchResult: Result<[WalletTransactionDataProfile], Error>?
        URLSession.shared.dataTask(with: url) { data, response, error in
            defer { semaphore.signal() }
            if let error {
                fetchResult = .failure(error)
                return
            }

            guard let status = (response as? HTTPURLResponse)?.statusCode else {
                fetchResult = .failure(TransactionDataProfileFetchError.missingResponse)
                return
            }
            guard (200..<300).contains(status) else {
                fetchResult = .failure(TransactionDataProfileFetchError.httpStatus(status))
                return
            }
            guard let data else {
                fetchResult = .failure(TransactionDataProfileFetchError.missingBody)
                return
            }

            do {
                let profiles = try JSONDecoder().decode([RemoteTransactionDataProfile].self, from: data)
                guard !profiles.isEmpty else {
                    fetchResult = .failure(TransactionDataProfileFetchError.emptyProfiles)
                    return
                }
                fetchResult = .success(
                    profiles.map {
                        WalletTransactionDataProfile(
                            type: $0.type,
                            displayName: $0.displayName,
                            fields: $0.fields
                        )
                    }
                )
            } catch {
                fetchResult = .failure(error)
            }
        }.resume()

        guard semaphore.wait(timeout: .now() + 3) == .success else {
            return transactionDataProfilesUnavailable("Timed out fetching transaction data profiles from \(url.absoluteString)")
        }

        switch fetchResult {
        case .success(let profiles):
            return TransactionDataProfilesConfiguration(profiles: profiles)
        case .failure(let error):
            return transactionDataProfilesUnavailable("Could not fetch transaction data profiles from \(url.absoluteString): \(error)")
        case nil:
            return transactionDataProfilesUnavailable("Could not fetch transaction data profiles from \(url.absoluteString)")
        }
    }

    private static func transactionDataProfilesUnavailable(_ reason: String) -> TransactionDataProfilesConfiguration {
        NSLog("[WalletE2E] Transaction data profiles unavailable: \(reason)")
        return TransactionDataProfilesConfiguration(
            profiles: [],
            warning: WalletStatusText.transactionDataProfilesUnavailable
        )
    }

    private struct TransactionDataProfilesConfiguration {
        let profiles: [WalletTransactionDataProfile]
        let warning: String?

        init(profiles: [WalletTransactionDataProfile], warning: String? = nil) {
            self.profiles = profiles
            self.warning = warning
        }
    }

    private struct RemoteTransactionDataProfile: Decodable {
        let type: String
        let displayName: String
        let fields: [String]
    }

    private enum TransactionDataProfileFetchError: Error {
        case emptyProfiles
        case httpStatus(Int)
        case missingBody
        case missingResponse
    }

    func handleDeepLink(_ url: URL) {
        logE2E("Deep link received: \(url.scheme ?? "unknown")")
        switch (url.scheme?.lowercased()).flatMap(WalletDeepLinkScheme.init(rawValue:)) {
        case .credentialOffer: openResolvedLink(url, kind: .offer)
        case .presentationRequest: openResolvedLink(url, kind: .presentation)
        case .authorizationCallback: continueAuthorization(callbackURI: url)
        case nil:
            if WalletLinkKind.classify(url.absoluteString) == .authorizationCallback {
                continueAuthorization(callbackURI: url)
            }
        }
    }

    /// Resolved scanner and external links enter the same authenticated task sheet.
    func openResolvedLink(_ url: URL, kind: WalletLinkKind) {
        if kind == .authorizationCallback { continueAuthorization(callbackURI: url); return }
        let flowKind: WalletExternalFlow.Kind
        switch kind {
        case .offer: flowKind = .offer
        case .presentation: flowKind = .presentation
        default: return
        }
        if externalFlow?.url == url { return }
        guard canAcceptExternalRequest && !proximityPresentation.active else {
            incomingLinkNotice = "Finish the current operation before opening another link."
            return
        }
        resetInputFocus()
        receiveTask?.cancel()
        paymentConsentTask?.cancel()
        presentationTask?.cancel()
        cancelIssuanceIfPresent()
        discardPresentationPreviewIfPresent()
        selectedTab = flowKind == .offer ? .receive : .present
        offerUrl = flowKind == .offer ? url.absoluteString : ""
        presentationRequestUrl = flowKind == .presentation ? url.absoluteString : ""
        txCode = ""
        offerPreview = nil
        lastReceivedCredentialIDs = []
        issuanceReceipt = nil
        receiveCompleted = false
        receiveNavigationResetKey += 1
        presentationReview = nil
        selectedPresentationCredentialOptions = []
        selectedPresentationDisclosureOptions = []
        presentationCompleted = false
        clearPendingPresentationContinuation()
        presentationNavigationResetKey += 1
        resetFlowStatusForIncomingURL()
        externalFlow = .pending(url, flowKind)
    }

    private var canAcceptExternalRequest: Bool {
        !flowIsCommitting && pendingPresentationContinuationURL == nil && pendingPresentationFormPostHTML == nil
    }

    var canDismissExternalFlow: Bool {
        canAcceptExternalRequest && !isAuthenticating && identityScreen?.busy != true
    }

    func prepareExternalFlow() {
        guard case let .pending(url, kind) = externalFlow, auth == .unlocked, isReady, !isLoading else { return }
        externalFlow = .active(url, kind)
        switch kind {
        case .offer: previewOffer()
        case .presentation: previewPresentation()
        }
    }

    @discardableResult func closeExternalFlow() -> Bool {
        guard externalFlow != nil, canDismissExternalFlow else { return false }
        externalFlow = nil
        incomingLinkNotice = nil
        startNewReceiveFlow()
        startNewPresentationFlow()
        selectedTab = .credentials
        return true
    }

    func startNewReceiveFlow() {
        receiveTask?.cancel()
        cancelIssuanceIfPresent()
        resetInputFocus()
        offerUrl = ""
        txCode = ""
        offerPreview = nil
        authorizationRequestURL = nil
        lastReceivedCredentialIDs = []
        issuanceReceipt = nil
        receiveCompleted = false
        receiveNavigationResetKey += 1
        isLoading = false
        isError = false
        statusTab = nil
        statusMessage = WalletStatusText.walletReady
    }

    func startNewPresentationFlow() {
        paymentConsentTask?.cancel()
        presentationTask?.cancel()
        discardPresentationPreviewIfPresent()
        resetInputFocus()
        presentationRequestUrl = ""
        presentationReview = nil
        selectedPresentationCredentialOptions = []
        selectedPresentationDisclosureOptions = []
        presentationCompleted = false
        clearPendingPresentationContinuation()
        presentationNavigationResetKey += 1
        isLoading = false
        isError = false
        statusTab = nil
        statusMessage = WalletStatusText.walletReady
    }

    func previewOffer() {
        resetInputFocus()
        guard !isLoading, offerPreview == nil else { return }
        let trimmedOfferUrl = offerUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let offer = URL(string: trimmedOfferUrl) else {
            setError(WalletStatusText.failure(WalletStatusText.receiveFailed, WalletStatusText.invalidOfferURL), tab: .receive)
            return
        }
        let request = ReceiveRequest(offerURL: offer.absoluteString, navigationResetKey: receiveNavigationResetKey)
        setLoading(WalletStatusText.resolvingCredentialOffer, tab: .receive)
        receiveTask = Task {
            var newSession: IssuanceSession?
            do {
                let session = try await walletClient.startIssuance(
                    IssuanceRequest(
                        offer: offer,
                        redirectURI: URL(string: "openid://")!,
                        did: did.isEmpty ? nil : did
                    )
                )
                newSession = session
                try Task.checkCancellation()
                guard isCurrent(request) else {
                    _ = try? await walletClient.cancelIssuance(sessionID: session.id)
                    return
                }
                issuanceSession = session
                offerPreview = session.offer
                issuanceCopyCounts = Dictionary(uniqueKeysWithValues: session.offer.credentials.map { ($0.configurationID, 1) })
                newSession = nil
                setSuccess(WalletStatusText.reviewCredentialOffer, tab: .receive)
                Task {
                    await prefetchCredentialCardArt(
                        uris: session.offer.credentials.map { $0.backgroundImageURI?.absoluteString }
                    )
                }
            } catch is CancellationError {
                if let newSession {
                    _ = try? await walletClient.cancelIssuance(sessionID: newSession.id)
                }
                return
            } catch {
                if let newSession {
                    _ = try? await walletClient.cancelIssuance(sessionID: newSession.id)
                }
                if isCurrent(request) {
                    setError(WalletStatusText.failure(WalletStatusText.receiveFailed, error), tab: .receive)
                }
            }
        }
    }

    func updateIssuanceCopies(_ configurationID: String, _ count: Int) {
        guard offerReviewEnabled,
              offerPreview?.credentials.contains(where: { $0.configurationID == configurationID }) == true else { return }
        issuanceCopyCounts[configurationID] = min(max(0, count), max(1, offerPreview?.batchSize ?? 1))
    }

    private func issuanceSelections(_ preview: IssuanceOfferPreview) throws -> [IssuanceCredentialSelection] {
        let selections = try preview.credentials.compactMap { credential -> IssuanceCredentialSelection? in
            let count = issuanceCopyCounts[credential.configurationID] ?? 1
            guard count > 0 else { return nil }
            return try IssuanceCredentialSelection(configurationID: credential.configurationID,
                holders: count == 1 ? .existing([.init(keyID: keyID, did: did.isEmpty ? nil : did)]) : .newKeys(count: count))
        }
        guard !selections.isEmpty else { throw WalletError.invalidInput("Select at least one credential") }
        return selections
    }

    func acceptOffer() {
        resetInputFocus()
        guard acceptOfferEnabled else { return }
        let trimmedOfferUrl = offerUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let offer = URL(string: trimmedOfferUrl) else {
            setError(WalletStatusText.failure(WalletStatusText.receiveFailed, WalletStatusText.invalidOfferURL), tab: .receive)
            return
        }
        let trimmedTxCode = offerPreview?.transactionCode.map { normalizedTransactionCode(txCode, requirement: $0) }
        guard let session = issuanceSession else { return }
        let previousCredentials = credentials
        let request = ReceiveRequest(offerURL: offer.absoluteString, navigationResetKey: receiveNavigationResetKey)

        setLoading(WalletStatusText.receivingCredential, tab: .receive, committing: true)
        receiveTask = Task {
            do {
                let selections = try issuanceSelections(session.offer)
                try Task.checkCancellation()
                guard isCurrent(request) else { return }
                switch session.offer.grant {
                case .preAuthorizedCode:
                    try await completeIssuanceOutcome(
                        try await walletClient.continuePreAuthorizedIssuance(
                            sessionID: session.id,
                            transactionCode: trimmedTxCode,
                            credentials: selections
                        ),
                        previousCredentials: previousCredentials,
                        request: request
                    )
                case .authorizationCode:
                    let authorization = try await walletClient.beginAuthorizationIssuance(sessionID: session.id, credentials: selections)
                    try Task.checkCancellation()
                    guard isCurrent(request) else { return }
                    authorizationRequestURL = authorization.url
                    setSuccess(WalletStatusText.reviewCredentialOffer, tab: .receive)
                }
            } catch is CancellationError {
                return
            } catch {
                if isCurrent(request) {
                    setError(WalletStatusText.failure(WalletStatusText.receiveFailed, error), tab: .receive)
                }
            }
        }
    }

    func declineOffer() {
        receiveTask?.cancel()
        let sessionID = issuanceSession?.id
        issuanceSession = nil
        offerPreview = nil
        authorizationRequestURL = nil
        offerUrl = ""
        txCode = ""
        receiveCompleted = false
        receiveNavigationResetKey += 1
        externalFlow = nil
        selectedTab = .credentials
        setSuccess(WalletStatusText.credentialOfferDeclined, tab: .credentials)
        if let sessionID {
            Task { try? await walletClient.cancelIssuance(sessionID: sessionID) }
        }
    }

    func authorizationRequestOpened() {
        authorizationRequestURL = nil
    }

    private func continueAuthorization(callbackURI: URL) {
        guard canAcceptExternalRequest else { return }
        if issuanceSession == nil {
            if !proximityPresentation.active && externalFlow == nil && !isLoading && offerPreview == nil && presentationReview == nil {
                externalFlow = .unavailableCallback(callbackURI)
                selectedTab = .receive
            }
            return
        }
        guard let session = issuanceSession,
              let preview = offerPreview,
              preview.grant == .authorizationCode else { return }
        let request = ReceiveRequest(
            offerURL: offerUrl.trimmingCharacters(in: .whitespacesAndNewlines),
            navigationResetKey: receiveNavigationResetKey
        )
        let previousCredentials = credentials
        setLoading(WalletStatusText.receivingCredential, tab: .receive, committing: true)
        receiveTask = Task {
            do {
                try await completeIssuanceOutcome(
                    try await walletClient.continueAuthorizationIssuance(
                        sessionID: session.id,
                        callbackURI: callbackURI
                    ),
                    previousCredentials: previousCredentials,
                    request: request
                )
            } catch is CancellationError {
                return
            } catch {
                if isCurrent(request) {
                    setError(WalletStatusText.failure(WalletStatusText.receiveFailed, error), tab: .receive)
                }
            }
        }
    }

    private func completeIssuanceOutcome(
        _ rawOutcome: IssuanceOutcome,
        previousCredentials: [Credential],
        request: ReceiveRequest
    ) async throws {
        try Task.checkCancellation()
        guard isCurrent(request) else { return }
        let outcome = try await refreshContinuations(rawOutcome)
        try Task.checkCancellation()
        guard isCurrent(request) else { return }
        let issuer = offerPreview?.issuer
        let credentialIDs: [String]
        switch outcome {
        case let .stored(_, ids):
            credentialIDs = ids
        case let .deferred(_, storedIDs, deferred):
            issuanceSession = nil
            offerPreview = nil
            authorizationRequestURL = nil
            deferredCredentials = (deferredCredentials + deferred).reduce(into: [DeferredCredential]()) { result, credential in
                if let index = result.firstIndex(where: { $0.id == credential.id }) { result[index] = credential }
                else { result.append(credential) }
            }
            lastReceivedCredentialIDs = storedIDs
            issuanceReceipt = IssuanceReceipt(issuer: issuer, pendingIDs: Set(deferred.map(\.id)))
            receiveCompleted = false
            if !storedIDs.isEmpty {
                let refreshed = try await walletClient.credentials()
                try Task.checkCancellation()
                guard isCurrent(request) else { return }
                credentials = refreshed
            }
            setSuccess(WalletStatusText.issuanceProgress(saved: storedIDs.count, pending: deferred.count), tab: .receive)
            return
        case .cancelled:
            issuanceSession = nil
            offerPreview = nil
            authorizationRequestURL = nil
            offerUrl = ""
            txCode = ""
            receiveCompleted = false
            receiveNavigationResetKey += 1
            setSuccess(WalletStatusText.credentialOfferDeclined, tab: .receive)
            return
        case let .failed(_, error, storedIDs, pending):
            deferredCredentials = (deferredCredentials + pending).reduce(into: []) { result, credential in
                if let index = result.firstIndex(where: { $0.id == credential.id }) { result[index] = credential }
                else { result.append(credential) }
            }
            lastReceivedCredentialIDs = storedIDs
            if error.targetFailure != nil || !storedIDs.isEmpty || !pending.isEmpty ||
                [.invalidSession, .remoteOutcomeUncertain, .storageOutcomeUncertain].contains(error.code) {
                issuanceReceipt = IssuanceReceipt(issuer: issuer, pendingIDs: Set(pending.map(\.id)), problem: error)
                issuanceSession = nil
                offerPreview = nil
                authorizationRequestURL = nil
            }
            if !storedIDs.isEmpty {
                let refreshed = try await walletClient.credentials()
                try Task.checkCancellation()
                guard isCurrent(request) else { return }
                credentials = refreshed
            }
            setError(WalletStatusText.issuanceFailure(error, saved: storedIDs.count, pending: pending.count), tab: .receive)
            return
        }
        try Task.checkCancellation()
        guard isCurrent(request) else { return }
        let refreshedCredentials = try await walletClient.credentials()
        try Task.checkCancellation()
        guard isCurrent(request) else { return }
        let receivedCredentialIDs = Self.resolvedReceivedCredentialIDs(
            returnedCredentialIDs: credentialIDs,
            previousCredentials: previousCredentials,
            refreshedCredentials: refreshedCredentials
        )
        let refreshedCredentialIDs = Set(refreshedCredentials.map(\.id))
        let displayableReceivedCredentialIDs = receivedCredentialIDs.filter { refreshedCredentialIDs.contains($0) }
        guard !displayableReceivedCredentialIDs.isEmpty else {
            credentials = refreshedCredentials
            issuanceSession = nil
            offerPreview = nil
            authorizationRequestURL = nil
            lastReceivedCredentialIDs = []
            issuanceReceipt = nil
            receiveCompleted = false
            setError(
                WalletStatusText.failure(
                    WalletStatusText.receiveFailed,
                    WalletStatusText.receivedCredentialsUnavailable
                ),
                tab: .receive
            )
            return
        }

        credentials = refreshedCredentials
        try await reconcileIdentityDocumentRegistrations()
        issuanceSession = nil
        offerPreview = nil
        authorizationRequestURL = nil
        lastReceivedCredentialIDs = displayableReceivedCredentialIDs
        issuanceReceipt = IssuanceReceipt(issuer: issuer)
        self.txCode = ""
        offerUrl = ""
        receiveCompleted = true
        receiveNavigationResetKey += 1
        selectedTab = .receive
        setSuccess(WalletStatusText.receivedCredentials(displayableReceivedCredentialIDs.count), tab: selectedTab)
    }

    func updateTxCode(_ value: String) {
        txCode = offerPreview?.transactionCode.map { normalizedTransactionCode(value, requirement: $0) } ?? value
    }

    func resumeDeferredCredential(_ credential: DeferredCredential) {
        guard !isLoading, let retained = deferredCredentials.first(where: { $0.id == credential.id }), retained.canResume else { return }
        let priorReceipt = issuanceReceipt.flatMap { $0.pendingIDs.contains(credential.id) ? $0 : nil }
        let priorIDs = priorReceipt == nil ? [] : lastReceivedCredentialIDs
        func receipt(_ pending: [DeferredCredential], problem: IssuanceFailure? = nil) -> IssuanceReceipt {
            IssuanceReceipt(issuer: priorReceipt?.issuer,
                pendingIDs: (priorReceipt?.pendingIDs ?? []).subtracting([credential.id]).union(pending.map(\.id)),
                problem: priorReceipt?.problem ?? problem)
        }
        func receivedIDs(_ ids: [String]) -> [String] { (priorIDs + ids).reduce(into: []) { if !$0.contains($1) { $0.append($1) } } }
        let request = ReceiveRequest(offerURL: offerUrl.trimmingCharacters(in: .whitespacesAndNewlines), navigationResetKey: receiveNavigationResetKey)
        setLoading(WalletStatusText.receivingCredential, tab: .receive, committing: true)
        receiveTask = Task {
            do {
                let outcome = try await refreshContinuations(walletClient.resumeDeferredIssuance(deferredCredentialID: credential.id), resumingID: credential.id)
                try Task.checkCancellation()
                guard isCurrent(request) else { return }
                switch outcome {
                case let .stored(_, credentialIDs):
                    let refreshedCredentials = try await walletClient.credentials()
                    try Task.checkCancellation()
                    guard isCurrent(request) else { return }
                    credentials = refreshedCredentials
                    try await reconcileIdentityDocumentRegistrations()
                    try Task.checkCancellation()
                    guard isCurrent(request) else { return }
                    deferredCredentials.removeAll { $0.id == credential.id }
                    lastReceivedCredentialIDs = receivedIDs(credentialIDs)
                    issuanceReceipt = receipt([])
                    receiveCompleted = issuanceReceipt?.problem == nil && issuanceReceipt?.pendingIDs.isEmpty == true
                    if deferredCredentials.isEmpty && !credentialIDs.isEmpty {
                        offerUrl = ""
                        receiveNavigationResetKey += 1
                        selectedTab = .receive
                        setSuccess(WalletStatusText.receivedCredentials(credentialIDs.count), tab: selectedTab)
                    } else {
                        setSuccess(WalletStatusText.receivedCredentials(credentialIDs.count), tab: .receive)
                    }
                case let .deferred(_, storedIDs, pending):
                    if !storedIDs.isEmpty {
                        let refreshed = try await walletClient.credentials()
                        try Task.checkCancellation()
                        guard isCurrent(request) else { return }
                        credentials = refreshed
                    }
                    lastReceivedCredentialIDs = receivedIDs(storedIDs)
                    issuanceReceipt = receipt(pending)
                    deferredCredentials.removeAll { $0.id == credential.id }
                    deferredCredentials.append(contentsOf: pending)
                    setSuccess(WalletStatusText.issuanceProgress(saved: storedIDs.count, pending: pending.count), tab: .receive)
                case .cancelled:
                    issuanceReceipt = receipt([])
                    deferredCredentials.removeAll { $0.id == credential.id }
                    setSuccess(WalletStatusText.credentialOfferDeclined, tab: .receive)
                case let .failed(_, error, storedIDs, pending):
                    deferredCredentials.removeAll { $0.id == credential.id }
                    deferredCredentials.append(contentsOf: pending)
                    lastReceivedCredentialIDs = receivedIDs(storedIDs)
                    issuanceReceipt = receipt(pending, problem: error)
                    if !storedIDs.isEmpty {
                        let refreshed = try await walletClient.credentials()
                        try Task.checkCancellation()
                        guard isCurrent(request) else { return }
                        credentials = refreshed
                    }
                    throw WalletError.internalFailure(WalletStatusText.issuanceFailure(error, saved: storedIDs.count, pending: pending.count))
                }
            } catch is CancellationError {
                return
            } catch {
                if isCurrent(request) {
                    setError(WalletStatusText.failure(WalletStatusText.receiveFailed, error), tab: .receive)
                }
            }
        }
    }

    private func refreshContinuations(_ outcome: IssuanceOutcome, resumingID: String? = nil) async throws -> IssuanceOutcome {
        guard outcome.hasPendingCredentials else { return outcome }
        do { return outcome.withContinuations(try await walletClient.listDeferredIssuance(), resumingID: resumingID) }
        catch is CancellationError { throw CancellationError() }
        // A status-read failure must not discard an already completed issuance result.
        catch { return outcome.withContinuations([], resumingID: resumingID) }
    }

    /// Refresh local state only; this never polls the issuer or takes over another writer.
    func refreshIssuanceStatus() {
        guard !isLoading else { return }
        let request = ReceiveRequest(offerURL: offerUrl.trimmingCharacters(in: .whitespacesAndNewlines), navigationResetKey: receiveNavigationResetKey)
        setLoading("Refreshing receiving status…", tab: .receive)
        receiveTask = Task {
            do {
                let pending = try await walletClient.listDeferredIssuance()
                let refreshed = try await walletClient.credentials()
                try Task.checkCancellation()
                guard isCurrent(request) else { return }
                credentials = refreshed
                deferredCredentials = pending
                if let receipt = issuanceReceipt {
                    issuanceReceipt = IssuanceReceipt(issuer: receipt.issuer,
                        pendingIDs: receipt.pendingIDs.intersection(pending.map(\.id)), problem: receipt.problem)
                }
                setSuccess("Receiving status updated", tab: .receive)
            } catch is CancellationError { return }
            catch {
                if isCurrent(request) { setError(WalletStatusText.failure("Could not refresh receiving status", error), tab: .receive) }
            }
        }
    }

    private func normalizedTransactionCode(
        _ value: String,
        requirement: IssuanceTransactionCode
    ) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalized = requirement.inputMode?.lowercased() == "numeric"
            ? trimmed.filter { $0.isASCII && $0.isNumber }
            : trimmed
        return requirement.length.map { String(normalized.prefix($0)) } ?? normalized
    }

    private func isCurrent(_ request: ReceiveRequest) -> Bool {
        receiveNavigationResetKey == request.navigationResetKey &&
            offerUrl.trimmingCharacters(in: .whitespacesAndNewlines) == request.offerURL
    }

    private struct ReceiveRequest {
        let offerURL: String
        let navigationResetKey: Int
    }

    func presentCredential() {
        resetInputFocus()
        let trimmedRequestUrl = presentationRequestUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let request = URL(string: trimmedRequestUrl) else {
            setError(WalletStatusText.failure(WalletStatusText.presentFailed, WalletStatusText.invalidRequestURL), tab: .present)
            return
        }

        setLoading(WalletStatusText.presentingCredential, tab: .present, committing: true)
        Task {
            do {
                let result = try await walletClient.present(
                    request: request,
                    did: did.isEmpty ? nil : did
                )
                handlePresentationResult(
                    result,
                    successMessage: WalletStatusText.presentationSent,
                    failureMessage: WalletStatusText.presentationFinishedWithoutVerifierConfirmation
                )
            } catch {
                setError(WalletStatusText.failure(WalletStatusText.presentFailed, error), tab: .present)
            }
        }
    }

    func previewPresentation() {
        resetInputFocus()
        guard !isLoading, presentationReview == nil else { return }
        let trimmedRequestUrl = presentationRequestUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let request = URL(string: trimmedRequestUrl) else {
            setError(WalletStatusText.failure(WalletStatusText.previewFailed, WalletStatusText.invalidRequestURL), tab: .present)
            return
        }

        let navigationResetKey = presentationNavigationResetKey
        let requestURL = trimmedRequestUrl
        setLoading(WalletStatusText.resolvingPresentation, tab: .present)
        presentationReview = nil
        selectedPresentationCredentialOptions = []
        selectedPresentationDisclosureOptions = []
        presentationCompleted = false
        clearPendingPresentationContinuation()
        presentationTask = Task {
            var newPreviewHandle: PresentationPreviewHandle?
            do {
                let result = try await walletClient.previewPresentation(request: request)
                newPreviewHandle = result.previewHandle
                try Task.checkCancellation()
                guard
                    presentationNavigationResetKey == navigationResetKey,
                    presentationRequestUrl.trimmingCharacters(in: .whitespacesAndNewlines) == requestURL
                else {
                    try? await walletClient.discardPresentationPreview(result.previewHandle)
                    return
                }
                presentationReview = result
                newPreviewHandle = nil
                switch result {
                case .ready(let preview):
                    selectedPresentationCredentialOptions = preview.sharingReview().defaultCredentialSelection()
                    selectedPresentationDisclosureOptions = []
                    setSuccess(WalletStatusText.reviewPresentationRequest, tab: .present)
                    prepareSelectedPaymentConsent()
                case .invalid:
                    selectedPresentationCredentialOptions = []
                    selectedPresentationDisclosureOptions = []
                    setSuccess(WalletStatusText.reviewPresentationError, tab: .present)
                }
            } catch is CancellationError {
                if let newPreviewHandle {
                    try? await walletClient.discardPresentationPreview(newPreviewHandle)
                }
            } catch {
                if let newPreviewHandle {
                    try? await walletClient.discardPresentationPreview(newPreviewHandle)
                }
                guard !Task.isCancelled else { return }
                setError(WalletStatusText.failure(WalletStatusText.previewFailed, error), tab: .present)
            }
        }
    }

    func togglePresentationCredential(_ selection: PresentationCredentialSelection) {
        guard let review = presentationSharingReview else { return }
        apply(review.toggling(credential: selection, in: presentationSharingSelection))
    }

    func togglePresentationDisclosure(_ selection: PresentationDisclosureSelection) {
        guard let review = presentationSharingReview else { return }
        apply(review.toggling(disclosure: selection, in: presentationSharingSelection))
    }

    private func apply(_ selection: SharingSelection) {
        selectedPresentationCredentialOptions = selection.credentials
        selectedPresentationDisclosureOptions = selection.disclosures
        prepareSelectedPaymentConsent()
    }

    private func prepareSelectedPaymentConsent() {
        paymentConsentTask?.cancel()
        guard let preview = presentationPreview else { return }
        let credentials = selectedPresentationCredentialOptions
        let disclosures = selectedPresentationDisclosureOptions
        let selectedDid = did.isEmpty ? nil : did
        paymentReview = .loading
        guard presentationCredentialSelectionComplete else { return }
        paymentConsentTask = Task {
            do {
                let consent = try await walletClient.preparePaymentConsent(previewHandle: preview.previewHandle,
                    selectedCredentialOptions: Array(credentials), selectedDisclosureOptions: Array(disclosures), did: selectedDid)
                try Task.checkCancellation()
                guard presentationPreview?.previewHandle == preview.previewHandle,
                      selectedPresentationCredentialOptions == credentials,
                      selectedPresentationDisclosureOptions == disclosures else { return }
                paymentReview = consent.map { .ready($0) } ?? .notRequired
            } catch is CancellationError { return
            } catch {
                guard !Task.isCancelled else { return }
                paymentReview = .blocked(error.localizedDescription)
            }
        }
    }

    func submitPresentation() {
        resetInputFocus()
        guard !isLoading, paymentReview.canConfirm else { return }
        guard let previewHandle = presentationPreview?.previewHandle else { return }
        guard presentationCredentialSelectionComplete else {
            setError(WalletStatusText.failure(WalletStatusText.presentFailed, WalletStatusText.selectCredentialForEveryRequest), tab: .present)
            return
        }
        let selectedDisclosureOptions = selectedPresentationDisclosureOptions
            .forSelectedCredentials(selectedPresentationCredentialOptions)
        let selectedCredentialOptions = Array(selectedPresentationCredentialOptions)
        let selectedDid = did.isEmpty ? nil : did

        let consentRevision = paymentReview.consent?.revision
        setLoading(WalletStatusText.presentingCredential, tab: .present, committing: true)
        presentationTask = Task {
            do {
                let result = try await walletClient.submitPresentation(
                    previewHandle: previewHandle,
                    selectedCredentialOptions: selectedCredentialOptions,
                    selectedDisclosureOptions: Array(selectedDisclosureOptions),
                    did: selectedDid,
                    paymentConsentRevision: consentRevision
                )
                try Task.checkCancellation()
                resetPresentationToEntry()
                handlePresentationResult(
                    result,
                    successMessage: WalletStatusText.presentationSent,
                    failureMessage: WalletStatusText.presentationFinishedWithoutVerifierConfirmation
                )
            } catch is CancellationError {
                return
            } catch {
                guard !Task.isCancelled else { return }
                resetPresentationToEntry()
                presentationCompleted = true
                setError(WalletStatusText.failure(WalletStatusText.presentFailed, error), tab: .present)
            }
        }
    }

    func rejectPresentation() {
        resetInputFocus()
        guard !isLoading else { return }
        guard let presentationReview else { return }
        let previewHandle = presentationReview.previewHandle
        let isReportingError: Bool
        if case .invalid = presentationReview {
            isReportingError = true
        } else {
            isReportingError = false
        }

        setLoading(WalletStatusText.decliningPresentation, tab: .present, committing: true)
        presentationTask = Task {
            do {
                let result = try await walletClient.rejectPresentation(previewHandle: previewHandle)
                try Task.checkCancellation()
                finishRejection()
                handlePresentationResult(
                    result,
                    successMessage: isReportingError
                        ? WalletStatusText.verifierNotified
                        : WalletStatusText.presentationRejected,
                    failureMessage: WalletStatusText.rejectionFinishedWithoutVerifierConfirmation
                )
            } catch is CancellationError {
                return
            } catch {
                guard !Task.isCancelled else { return }
                resetPresentationToEntry()
                presentationCompleted = true
                setError(WalletStatusText.failure(WalletStatusText.rejectFailed, error), tab: .present)
            }
        }
    }

    private func finishRejection() {
        resetPresentationToEntry()
    }

    private func finishSuccessfulPresentation() {
        resetPresentationToEntry()
        presentationCompleted = true
    }

    private func resetPresentationToEntry() {
        presentationReview = nil
        presentationRequestUrl = ""
        selectedPresentationCredentialOptions = []
        selectedPresentationDisclosureOptions = []
        presentationCompleted = false
        presentationNavigationResetKey += 1
    }

    func cancelPresentationReview() {
        resetInputFocus()
        guard !isLoading, let previewHandle = presentationReview?.previewHandle else { return }
        paymentConsentTask?.cancel()
        presentationTask?.cancel()
        resetPresentationToEntry()
        setSuccess(WalletStatusText.presentationReviewCancelled, tab: .present)
        Task { try? await walletClient.discardPresentationPreview(previewHandle) }
    }

    func completePresentationContinuation() {
        guard let successMessage = pendingPresentationSuccessMessage else { return }
        clearPendingPresentationContinuation()
        finishSuccessfulPresentation()
        setSuccess(successMessage, tab: .present)
    }

    func failPresentationContinuation(_ reason: String) {
        guard pendingPresentationSuccessMessage != nil else { return }
        clearPendingPresentationContinuation()
        resetPresentationToEntry()
        presentationCompleted = true
        setError(
            WalletStatusText.failure(WalletStatusText.presentationContinuationFailed, reason),
            tab: .present
        )
    }

    private func handlePresentationResult(
        _ result: PresentationResult,
        successMessage: String,
        failureMessage: String
    ) {
        clearPendingPresentationContinuation()
        switch result {
        case .transmitted(.failed):
            presentationCompleted = true
            setError(failureMessage, tab: .present)
        case .prepared(.openURL(let url)):
            pendingPresentationSuccessMessage = successMessage
            pendingPresentationContinuationURL = url
            presentationCompleted = false
        case .prepared(.submitForm(let html)):
            pendingPresentationSuccessMessage = successMessage
            pendingPresentationFormPostHTML = html
            presentationCompleted = false
        case .transmitted(.succeeded(_, let redirectURL)):
            if let redirectURL {
                pendingPresentationSuccessMessage = successMessage
                pendingPresentationContinuationURL = redirectURL
                presentationCompleted = false
            } else {
                finishSuccessfulPresentation()
                setSuccess(successMessage, tab: .present)
            }
        }
    }

    private func clearPendingPresentationContinuation() {
        pendingPresentationContinuationURL = nil
        pendingPresentationFormPostHTML = nil
        pendingPresentationSuccessMessage = nil
    }

    private func cancelIssuanceIfPresent() {
        guard let sessionID = issuanceSession?.id else { return }
        issuanceSession = nil
        authorizationRequestURL = nil
        Task { try? await walletClient.cancelIssuance(sessionID: sessionID) }
    }

    private func discardPresentationPreviewIfPresent() {
        guard let previewHandle = presentationReview?.previewHandle else { return }
        Task { try? await walletClient.discardPresentationPreview(previewHandle) }
    }

    private func reconcileIdentityDocumentRegistrations() async throws {
        try await identityDocumentRegistrationUpdate()
    }

    private static func defaultIdentityDocumentRegistrationUpdate() async throws {
        if #available(iOS 26.0, *) {
            try await DemoIdentityDocumentRegistration.update()
        }
    }

    func retryOpeningWallet() {
        guard !isLoading, !isReady else { return }
        bootstrapIfNeeded()
    }

    private func bootstrapIfNeeded() {
        guard !isReady else { return }
        bootstrap(signingProtection: selectedSigningProtection)
    }

    static let pinLength = WalletAccessController.pinLength

    private func bootstrap(signingProtection: WalletDemoSigningProtection) {
        setLoading(WalletStatusText.bootstrappingWallet)
        logE2E("Bootstrap started")
        Task {
            do {
                try await loadWallet(signingProtection: signingProtection)
                if isReady { setSuccess(WalletStatusText.walletReady) }
                else { isLoading = false; statusMessage = "Set up your wallet" }
                logE2E(isReady ? "Bootstrap: wallet ready" : "Bootstrap: awaiting key setup")
            } catch {
                logE2E("Bootstrap: FAILED with error: \(error.localizedDescription)")
                setError(WalletStatusText.failure(WalletStatusText.bootstrapFailed, error))
            }
        }
    }

    private func loadWallet(
        signingProtection: WalletDemoSigningProtection,
        requiredAppliedSigningProtection: WalletDemoSigningProtection? = nil
    ) async throws {
        if let service = try await walletClient.signingIdentityManager() {
            let model = identityScreen ?? WalletIdentityScreenModel(service: service, preferredAuthorization: signingProtection.authorizationPolicy) { [weak self] in
                guard let self else { return }
                self.bootstrap(signingProtection: self.selectedSigningProtection)
            }
            identityScreen = model
            await model.refresh()
            guard model.identity != nil else { isReady = false; return }
        }
        logE2E("Opening selected wallet identity")
        let result = try await walletClient.bootstrap(signingProtection: signingProtection)
        logE2E("Bootstrap: success, DID: \(result.did)")

        let appliedProtection = try WalletDemoSigningProtection(
            appliedPolicy: result.keyUseAuthorizationPolicy
        )
        guard requiredAppliedSigningProtection == nil || appliedProtection == requiredAppliedSigningProtection else {
            throw WalletError.internalFailure(
                "Reprovisioned wallet did not apply the selected signing protection"
            )
        }

        logE2E("Bootstrap: calling wallet.credentials()")
        let list = try await walletClient.credentials()
        logE2E("Bootstrap: listCredentials returned \(list.count) credentials")

        did = result.did
        keyID = result.keyID
        publicJWK = result.publicJWK
        credentials = list
        deferredCredentials = try await walletClient.listDeferredIssuance()
        appliedSigningProtection = appliedProtection
        selectedSigningProtection = signingProtectionMode.resolve(appliedProtection)
        signingProtectionStore.save(selectedSigningProtection)
        signingProtectionReprovisionTarget = nil
        try await reconcileIdentityDocumentRegistrations()
        isReady = true
        showBiometricSigningWarningIfNeeded(
            warningSequence: foregroundSequence > 0 ? foregroundSequence : nil
        )
    }

    private func validateSigningProtection(_ protection: WalletDemoSigningProtection) async -> Bool {
        do {
            let availability = try await walletClient.signingProtectionAvailability(protection)
            if protection.requiresBiometrics {
                biometricSigningAvailability = availability
                if availability == .available {
                    signingProtectionWarning = nil
                }
            }
            guard let message = availability.message else {
                signingProtectionError = nil
                return true
            }
            signingProtectionError = message
            return false
        } catch {
            signingProtectionError = WalletStatusText.failure(
                WalletStatusText.signingProtectionChangeFailed,
                error
            )
            if protection.requiresBiometrics {
                biometricSigningAvailability = .unsupported
            }
            return false
        }
    }

    private func refreshBiometricSigningAvailability(warningSequence: Int? = nil) {
        biometricSigningAvailabilityTask?.cancel()
        biometricSigningAvailabilityTask = Task { [weak self] in
            guard let self else { return }
            let availability: WalletDemoSigningProtectionAvailability
            do {
                availability = try await walletClient.signingProtectionAvailability(
                    appliedSigningProtection.flatMap { $0.requiresBiometrics ? $0 : nil } ?? .biometric
                )
            } catch {
                availability = .unsupported
            }
            guard !Task.isCancelled else { return }
            biometricSigningAvailability = availability
            if availability == .available {
                signingProtectionWarning = nil
            }
            showBiometricSigningWarningIfNeeded(warningSequence: warningSequence)
        }
    }

    private func showBiometricSigningWarningIfNeeded(warningSequence: Int?) {
        guard let warningSequence,
              lastWarnedForegroundSequence != warningSequence,
              auth == .unlocked,
              appliedSigningProtection?.requiresBiometrics == true,
              let availability = biometricSigningAvailability,
              let warning = availability.warningMessage(
                  canChooseNoBiometricSigning: signingProtectionMode.allows(.none)
              ) else { return }
        lastWarnedForegroundSequence = warningSequence
        signingProtectionWarning = warning
    }

    private func cancelActiveWalletOperations() {
        receiveTask?.cancel()
        paymentConsentTask?.cancel()
        presentationTask?.cancel()
        cancelIssuanceIfPresent()
        discardPresentationPreviewIfPresent()
    }

    private func clearWalletState() {
        externalFlow = nil
        selectedTab = .credentials
        did = ""
        keyID = ""
        publicJWK = ""
        credentials = []
        isReady = false
        appliedSigningProtection = nil
        offerUrl = ""
        txCode = ""
        offerPreview = nil
        presentationRequestUrl = ""
        presentationReview = nil
        selectedPresentationCredentialOptions = []
        selectedPresentationDisclosureOptions = []
        deferredCredentials = []
        lastReceivedCredentialIDs = []
        issuanceReceipt = nil
        receiveCompleted = false
        presentationCompleted = false
        pendingPresentationContinuationURL = nil
        pendingPresentationFormPostHTML = nil
        statusExpanded = false
        statusDismissedKey = nil
        signingProtectionWarning = nil
    }

    private static func attestationConfiguration(
        baseUrl: String?,
        attesterPath: String?,
        bearerToken: String?,
        hostHeader: String?
    ) -> WalletAttestationConfiguration? {
        guard let baseUrl = baseUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
              !baseUrl.isEmpty else {
            return nil
        }

        return WalletAttestationConfiguration(
            baseURL: baseUrl,
            attesterPath: attesterPath ?? "",
            bearerToken: bearerToken ?? "",
            hostHeader: hostHeader ?? ""
        )
    }

    /// Cross-process access for this demo, or a crash naming what is missing.
    ///
    /// Not optional: this target embeds a document-provider extension, and a wallet that quietly falls
    /// back to process-local storage looks healthy while being invisible to that extension. The
    /// symptom would be a failed presentation on a device, so the misconfiguration - an Info.plist key
    /// the build did not expand - has to stop the app at launch instead.
    private static func crossProcessAccessConfiguration() -> WalletCrossProcessAccess {
        guard let keychainAccessGroup = IdentityDocumentSharedConfiguration.keychainAccessGroup,
              !keychainAccessGroup.isEmpty else {
            fatalError(
                IdentityDocumentSupportFailure
                    .unresolvedKeychainAccessGroup(IdentityDocumentNamespace.keychainAccessGroupInfoKey)
                    .localizedDescription
            )
        }
        return WalletCrossProcessAccess(
            appGroupIdentifier: IdentityDocumentSharedConfiguration.appGroupIdentifier,
            keychainAccessGroup: keychainAccessGroup
        )
    }

    private func setLoading(_ message: String, tab: WalletTab? = nil, committing: Bool = false) {
        flowIsCommitting = committing
        isLoading = true
        isError = false
        statusTab = tab
        statusMessage = message
        statusExpanded = false
        statusHideTask?.cancel()
        logE2E("STATUS \(message)")
    }

    private func setSuccess(_ message: String, tab: WalletTab? = nil) {
        flowIsCommitting = false
        isLoading = false
        isError = false
        statusTab = tab
        statusMessage = message
        statusExpanded = false
        statusOccurrenceId += 1
        logE2E("STATUS \(message)")
        scheduleSuccessAutoHide()
    }

    private static func resolvedReceivedCredentialIDs(
        returnedCredentialIDs: [String],
        previousCredentials: [Credential],
        refreshedCredentials: [Credential]
    ) -> [String] {
        let refreshedIDs = Set(refreshedCredentials.map(\.id))
        let returnedResolvedIDs = returnedCredentialIDs.filter { refreshedIDs.contains($0) }
        if !returnedResolvedIDs.isEmpty {
            return returnedResolvedIDs
        }

        let previousIDs = Set(previousCredentials.map(\.id))
        let newCredentialIDs = refreshedCredentials
            .map(\.id)
            .filter { !previousIDs.contains($0) }
        return newCredentialIDs.isEmpty ? returnedCredentialIDs : newCredentialIDs
    }

    private func setError(_ message: String, tab: WalletTab? = nil) {
        flowIsCommitting = false
        isLoading = false
        isError = true
        statusTab = tab
        statusMessage = message
        statusExpanded = false
        statusOccurrenceId += 1
        statusHideTask?.cancel()
        logE2E("STATUS \(message)")
    }

    private func resetFlowStatusForIncomingURL() {
        isLoading = !isReady
        isError = false
        statusTab = nil
        statusMessage = isReady ? WalletStatusText.walletReady : WalletStatusText.startingWallet
        statusExpanded = false
        logE2E("STATUS \(statusMessage)")
        if isReady && !isLoading {
            scheduleSuccessAutoHide()
        }
    }

    private func resetInputFocus() {
        inputFocusResetKey += 1
    }

    private func statusApplies(to tab: WalletTab) -> Bool {
        statusTab == nil || statusTab == tab
    }

    private func fallbackStatusMessage(for tab: WalletTab) -> String {
        switch tab {
        case .credentials, .receive:
            return ""
        case .present:
            if presentationPreview != nil {
                return WalletStatusText.reviewPresentationRequest
            }
            if presentationError != nil {
                return WalletStatusText.reviewPresentationError
            }
            return ""
        }
    }

    private func statusBanner(for tab: WalletTab) -> WalletStatusBannerModel? {
        let message = statusMessage(for: tab)
        guard !message.isEmpty else { return nil }
        let kind: WalletStatusKind
        if statusIsError(for: tab) {
            kind = .error
        } else if statusIsLoading(for: tab) {
            kind = .busy
        } else if isInfoStatus(message) {
            kind = .info
        } else {
            kind = .success
        }
        return WalletStatusBannerModel(message: message, kind: kind, occurrenceId: statusOccurrenceId)
    }

    private func isInfoStatus(_ message: String) -> Bool {
        message == WalletStatusText.reviewCredentialOffer ||
            message == WalletStatusText.reviewPresentationRequest ||
            message == WalletStatusText.reviewPresentationError
    }

    private func scheduleSuccessAutoHide() {
        statusHideTask?.cancel()
        let tab = statusTab ?? selectedTab
        guard let banner = statusBanner(for: tab), banner.kind == .success else { return }
        let key = banner.key
        statusHideTask = Task {
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            guard !Task.isCancelled else { return }
            if statusBanner(for: tab)?.key == key {
                statusDismissedKey = key
                statusExpanded = false
            }
        }
    }

    private func logE2E(_ message: String) {
        NSLog("[WalletE2E] \(message)")
    }
}

private extension PresentationPreviewResult {
    var previewHandle: PresentationPreviewHandle {
        switch self {
        case .ready(let preview): preview.previewHandle
        case .invalid(let error): error.previewHandle
        }
    }
}

#if DEBUG
extension WalletViewModel {
    static func mockForUITests() -> WalletViewModel {
        WalletViewModel(
            walletID: "mock-wallet",
            walletClient: MockWalletClient()
        )
    }
}
#endif
