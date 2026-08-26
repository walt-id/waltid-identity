import Foundation
import WalletDemoIdentityDocumentSupport
import WalletDemoSharingUI
import WalletSDK

enum WalletTab: Hashable {
    case credentials
    case receive
    case present
}

enum WalletAuthState: Equatable {
    case setup
    case login
    case storageUnavailable(String)
    case unlocked
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
    static let pinMustContain4To8Digits = "PIN must contain 4 to 8 digits"
    static let pinConfirmationDoesNotMatch = "PIN confirmation does not match"
    static let wrongPin = "Wrong PIN"
    static let enableBiometricUnlock = "Enable biometric unlock"
    static let biometricUnlockNotAuthorized = "Biometric unlock was not authorized. Use the PIN instead."
    static let receivedCredentialsUnavailable = "received credentials are not available locally"
    static let transactionDataProfilesUnavailable = "Transaction data profiles could not be loaded; transaction-data presentation requests will be rejected."

    static func receivedCredentials(_ count: Int) -> String {
        "Received \(count) credential(s)"
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
    @Published private(set) var presentationReview: PresentationPreviewResult?
    @Published var selectedPresentationCredentialOptions: Set<PresentationCredentialSelection> = []
    @Published var selectedPresentationDisclosureOptions: Set<PresentationDisclosureSelection> = []
    @Published var selectedTab: WalletTab = .credentials
    @Published var offerPreview: IssuanceOfferPreview?
    @Published private(set) var authorizationRequestURL: URL?
    @Published var deferredCredentials: [DeferredCredential] = []
    @Published var lastReceivedCredentialIDs: [String] = []
    @Published var receiveCompleted = false
    @Published var presentationCompleted = false
    @Published var receiveNavigationResetKey = 0
    @Published var presentationNavigationResetKey = 0
    @Published var inputFocusResetKey = 0
    @Published var transactionDataProfilesWarning: String?
    @Published var statusExpanded = false
    @Published var auth: WalletAuthState = .setup
    @Published var pin = ""
    @Published var pinConfirmation = ""
    @Published var useBiometrics = false
    @Published private(set) var isBiometricUnlockAvailable = false
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
    @Published var pinError: String?
    @Published var isAuthenticating = false
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
        offerReviewEnabled && hasValidTransactionCode
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
        receiveTask?.cancel()
        presentationTask?.cancel()
        cancelIssuanceIfPresent()
        discardPresentationPreviewIfPresent()
        clearPendingPresentationContinuation()
        Task {
            cancelActiveWalletOperations()
            do {
                try await walletClient.deleteLocalData()
                pinStore.clear()
                clearWalletState()
                pin = ""
                pinConfirmation = ""
                useBiometrics = false
                pinError = nil
                isAuthenticating = false
                biometricPromptConsumed = false
                auth = .setup
                refreshBiometricSigningAvailability()
                do {
                    try await reconcileIdentityDocumentRegistrations()
                } catch {
                    setError(WalletStatusText.failure(WalletStatusText.resetWalletFailed, error))
                }
            } catch {
                setError(WalletStatusText.failure(WalletStatusText.resetWalletFailed, error))
            }
        }
    }

    func lock() {
        receiveTask?.cancel()
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
        pin = ""
        pinConfirmation = ""
        pinError = nil
        isAuthenticating = false
        biometricPromptConsumed = true
        auth = .login
    }

    func updateUseBiometrics(_ enabled: Bool) {
        guard auth == .setup else { return }
        if !enabled {
            useBiometrics = false
            pinError = nil
            return
        }
        guard !isAuthenticating else { return }
        guard biometricAuthenticator.isAvailable else {
            useBiometrics = false
            pinError = WalletStatusText.biometricUnlockNotAuthorized
            return
        }
        useBiometrics = true
        pinError = nil
        isAuthenticating = true
        Task {
            let result = await biometricAuthenticator.authenticate(reason: WalletStatusText.enableBiometricUnlock)
            isAuthenticating = false
            guard auth == .setup else { return }
            useBiometrics = result == .succeeded
            if result != .succeeded {
                pinError = WalletStatusText.biometricUnlockNotAuthorized
            }
        }
    }

    func submitPin() {
        guard !isAuthenticating else { return }
        switch auth {
        case .setup:
            submitSetupPin()
        case .login:
            submitLoginPin()
        case .storageUnavailable, .unlocked:
            break
        }
    }

    func unlockWithBiometrics(force: Bool = false) {
        guard auth == .login else { return }
        guard force || !biometricPromptConsumed else { return }
        guard !isAuthenticating else { return }
        guard pinStore.isBiometricUnlockEnabled, biometricAuthenticator.isAvailable else { return }
        biometricPromptConsumed = true
        isAuthenticating = true
        Task {
            let result = await biometricAuthenticator.authenticate(reason: "Unlock the wallet")
            isAuthenticating = false
            guard auth == .login else { return }
            if result == .succeeded {
                auth = .unlocked
                showBiometricSigningWarningIfNeeded(
                    warningSequence: foregroundSequence > 0 ? foregroundSequence : nil
                )
                bootstrapIfNeeded()
            }
        }
    }

    func unlockForTests(pin: String = "1234") {
        self.pin = pin
        self.pinConfirmation = pin
        submitPin()
    }

    func promptBiometricUnlockIfNeeded() {
        guard auth == .login else { return }
        unlockWithBiometrics()
    }

    func handleApplicationBecameActive() {
        refreshBiometricAvailability()
        foregroundSequence += 1
        refreshBiometricSigningAvailability(warningSequence: foregroundSequence)
        promptBiometricUnlockIfNeeded()
    }

    var isBiometricUnlockEnabled: Bool { pinStore.isBiometricUnlockEnabled }

    func refreshBiometricAvailability() {
        isBiometricUnlockAvailable = biometricAuthenticator.isAvailable
    }

    var isBiometricSigningAvailable: Bool {
        biometricSigningAvailability == .available
    }

    func selectSigningProtection(_ protection: WalletDemoSigningProtection) {
        guard auth == .setup,
              signingProtectionMode == .optional,
              !isAuthenticating,
              protection != .biometric || isBiometricSigningAvailable else { return }
        selectedSigningProtection = protection
        signingProtectionError = nil
    }

    func dismissSigningProtectionWarning() {
        signingProtectionWarning = nil
    }

    func requestSigningProtectionChange(_ protection: WalletDemoSigningProtection) {
        guard signingProtectionMode.allows(protection), !isChangingSigningProtection, !isLoading else { return }
        guard protection != .biometric || isBiometricSigningAvailable else { return }
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
    private let pinStore: DemoPinStore
    private let biometricAuthenticator: any DemoBiometricAuthenticator
    private var biometricPromptConsumed = false

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
            transactionDataProfiles: transactionDataProfiles.profiles,
            crossProcessAccess: Self.crossProcessAccessConfiguration(),
            defaultKeyUseAuthorizationPolicy: selectedProtection.authorizationPolicy,
            keyUseAuthorizationPrompt: WalletKeyUseAuthorizationPrompt(
                message: "Authorize wallet signing",
                cancelText: "Cancel"
            )
        )
        self.signingProtectionMode = signingProtectionMode
        selectedSigningProtection = selectedProtection
        appliedSigningProtection = nil
        pendingSigningProtectionChange = nil
        signingProtectionReprovisionTarget = nil
        self.signingProtectionStore = resolvedStore
        self.walletClient = walletClient ?? SDKWalletClient(configuration: configuration)
        self.identityDocumentRegistrationUpdate = identityDocumentRegistrationUpdate ?? {
            try await Self.defaultIdentityDocumentRegistrationUpdate()
        }
        self.pinStore = pinStore ?? (
            walletClient == nil
                ? UserDefaultsDemoPinStore(walletID: walletID)
                : InMemoryDemoPinStore()
        )
        self.biometricAuthenticator = biometricAuthenticator ?? (
            walletClient == nil
                ? LocalAuthenticationBiometricAuthenticator()
                : UnavailableDemoBiometricAuthenticator()
        )
        transactionDataProfilesWarning = transactionDataProfiles.warning
        auth = self.pinStore.hasPin ? .login : .setup
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
        resetInputFocus()
        logE2E("Deep link received: \(url.scheme ?? "unknown")")
        switch url.scheme.flatMap(WalletDeepLinkScheme.init(rawValue:)) {
        case .credentialOffer:
            receiveTask?.cancel()
            presentationTask?.cancel()
            cancelIssuanceIfPresent()
            discardPresentationPreviewIfPresent()
            selectedTab = .receive
            offerUrl = url.absoluteString
            offerPreview = nil
            lastReceivedCredentialIDs = []
            receiveCompleted = false
            receiveNavigationResetKey += 1
            presentationReview = nil
            selectedPresentationCredentialOptions = []
            selectedPresentationDisclosureOptions = []
            presentationCompleted = false
            clearPendingPresentationContinuation()
            presentationNavigationResetKey += 1
            resetFlowStatusForIncomingURL()
        case .presentationRequest:
            receiveTask?.cancel()
            presentationTask?.cancel()
            cancelIssuanceIfPresent()
            discardPresentationPreviewIfPresent()
            selectedTab = .present
            presentationRequestUrl = url.absoluteString
            txCode = ""
            offerPreview = nil
            lastReceivedCredentialIDs = []
            receiveCompleted = false
            receiveNavigationResetKey += 1
            presentationReview = nil
            selectedPresentationCredentialOptions = []
            selectedPresentationDisclosureOptions = []
            presentationCompleted = false
            clearPendingPresentationContinuation()
            presentationNavigationResetKey += 1
            resetFlowStatusForIncomingURL()
        case .authorizationCallback:
            continueAuthorization(callbackURI: url)
        case nil:
            break
        }
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
        receiveCompleted = false
        receiveNavigationResetKey += 1
        isLoading = false
        isError = false
        statusTab = nil
        statusMessage = WalletStatusText.walletReady
    }

    func startNewPresentationFlow() {
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

        setLoading(WalletStatusText.receivingCredential, tab: .receive)
        receiveTask = Task {
            do {
                switch session.offer.grant {
                case .preAuthorizedCode:
                    try await completeIssuanceOutcome(
                        try await walletClient.continuePreAuthorizedIssuance(
                            sessionID: session.id,
                            transactionCode: trimmedTxCode
                        ),
                        previousCredentials: previousCredentials,
                        request: request
                    )
                case .authorizationCode:
                    let authorization = try await walletClient.beginAuthorizationIssuance(sessionID: session.id)
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
        setSuccess(WalletStatusText.credentialOfferDeclined, tab: .receive)
        if let sessionID {
            Task { try? await walletClient.cancelIssuance(sessionID: sessionID) }
        }
    }

    func authorizationRequestOpened() {
        authorizationRequestURL = nil
    }

    private func continueAuthorization(callbackURI: URL) {
        guard let session = issuanceSession,
              let preview = offerPreview,
              preview.grant == .authorizationCode else { return }
        let request = ReceiveRequest(
            offerURL: offerUrl.trimmingCharacters(in: .whitespacesAndNewlines),
            navigationResetKey: receiveNavigationResetKey
        )
        let previousCredentials = credentials
        setLoading(WalletStatusText.receivingCredential, tab: .receive)
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
        _ outcome: IssuanceOutcome,
        previousCredentials: [Credential],
        request: ReceiveRequest
    ) async throws {
        try Task.checkCancellation()
        guard isCurrent(request) else { return }
        let credentialIDs: [String]
        switch outcome {
        case let .stored(_, ids):
            credentialIDs = ids
        case let .deferred(_, storedIDs, deferred):
            issuanceSession = nil
            offerPreview = nil
            authorizationRequestURL = nil
            deferredCredentials = (deferredCredentials + deferred).reduce(into: [DeferredCredential]()) { result, credential in
                if !result.contains(where: { $0.id == credential.id }) { result.append(credential) }
            }
            lastReceivedCredentialIDs = storedIDs
            receiveCompleted = false
            setSuccess("Credential issuance deferred", tab: .receive)
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
        case let .failed(_, error, _):
            throw WalletError.internalFailure(error.message)
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
        self.txCode = ""
        offerUrl = ""
        receiveCompleted = false
        receiveNavigationResetKey += 1
        selectedTab = .credentials
        setSuccess(WalletStatusText.receivedCredentials(displayableReceivedCredentialIDs.count), tab: .credentials)
    }

    func updateTxCode(_ value: String) {
        txCode = offerPreview?.transactionCode.map { normalizedTransactionCode(value, requirement: $0) } ?? value
    }

    func resumeDeferredCredential(_ credential: DeferredCredential) {
        guard !isLoading else { return }
        setLoading(WalletStatusText.receivingCredential, tab: .receive)
        receiveTask = Task {
            do {
                let outcome = try await walletClient.resumeDeferredIssuance(deferredCredentialID: credential.id)
                switch outcome {
                case let .stored(_, credentialIDs):
                    let refreshedCredentials = try await walletClient.credentials()
                    credentials = refreshedCredentials
                    try await reconcileIdentityDocumentRegistrations()
                    deferredCredentials.removeAll { $0.id == credential.id }
                    lastReceivedCredentialIDs = credentialIDs
                    receiveCompleted = false
                    if deferredCredentials.isEmpty && !credentialIDs.isEmpty {
                        offerUrl = ""
                        receiveNavigationResetKey += 1
                        selectedTab = .credentials
                        setSuccess(WalletStatusText.receivedCredentials(credentialIDs.count), tab: .credentials)
                    } else {
                        setSuccess(WalletStatusText.receivedCredentials(credentialIDs.count), tab: .receive)
                    }
                case let .deferred(_, _, credentials):
                    deferredCredentials.removeAll { $0.id == credential.id }
                    deferredCredentials.append(contentsOf: credentials)
                    setSuccess("Credential issuance still pending", tab: .receive)
                case .cancelled:
                    deferredCredentials.removeAll { $0.id == credential.id }
                    setSuccess(WalletStatusText.credentialOfferDeclined, tab: .receive)
                case let .failed(_, error, _):
                    throw WalletError.internalFailure(error.message)
                }
            } catch is CancellationError {
                return
            } catch {
                setError(WalletStatusText.failure(WalletStatusText.receiveFailed, error), tab: .receive)
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

        setLoading(WalletStatusText.presentingCredential, tab: .present)
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
    }

    func submitPresentation() {
        resetInputFocus()
        guard !isLoading else { return }
        guard let previewHandle = presentationPreview?.previewHandle else { return }
        guard presentationCredentialSelectionComplete else {
            setError(WalletStatusText.failure(WalletStatusText.presentFailed, WalletStatusText.selectCredentialForEveryRequest), tab: .present)
            return
        }
        let selectedDisclosureOptions = selectedPresentationDisclosureOptions
            .forSelectedCredentials(selectedPresentationCredentialOptions)
        let selectedCredentialOptions = Array(selectedPresentationCredentialOptions)
        let selectedDid = did.isEmpty ? nil : did

        setLoading(WalletStatusText.presentingCredential, tab: .present)
        presentationTask = Task {
            do {
                let result = try await walletClient.submitPresentation(
                    previewHandle: previewHandle,
                    selectedCredentialOptions: selectedCredentialOptions,
                    selectedDisclosureOptions: Array(selectedDisclosureOptions),
                    did: selectedDid
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

        setLoading(WalletStatusText.decliningPresentation, tab: .present)
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
                setError(WalletStatusText.failure(WalletStatusText.rejectFailed, error), tab: .present)
            }
        }
    }

    private func finishRejection() {
        resetPresentationToEntry()
    }

    private func finishSuccessfulPresentation() {
        resetPresentationToEntry()
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
            presentationCompleted = false
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

    private func bootstrapIfNeeded() {
        guard !isReady else { return }
        bootstrap(signingProtection: selectedSigningProtection)
    }

    private func submitSetupPin() {
        guard Self.isValidPin(pin) else {
            pinError = WalletStatusText.pinMustContain4To8Digits
            return
        }
        guard pin == pinConfirmation else {
            pinError = WalletStatusText.pinConfirmationDoesNotMatch
            return
        }
        isAuthenticating = true
        pinError = nil
        Task {
            let selection = signingProtectionMode.resolve(selectedSigningProtection)
            guard await validateSigningProtection(selection) else {
                isAuthenticating = false
                return
            }
            do {
                signingProtectionStore.save(selection)
                selectedSigningProtection = selection
                try await pinStore.setPin(pin)
                pinStore.isBiometricUnlockEnabled = useBiometrics
                isAuthenticating = false
                auth = .unlocked
                bootstrapIfNeeded()
            } catch {
                isAuthenticating = false
                pinError = "PIN could not be saved"
            }
        }
    }

    private func submitLoginPin() {
        guard Self.isValidPin(pin) else {
            pinError = WalletStatusText.pinMustContain4To8Digits
            return
        }
        isAuthenticating = true
        pinError = nil
        Task {
            let matches = await pinStore.verifyPin(pin)
            isAuthenticating = false
            guard auth == .login else { return }
            if matches {
                auth = .unlocked
                showBiometricSigningWarningIfNeeded(
                    warningSequence: foregroundSequence > 0 ? foregroundSequence : nil
                )
                bootstrapIfNeeded()
            } else {
                pinError = WalletStatusText.wrongPin
            }
        }
    }

    private static func isValidPin(_ pin: String) -> Bool {
        pin.range(of: #"^\d{4,8}$"#, options: .regularExpression) != nil
    }

    private func bootstrap(signingProtection: WalletDemoSigningProtection) {
        setLoading(WalletStatusText.bootstrappingWallet)
        logE2E("Bootstrap started")
        Task {
            do {
                try await loadWallet(signingProtection: signingProtection)
                setSuccess(WalletStatusText.walletReady)
                logE2E("Bootstrap: completed successfully, wallet is ready")
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
        logE2E("Bootstrap: calling wallet.bootstrap()")
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
            if protection == .biometric {
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
            if protection == .biometric {
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
                availability = try await walletClient.signingProtectionAvailability(.biometric)
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
              appliedSigningProtection == .biometric,
              let availability = biometricSigningAvailability,
              let warning = availability.warningMessage(
                  canChooseNoBiometricSigning: signingProtectionMode.allows(.none)
              ) else { return }
        lastWarnedForegroundSequence = warningSequence
        signingProtectionWarning = warning
    }

    private func cancelActiveWalletOperations() {
        receiveTask?.cancel()
        presentationTask?.cancel()
        cancelIssuanceIfPresent()
        discardPresentationPreviewIfPresent()
    }

    private func clearWalletState() {
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

    private func setLoading(_ message: String, tab: WalletTab? = nil) {
        isLoading = true
        isError = false
        statusTab = tab
        statusMessage = message
        statusExpanded = false
        statusHideTask?.cancel()
        logE2E("STATUS \(message)")
    }

    private func setSuccess(_ message: String, tab: WalletTab? = nil) {
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
